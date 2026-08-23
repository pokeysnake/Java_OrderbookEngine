package com.nicholasp.ordermatchingengine.controller;

import com.nicholasp.ordermatchingengine.controller.OrderBookController.PlaceOrderRequest;
import com.nicholasp.ordermatchingengine.model.OrderSide;
import com.nicholasp.ordermatchingengine.service.OrderBookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;

@WebMvcTest(OrderBookController.class)
@Import(OrderBookService.class)
class OrderBookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void placeOrderReturns201() throws Exception
    {
        
        PlaceOrderRequest request = new PlaceOrderRequest(OrderSide.BUY, new BigDecimal("100"), 10);

        mockMvc.perform(post("/api/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
            
    }

    @Test
    void globalHandlerConvertsTo400() throws Exception
    {
        PlaceOrderRequest request = new PlaceOrderRequest(OrderSide.BUY, BigDecimal.ZERO, 10);
        mockMvc.perform(post("/api/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }
}
