package com.nicholasp.ordermatchingengine.controller;

import com.nicholasp.ordermatchingengine.controller.OrderBookController.PlaceOrderRequest;
import com.nicholasp.ordermatchingengine.model.OrderSide;
import com.nicholasp.ordermatchingengine.repository.TradeRepository;
import com.nicholasp.ordermatchingengine.service.OrderBookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.*;

@WebMvcTest(OrderBookController.class)
@Import(OrderBookService.class)
class OrderBookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TradeRepository tradeRepository;

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

    @Test
    void cancelEndPoint() throws Exception
    {
        PlaceOrderRequest request = new PlaceOrderRequest(OrderSide.BUY, new BigDecimal("100"), 10);
        
       String responseBody = mockMvc.perform(post("/api/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String orderId = objectMapper.readTree(responseBody).get("orderId").asString();

        mockMvc.perform(delete("/api/orders/" + orderId))
            .andExpect(status().isOk());
    }

    @Test
    void cancelNonexistantID() throws Exception
    {
        mockMvc.perform(delete("/api/orders/" + UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void getBookDepthReturns200() throws Exception
    {
        PlaceOrderRequest request = new PlaceOrderRequest(OrderSide.BUY, new BigDecimal("100"), 10); //placeorderreq to create a book and sample info
        mockMvc.perform(post("/api/orders") //create a post to api/orders
            .contentType(MediaType.APPLICATION_JSON) //content type is a json
            .content(objectMapper.writeValueAsString(request))) //map the value of the content to a string
            .andExpect(status().isCreated()); //confirm that we successfully created the post, 201

        mockMvc.perform(get("/api/orders/book")) //create a get for the orders/book
            .andExpect(status().isOk()) //confirm 200 
            .andExpect(jsonPath("$.buyLevels[0].price").value(100))//accessing the 0 index of the buy levels from getDepth and checking if the value is 100 for price
            .andExpect(jsonPath("$.buyLevels[0].totalQuantity").value(10)); //accessing the buylevels[zero index] and getting the totalquantity and making sure it is 10
    }
}
