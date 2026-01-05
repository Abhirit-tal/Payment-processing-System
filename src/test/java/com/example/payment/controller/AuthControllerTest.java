package com.example.payment.controller;

import com.example.payment.auth.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AuthControllerTest {

    private MockMvc mockMvc;
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    public void setup() {
        jwtTokenProvider = mock(JwtTokenProvider.class);
        AuthController controller = new AuthController(jwtTokenProvider, "dev-local-key");
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    public void testTokenUnauthorized() throws Exception {
        mockMvc.perform(post("/auth/token").contentType(MediaType.APPLICATION_JSON).content("{\"developer_key\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }
}
