package com.nicholasp.ordermatchingengine.bench;

import com.nicholasp.ordermatchingengine.engine.OrderBook;
import com.nicholasp.ordermatchingengine.model.Order;
import com.nicholasp.ordermatchingengine.model.OrderSide;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.math.BigDecimal;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

/**
 * Core-only benchmarks for {@link OrderBook}: no HTTP, Spring or Postgres.
 * <p>
 * Orders are pre-built in {@code @Setup} so the timings measure the engine,
 * not {@code UUID.randomUUID()} / {@code Instant.now()} in the Order constructor.
 * <p>
 * Run (from the project folder):
 * <pre>
 * .\mvnw.cmd -q test-compile
 * .\mvnw.cmd -q exec:exec -Dexec.executable=java -Dexec.classpathScope=test "-Dexec.args=-cp %classpath org.openjdk.jmh.Main OrderBookBenchmark"
 * </pre>
 * Modes: Throughput gives orders/sec; SampleTime gives the latency
 * distribution (p50 / p99 / p99.9) per call in microseconds.
 */
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
// Short iterations so the pre-built order pool (4M) is never exhausted mid-iteration
@Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class OrderBookBenchmark {

    private static final BigDecimal[] PRICES = new BigDecimal[11];
    static {
        for (int i = 0; i < PRICES.length; i++) {
            PRICES[i] = BigDecimal.valueOf(95 + i);
        }
    }

    /**
     * Mixed submit flow, same shape as the Gatling scenario: random side,
     * price 95-105, qty 1-20. Most orders cross, so the book stays small.
     */
    @State(Scope.Thread)
    public static class SubmitState {
        static final int POOL = 4_000_000;
        OrderBook book;
        Order[] pool;
        int next;

        @Setup(Level.Iteration)
        public void setup() {
            book = new OrderBook();
            SplittableRandom rnd = new SplittableRandom(42);
            pool = new Order[POOL];
            for (int i = 0; i < POOL; i++) {
                pool[i] = new Order(rnd.nextBoolean() ? OrderSide.BUY : OrderSide.SELL,
                        PRICES[rnd.nextInt(PRICES.length)], 1 + rnd.nextInt(20));
            }
            next = 0;
        }

        Order nextOrder() {
            if (next == POOL) {
                throw new IllegalStateException("order pool exhausted: raise POOL or shorten the iteration");
            }
            return pool[next++];
        }
    }

    @Benchmark
    public void submitOrder(SubmitState s, Blackhole bh) {
        bh.consume(s.book.submitOrder(s.nextOrder()));
    }

    /**
     * Cancel on a deep book: 10,000 resting BUY orders over 1,000 price
     * levels. Each call cancels a random resting order, then re-submits that
     * same order (cancel doesn't change its remaining quantity), so the book
     * size stays constant. The re-submit doesn't cross (all BUYs), so it costs
     * one comparison plus an O(log n) insert.
     */
    @State(Scope.Thread)
    public static class CancelState {
        static final int RESTING = 10_000;
        OrderBook book;
        Order[] resting;
        SplittableRandom rnd;

        @Setup(Level.Iteration)
        public void setup() {
            book = new OrderBook();
            rnd = new SplittableRandom(7);
            resting = new Order[RESTING];
            for (int i = 0; i < RESTING; i++) {
                resting[i] = new Order(OrderSide.BUY, BigDecimal.valueOf(1 + (i % 1000)), 10);
                book.submitOrder(resting[i]);
            }
        }
    }

    @Benchmark
    public void cancelAndReplace(CancelState s, Blackhole bh) {
        Order o = s.resting[s.rnd.nextInt(CancelState.RESTING)];
        bh.consume(s.book.cancelOrder(o.getId()));
        s.book.submitOrder(o);
    }
}
