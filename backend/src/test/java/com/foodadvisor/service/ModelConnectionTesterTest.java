package com.foodadvisor.service;

import com.foodadvisor.entity.ModelConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class ModelConnectionTesterTest {

    @Test
    void buildsOpenAiCompatibleModelsEndpointWithoutDuplicateVersion()
            throws Exception {
        ModelConnectionTester tester = new ModelConnectionTester(
                mock(ApiKeyCryptoService.class)
        );
        Method method = ModelConnectionTester.class.getDeclaredMethod(
                "buildModelsEndpoint",
                String.class
        );
        method.setAccessible(true);

        assertEquals(
                "https://model.example/v1/models",
                method.invoke(tester, "https://model.example/v1/")
        );
        assertEquals(
                "https://model.example/v1/models",
                method.invoke(tester, "https://model.example")
        );
    }
}
