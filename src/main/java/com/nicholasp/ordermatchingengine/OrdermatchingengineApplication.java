package com.nicholasp.ordermatchingengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // runs OrderBookService.flushTrades() and publishDepth()
public class OrdermatchingengineApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrdermatchingengineApplication.class, args);
	}

}
