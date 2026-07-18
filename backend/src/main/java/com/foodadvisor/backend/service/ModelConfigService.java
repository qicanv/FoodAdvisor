package com.foodadvisor.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodadvisor.dto.modelconfig.ConnectionTestResponse;
import com.foodadvisor.dto.modelconfig.ModelConfigRequest;
import com.foodadvisor.dto.modelconfig.ModelConfigResponse;
import com.foodadvisor.dto.modelconfig.SceneBindingRequest;
import com.foodadvisor.dto.modelconfig.SceneBindingResponse;
import com.foodadvisor.entity.ModelConfig;
import com.foodadvisor.entity.ModelSceneBinding;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.ModelConfigMapper;
import com.foodadvisor.mapper.ModelSceneBindingMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ModelConfigService {

    private static final Set<String> SUPPORTED_SCENES = Set.of(
            "STORE_RECOMMENDATION",
            "REVIEW_SUMMARY",
            "REVIEW_REPLY"
    );

    private final ModelConfigMapper modelConfigMapper;
    private final ModelSceneBindingMapper sceneBindingMapper;
    private final ApiKeyCryptoService apiKeyCryptoService;
    private final ModelConnectionTester modelConnectionTester;

    public ModelConfigService(
            ModelConfigMapper modelConfigMapper,
            ModelSceneBindingMapper sceneBindingMapper,
            ApiKeyCryptoService apiKeyCryptoService,
            ModelConnectionTester modelConnectionTester
    ) {
        this.modelConfigMapper = modelConfigMapper;
        this.sceneBindingMapper = sceneBindingMapper;
        this.apiKeyCryptoService = apiKeyCryptoService;
        this.modelConnectionTester = modelConnectionTester;
    }

    public List<ModelConfigResponse> listConfigs() {
        return modelConfigMapper.selectList(
                        new LambdaQueryWrapper<ModelConfig>()
                                .orderByDesc(ModelConfig::getUpdatedAt)
                                .orderByAsc(ModelConfig::getId)
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ModelConfigResponse createConfig(ModelConfigRequest request) {
        requireApiKey(request.apiKey());
        validateStatus(request.status());

        ModelConfig config = new ModelConfig();
        applyRequest(config, request, true);
        modelConfigMapper.insert(config);
        return toResponse(config);
    }

    @Transactional
    public ModelConfigResponse updateConfig(
            Long id,
            ModelConfigRequest request
    ) {
        validateStatus(request.status());
        ModelConfig config = getConfigOrThrow(id);
        applyRequest(config, request, false);
        modelConfigMapper.updateById(config);
        return toResponse(getConfigOrThrow(id));
    }

    @Transactional
    public ConnectionTestResponse testConfig(Long id) {
        ModelConfig config = getConfigOrThrow(id);
        ConnectionTestResponse result = modelConnectionTester.test(config);

        config.setLastTestStatus(result.success() ? "SUCCESS" : "FAILED");
        config.setLastTestMessage(result.message());
        config.setLastTestedAt(OffsetDateTime.now());
        config.setUpdatedAt(OffsetDateTime.now());
        modelConfigMapper.updateById(config);

        return result;
    }

    public List<SceneBindingResponse> listSceneBindings() {
        List<ModelConfig> configs = modelConfigMapper.selectList(null);
        Map<Long, ModelConfig> configById = configs.stream()
                .collect(Collectors.toMap(ModelConfig::getId, item -> item));

        return sceneBindingMapper.selectList(
                        new LambdaQueryWrapper<ModelSceneBinding>()
                                .orderByAsc(ModelSceneBinding::getSceneType)
                )
                .stream()
                .map(binding -> toSceneResponse(binding, configById))
                .toList();
    }

    @Transactional
    public SceneBindingResponse bindScene(SceneBindingRequest request) {
        validateScene(request.sceneType());
        ModelConfig config = getConfigOrThrow(request.modelConfigId());

        if (!"ACTIVE".equals(config.getStatus())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "MODEL_CONFIG_DISABLED",
                    "Only active model configurations can be bound to a scene"
            );
        }

        if (!"SUCCESS".equals(config.getLastTestStatus())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "MODEL_CONFIG_NOT_TESTED",
                    "Run a successful connection test before binding this model"
            );
        }

        ModelSceneBinding binding = sceneBindingMapper.selectOne(
                new LambdaQueryWrapper<ModelSceneBinding>()
                        .eq(ModelSceneBinding::getSceneType, request.sceneType())
        );

        OffsetDateTime now = OffsetDateTime.now();
        if (binding == null) {
            binding = new ModelSceneBinding();
            binding.setSceneType(request.sceneType());
            binding.setModelConfigId(request.modelConfigId());
            binding.setStatus("ACTIVE");
            binding.setCreatedAt(now);
            binding.setUpdatedAt(now);
            sceneBindingMapper.insert(binding);
        } else {
            binding.setModelConfigId(request.modelConfigId());
            binding.setStatus("ACTIVE");
            binding.setUpdatedAt(now);
            sceneBindingMapper.updateById(binding);
        }

        return toSceneResponse(binding, Map.of(config.getId(), config));
    }

    private void applyRequest(
            ModelConfig config,
            ModelConfigRequest request,
            boolean creating
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        config.setConfigName(request.configName());
        config.setProvider(request.provider());
        config.setModelName(request.modelName());
        config.setBaseUrl(request.baseUrl());
        config.setTimeoutMs(request.timeoutMs());
        config.setTemperature(request.temperature());
        config.setMaxOutputTokens(request.maxOutputTokens());
        config.setStatus(request.status());
        config.setUpdatedAt(now);

        if (creating) {
            config.setEncryptedApiKey(apiKeyCryptoService.encrypt(request.apiKey()));
            config.setCreatedAt(now);
        } else if (request.apiKey() != null && !request.apiKey().isBlank()) {
            config.setEncryptedApiKey(apiKeyCryptoService.encrypt(request.apiKey()));
            config.setLastTestStatus(null);
            config.setLastTestMessage("API key changed; run connection test again");
            config.setLastTestedAt(null);
        }
    }

    private ModelConfig getConfigOrThrow(Long id) {
        ModelConfig config = modelConfigMapper.selectById(id);
        if (config == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "MODEL_CONFIG_NOT_FOUND",
                    "Model configuration not found"
            );
        }
        return config;
    }

    private void requireApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "API_KEY_REQUIRED",
                    "API key is required when creating a model configuration"
            );
        }
    }

    private void validateStatus(String status) {
        if (!Set.of("ACTIVE", "DISABLED").contains(status)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_STATUS",
                    "Status must be ACTIVE or DISABLED"
            );
        }
    }

    private void validateScene(String sceneType) {
        if (!SUPPORTED_SCENES.contains(sceneType)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SCENE_TYPE",
                    "Scene type is not supported"
            );
        }
    }

    private ModelConfigResponse toResponse(ModelConfig config) {
        return new ModelConfigResponse(
                config.getId(),
                config.getConfigName(),
                config.getProvider(),
                config.getModelName(),
                config.getBaseUrl(),
                apiKeyCryptoService.mask(
                        apiKeyCryptoService.decrypt(config.getEncryptedApiKey())
                ),
                config.getTimeoutMs(),
                config.getTemperature(),
                config.getMaxOutputTokens(),
                config.getStatus(),
                config.getLastTestStatus(),
                config.getLastTestMessage(),
                config.getLastTestedAt(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }

    private SceneBindingResponse toSceneResponse(
            ModelSceneBinding binding,
            Map<Long, ModelConfig> configById
    ) {
        ModelConfig config = configById.get(binding.getModelConfigId());
        return new SceneBindingResponse(
                binding.getId(),
                binding.getSceneType(),
                binding.getModelConfigId(),
                config == null ? null : config.getConfigName(),
                config == null ? null : config.getModelName(),
                binding.getStatus(),
                binding.getUpdatedAt()
        );
    }
}
