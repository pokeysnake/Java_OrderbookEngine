package com.nicholasp.ordermatchingengine.gatling;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.util.concurrent.ThreadLocalRandom;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;



public class OrderSubmissionSimulation extends Simulation {

    private final HttpProtocolBuilder httpProtocol = http 
        .baseUrl("http://localhost:8080")
        .acceptHeader("application/json")
        .contentTypeHeader("application/json");

    private String randomorderJson(io.gatling.javaapi.core.Session session)
    {
        String side = ThreadLocalRandom.current().nextBoolean() ? "BUY" : "SELL";
        int price = 95 + ThreadLocalRandom.current().nextInt(11); //have to use ThreadLocalRandom for threadsafe instances of new Random()
        int quantity = 1 + ThreadLocalRandom.current().nextInt(20);

        return "{\"side\":\"" + side + "\",\"price\":" + price + ",\"quantity\":" + quantity + "}"; //creates the order syntax 
    }

    private final ScenarioBuilder placeOrders = scenario("Place Orders")
        .repeat(50).on( //each virtual user that is spun up will fire 50 requests in a row before its done
            exec(
                http("Place Order")
                .post("/api/orders")
                .body(StringBody(this::randomorderJson)) //uses method reference to above method as the body source
                .check(status().is(201)) //HAS to return 201 or Gatling counts it as a failure (KO) instead of ignoring it
            )
        );


        //simulation initializer that Gatling expects
        //calls injectionProfile to start at 0 concurrent useres then linearly ramps up to 2000 in the span of 20 seconds
    {
        setUp(
            placeOrders.injectOpen(rampUsers(2000).during(20))
        ).protocols(httpProtocol);
    }
    
}
