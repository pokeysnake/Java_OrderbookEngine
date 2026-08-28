package com.nicholasp.ordermatchingengine.config;

import com.nicholasp.ordermatchingengine.controller.OrderBookController.PlaceOrderRequest;
import com.nicholasp.ordermatchingengine.engine.OrderBook;
import com.nicholasp.ordermatchingengine.model.OrderSide;
import com.nicholasp.ordermatchingengine.model.Trade;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

// RANDOM_PORT boots a real embedded Tomcat on a free port instead of the
// simulated, no-socket dispatch that @WebMvcTest/MockMvc use - a real
// WebSocket handshake needs an actual TCP connection to shake hands over.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketConfigTest {

    // Spring injects whatever port the embedded server actually bound to,
    // since RANDOM_PORT means we don't know it ahead of time.
    @LocalServerPort
    private int port;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient()); //creates a new stomp client that is able to speak STOMP frames so it has all the helper functions
        stompClient.setMessageConverter(new JacksonJsonMessageConverter()); //wraps the stomp clients messaging system with the jackson json message converter 
    }

    @Test
    void tradeIsBroadcastToSubscribers() throws Exception {
        // The mailbox the STOMP callback thread will drop the received Trade into,
        // and the test thread will block-with-timeout waiting on.
        BlockingQueue<Trade> receivedTrades = new LinkedBlockingQueue<>();

        // stompClient.connectAsync(url, handler) starts connecting to the server.
        // It does NOT wait for the connection to finish - it immediately hands back
        // a "receipt" object (a CompletableFuture<StompSession>) that will eventually
        // contain the real StompSession, once the handshake actually completes.
        //
        // "new StompSessionHandlerAdapter() {}" - StompSessionHandlerAdapter is a class
        // with methods for handling connection events (onConnect, onError, etc.), all
        // empty by default. The "{}" after it means: make an anonymous, on-the-spot
        // subclass with no changes - we don't care about those events right now, we
        // just need SOME object of that type to hand to connectAsync.
        //
        // ".get(5, TimeUnit.SECONDS)" is what actually pauses this thread. It says:
        // "block right here until that CompletableFuture has its result ready, but
        // if 5 seconds pass and it's still not ready, give up and throw an exception"
        // (instead of hanging forever if something's wrong).
        StompSession session = stompClient
            .connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {
                @Override
                public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                    exception.printStackTrace();
                }
            })
            .get(5, TimeUnit.SECONDS);

        // session.subscribe(destination, handler) tells the server "send me anything
        // published to /topic/trades from now on." It needs a StompFrameHandler,
        // which is an interface with exactly two methods you must implement:
        //
        //   getPayloadType(headers) - called first, BEFORE the message body is even
        //   read. It answers "what Java class should the incoming bytes be turned
        //   into?" We say Trade.class, so the JacksonJsonMessageConverter from
        //   setUp() knows to deserialize into a real Trade object.
        //
        //   handleFrame(headers, payload) - called every time a message actually
        //   arrives, with "payload" already converted into that Trade object. This
        //   runs on the STOMP client's own internal thread - NOT the test's thread -
        //   which is exactly why we hand it off to a BlockingQueue instead of trying
        //   to use it directly here.
        //
        // "new StompFrameHandler() { ... }" is the same anonymous-class pattern as
        // before: defining a one-off implementation of an interface inline, on the
        // spot, instead of creating a separate named class file for it.
        session.subscribe("/topic/trades", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Trade.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedTrades.add((Trade) payload);
            }
        });

        // Now that we're subscribed, trigger a real trade through the actual REST
        // endpoint - not by calling the service directly - so this test proves the
        // FULL path works: HTTP request -> Controller -> Service -> OrderBook match
        // -> broadcast -> our subscription above.
        //
        // A plain RestTemplate (no Spring wiring needed) can POST JSON just like
        // Invoke-RestMethod did from PowerShell earlier. PlaceOrderRequest is the
        // exact record OrderBookController already expects as its request body, so
        // we reuse it directly instead of redefining the same shape twice.
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://localhost:" + port + "/api/orders";

        // Resting SELL order - nothing to match against yet, so no trade fires here.
        restTemplate.postForEntity(url, new PlaceOrderRequest(OrderSide.SELL, new BigDecimal("100"), 10), String.class);

        // Matching BUY order at the same price/quantity - this is the one that
        // actually generates a Trade and triggers the broadcast.
        restTemplate.postForEntity(url, new PlaceOrderRequest(OrderSide.BUY, new BigDecimal("100"), 10), String.class);

        // The broadcast happens asynchronously relative to this test thread (see the
        // earlier discussion on threading), so we can't just read receivedTrades
        // immediately - it might not have arrived yet. poll(timeout, unit) blocks
        // until either something shows up or the timeout expires, returning null in
        // the latter case instead of hanging forever.
        Trade trade = receivedTrades.poll(5, TimeUnit.SECONDS);

        assertNotNull(trade, "expected a trade to be broadcast within the timeout");
        assertEquals(0, new BigDecimal("100").compareTo(trade.getPrice()));
        assertEquals(10, trade.getQuantity());
    }

    @Test
    void bookDepthArrivesWhenOrderPlaced() throws Exception
    {
        // We broadcast OrderBook.BookDepth (not OrderBook itself) - the same
        // reasoning as before: OrderBook is the internal engine object (locks,
        // raw order queues), BookDepth is the safe, external-facing aggregate
        // view, the exact same type GET /api/orders/book already returns.
        BlockingQueue<OrderBook.BookDepth> books = new LinkedBlockingQueue<>();

        StompSession session = stompClient
            .connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {
                @Override
                public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                    exception.printStackTrace();
                }
            })
            .get(5, TimeUnit.SECONDS);

        session.subscribe("/topic/book", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return OrderBook.BookDepth.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                books.add((OrderBook.BookDepth) payload);
            }
        });

        RestTemplate restTemplate = new RestTemplate();
        String url = "http://localhost:" + port + "/api/orders";

        // A single resting order (nothing to match against) is deliberately
        // the case to test here - it's the exact scenario that was originally
        // broken (zero trades generated, but the book still changed), so it's
        // the one that actually proves the fix.
        restTemplate.postForEntity(url, new PlaceOrderRequest(OrderSide.SELL, new BigDecimal("100"), 10), String.class);

        OrderBook.BookDepth bookDepth = books.poll(5, TimeUnit.SECONDS);

        assertNotNull(bookDepth, "expected a book depth update to be broadcast within the timeout");
        assertFalse(bookDepth.sellLevels().isEmpty(), "expected the resting SELL order to appear in the broadcast book");
    }
}
