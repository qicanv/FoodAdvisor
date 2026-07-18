package com.foodadvisor.controller;

import com.foodadvisor.dto.constraint.ConstraintExtractResponse;
import com.foodadvisor.exception.GlobalExceptionHandler;
import com.foodadvisor.service.ConstraintExtractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ConstraintControllerTest {

    @Mock
    private ConstraintExtractionService constraintExtractionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new ConstraintController(
                                constraintExtractionService
                        )
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldIgnoreForgedUserHeaderAndUseJwtUserId()
            throws Exception {
        when(constraintExtractionService.extractAndMerge(
                1L,
                7L,
                "四个人吃川菜"
        )).thenReturn(new ConstraintExtractResponse());

        mockMvc.perform(post(
                        "/api/diner/sessions/1/constraints/extract"
                )
                        .requestAttr("userId", 7)
                        .header("X-User-Id", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"message\":\"四个人吃川菜\"}"
                        ))
                .andExpect(status().isOk());

        verify(constraintExtractionService).extractAndMerge(
                1L,
                7L,
                "四个人吃川菜"
        );
    }
}
