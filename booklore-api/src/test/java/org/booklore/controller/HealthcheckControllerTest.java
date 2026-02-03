package org.booklore.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(properties = {"app.version=1.0.0"})
public class HealthcheckControllerTest {

    private MockMvc mockMvc;



    @Autowired

    private ObjectMapper objectMapper;



    @Autowired

    private WebApplicationContext webApplicationContext;



    @BeforeEach

    public void setup() {

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

    }



    @Test

    public void getPing_ShouldReturnHealthcheckResponse() throws Exception {

        mockMvc.perform(get("/api/v1/healthcheck"))

                .andExpect(status().isOk())

                .andExpect(jsonPath("$.data.status").value("UP"))

                .andExpect(jsonPath("$.data.message").value("Application is running smoothly."))

                .andExpect(jsonPath("$.data.timestamp").exists())

                .andExpect(jsonPath("$.data.version").value("1.0.0"));

    }

}
