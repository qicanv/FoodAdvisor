package com.foodadvisor.dto.constraint;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ConstraintPatchOperations {

    @JsonProperty("set")
    private Map<String, Object> setValues = new LinkedHashMap<>();

    private Map<String, List<String>> add = new LinkedHashMap<>();
    private Map<String, List<String>> remove = new LinkedHashMap<>();
    private List<String> clear = new ArrayList<>();
    private Map<String, List<String>> exclude = new LinkedHashMap<>();
    private Map<String, List<String>> unexclude = new LinkedHashMap<>();
}
