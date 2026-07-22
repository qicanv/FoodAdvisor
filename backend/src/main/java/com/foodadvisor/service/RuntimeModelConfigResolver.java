package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodadvisor.dto.ai.RuntimeModelConfig;
import com.foodadvisor.entity.ModelConfig;
import com.foodadvisor.entity.ModelSceneBinding;
import com.foodadvisor.mapper.ModelConfigMapper;
import com.foodadvisor.mapper.ModelSceneBindingMapper;
import org.springframework.stereotype.Service;

/**
 * 根据业务场景解析当前启用的模型配置。
 *
 * 本类只负责：
 * 1. 查询场景绑定；
 * 2. 校验模型配置状态；
 * 3. 解密 API Key；
 * 4. 返回仅供内部调用使用的运行时配置。
 */
@Service
public class RuntimeModelConfigResolver {

    public static final String STORE_RECOMMENDATION =
            "STORE_RECOMMENDATION";

    private static final String ACTIVE = "ACTIVE";
    private static final String TEST_SUCCESS = "SUCCESS";

    private final ModelSceneBindingMapper sceneBindingMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final ApiKeyCryptoService apiKeyCryptoService;

    public RuntimeModelConfigResolver(
            ModelSceneBindingMapper sceneBindingMapper,
            ModelConfigMapper modelConfigMapper,
            ApiKeyCryptoService apiKeyCryptoService
    ) {
        this.sceneBindingMapper = sceneBindingMapper;
        this.modelConfigMapper = modelConfigMapper;
        this.apiKeyCryptoService = apiKeyCryptoService;
    }

    /**
     * 解析探店推荐场景当前绑定的模型。
     */
    public RuntimeModelConfig resolveStoreRecommendation() {
        return resolveRequired(STORE_RECOMMENDATION);
    }

    /**
     * 解析指定场景的运行时模型配置。
     *
     * 配置不可用时抛出安全的配置异常，由上层决定是否降级。
     */
    public RuntimeModelConfig resolveRequired(String sceneType) {
        ModelSceneBinding binding =
                sceneBindingMapper.selectOne(
                        new LambdaQueryWrapper<ModelSceneBinding>()
                                .eq(
                                        ModelSceneBinding::getSceneType,
                                        sceneType
                                )
                                .eq(
                                        ModelSceneBinding::getStatus,
                                        ACTIVE
                                )
                );

        if (binding == null) {
            throw new IllegalStateException(
                    "No active model configuration is bound to scene: "
                            + sceneType
            );
        }

        ModelConfig config =
                modelConfigMapper.selectById(
                        binding.getModelConfigId()
                );

        if (config == null) {
            throw new IllegalStateException(
                    "Bound model configuration does not exist"
            );
        }

        if (!ACTIVE.equals(config.getStatus())) {
            throw new IllegalStateException(
                    "Bound model configuration is disabled"
            );
        }

        if (!TEST_SUCCESS.equals(config.getLastTestStatus())) {
            throw new IllegalStateException(
                    "Bound model configuration has not passed connection testing"
            );
        }

        String provider =
                requireText(config.getProvider(), "provider");
        String modelName =
                requireText(config.getModelName(), "modelName");
        String baseUrl =
                requireText(config.getBaseUrl(), "baseUrl");

        String apiKey =
                apiKeyCryptoService.decrypt(
                        config.getEncryptedApiKey()
                );

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Bound model API key is empty"
            );
        }

        return new RuntimeModelConfig(
                provider,
                modelName,
                baseUrl,
                apiKey,
                config.getTimeoutMs(),
                config.getTemperature(),
                config.getMaxOutputTokens()
        );
    }

    private String requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Bound model configuration field is missing: "
                            + fieldName
            );
        }

        return value.trim();
    }
}