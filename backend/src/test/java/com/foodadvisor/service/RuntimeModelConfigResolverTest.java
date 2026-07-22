package com.foodadvisor.service;

import com.foodadvisor.dto.ai.RuntimeModelConfig;
import com.foodadvisor.entity.ModelConfig;
import com.foodadvisor.entity.ModelSceneBinding;
import com.foodadvisor.mapper.ModelConfigMapper;
import com.foodadvisor.mapper.ModelSceneBindingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeModelConfigResolverTest {

    @Mock
    private ModelSceneBindingMapper sceneBindingMapper;

    @Mock
    private ModelConfigMapper modelConfigMapper;

    @Mock
    private ApiKeyCryptoService apiKeyCryptoService;

    private RuntimeModelConfigResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new RuntimeModelConfigResolver(
                sceneBindingMapper,
                modelConfigMapper,
                apiKeyCryptoService
        );
    }

    @Test
    void shouldResolveActiveTestedStoreRecommendationModel() {
        ModelSceneBinding binding = new ModelSceneBinding();
        binding.setId(1L);
        binding.setSceneType("STORE_RECOMMENDATION");
        binding.setModelConfigId(10L);
        binding.setStatus("ACTIVE");

        ModelConfig config = new ModelConfig();
        config.setId(10L);
        config.setProvider("OPENAI_COMPATIBLE");
        config.setModelName("demo-model");
        config.setBaseUrl("https://example.com/v1");
        config.setEncryptedApiKey("gcm:encrypted");
        config.setTimeoutMs(30000);
        config.setTemperature(new BigDecimal("0.2"));
        config.setMaxOutputTokens(1200);
        config.setStatus("ACTIVE");
        config.setLastTestStatus("SUCCESS");

        when(sceneBindingMapper.selectOne(any()))
                .thenReturn(binding);
        when(modelConfigMapper.selectById(10L))
                .thenReturn(config);
        when(apiKeyCryptoService.decrypt("gcm:encrypted"))
                .thenReturn("plain-api-key");

        RuntimeModelConfig result =
                resolver.resolveStoreRecommendation();

        assertAll(
                () -> assertEquals(
                        "OPENAI_COMPATIBLE",
                        result.provider()
                ),
                () -> assertEquals(
                        "demo-model",
                        result.modelName()
                ),
                () -> assertEquals(
                        "https://example.com/v1",
                        result.baseUrl()
                ),
                () -> assertEquals(
                        "plain-api-key",
                        result.apiKey()
                ),
                () -> assertEquals(
                        30000,
                        result.timeoutMs()
                ),
                () -> assertEquals(
                        new BigDecimal("0.2"),
                        result.temperature()
                ),
                () -> assertEquals(
                        1200,
                        result.maxOutputTokens()
                )
        );

        verify(apiKeyCryptoService)
                .decrypt("gcm:encrypted");
    }

    @Test
    void shouldRejectWhenSceneHasNoActiveBinding() {
        when(sceneBindingMapper.selectOne(any()))
                .thenReturn(null);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        resolver::resolveStoreRecommendation
                );

        assertEquals(
                "No active model configuration is bound to scene: "
                        + "STORE_RECOMMENDATION",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectDisabledModelConfiguration() {
        ModelSceneBinding binding = new ModelSceneBinding();
        binding.setModelConfigId(10L);
        binding.setSceneType("STORE_RECOMMENDATION");
        binding.setStatus("ACTIVE");

        ModelConfig config = new ModelConfig();
        config.setId(10L);
        config.setStatus("DISABLED");
        config.setLastTestStatus("SUCCESS");

        when(sceneBindingMapper.selectOne(any()))
                .thenReturn(binding);
        when(modelConfigMapper.selectById(10L))
                .thenReturn(config);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        resolver::resolveStoreRecommendation
                );

        assertEquals(
                "Bound model configuration is disabled",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectModelThatHasNotPassedConnectionTest() {
        ModelSceneBinding binding = new ModelSceneBinding();
        binding.setModelConfigId(10L);
        binding.setSceneType("STORE_RECOMMENDATION");
        binding.setStatus("ACTIVE");

        ModelConfig config = new ModelConfig();
        config.setId(10L);
        config.setStatus("ACTIVE");
        config.setLastTestStatus("FAILED");

        when(sceneBindingMapper.selectOne(any()))
                .thenReturn(binding);
        when(modelConfigMapper.selectById(10L))
                .thenReturn(config);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        resolver::resolveStoreRecommendation
                );

        assertEquals(
                "Bound model configuration has not passed connection testing",
                exception.getMessage()
        );
    }
}