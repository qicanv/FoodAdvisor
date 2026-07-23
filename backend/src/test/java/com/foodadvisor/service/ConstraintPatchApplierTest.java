package com.foodadvisor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.constraint.ConstraintPatch;
import com.foodadvisor.dto.constraint.ConstraintPatchOperations;
import com.foodadvisor.dto.constraint.ConstraintState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConstraintPatchApplierTest {

    private final ConstraintPatchApplier applier =
            new ConstraintPatchApplier(new ObjectMapper());

    @Test
    void replacesCuisineAndAddsExplicitExclusion() {
        ConstraintState current = new ConstraintState();
        current.setCuisines(new ArrayList<>(List.of("川菜")));
        ConstraintPatch patch = patch();
        patch.getOperations().setSetValues(Map.of(
                "cuisines", List.of("烧烤")));
        patch.getOperations().setExclude(Map.of(
                "cuisines", List.of("川菜")));

        ConstraintState state = applier.apply(current, patch).state();

        assertEquals(List.of("烧烤"), state.getCuisines());
        assertEquals(List.of("川菜"), state.getExcludedCuisines());
    }

    @Test
    void positiveAndExcludedValuesRemainMutuallyExclusive() {
        ConstraintState current = new ConstraintState();
        current.setCuisines(new ArrayList<>(List.of("川菜", "火锅")));
        ConstraintPatch patch = patch();
        patch.getOperations().setExclude(Map.of(
                "cuisines", List.of("火锅")));

        ConstraintState state = applier.apply(current, patch).state();

        assertFalse(state.getCuisines().contains("火锅"));
        assertTrue(state.getExcludedCuisines().contains("火锅"));
    }

    @Test
    void unexcludeRemovesOnlyRequestedValue() {
        ConstraintState current = new ConstraintState();
        current.setExcludedMerchantTypes(
                new ArrayList<>(List.of("火锅", "烧烤")));
        ConstraintPatch patch = patch();
        patch.getOperations().setUnexclude(Map.of(
                "merchantTypes", List.of("火锅")));

        ConstraintState state = applier.apply(current, patch).state();

        assertEquals(List.of("烧烤"), state.getExcludedMerchantTypes());
    }

    @Test
    void removesQuietWithoutClearingOtherEnvironment() {
        ConstraintState current = new ConstraintState();
        current.setEnvironmentRequirements(
                new ArrayList<>(List.of("安静", "适合拍照")));
        ConstraintPatch patch = patch();
        patch.getOperations().setRemove(Map.of(
                "environmentRequirements", List.of("不吵")));

        ConstraintState state = applier.apply(current, patch).state();

        assertEquals(List.of("适合拍照"),
                state.getEnvironmentRequirements());
    }

    @Test
    void recalculatesPerCapitaWhenTotalAndPartyChange() {
        ConstraintState current = new ConstraintState();
        ConstraintPatch patch = patch();
        patch.getOperations().setSetValues(Map.of(
                "partySize", 4,
                "totalBudget", 200));

        ConstraintState state = applier.apply(current, patch).state();

        assertEquals(new BigDecimal("50.00"), state.getPerCapitaBudget());
    }

    @Test
    void explicitPerCapitaWinsAndTargetTimeIsReportedChanged() {
        ConstraintState current = new ConstraintState();
        current.setPartySize(2);
        current.setTotalBudget(new BigDecimal("200"));
        ConstraintPatch patch = patch();
        patch.getOperations().setSetValues(Map.of(
                "partySize", 4,
                "perCapitaBudget", 80,
                "businessTargetTime", "22:00",
                "businessTargetNextDay", true));

        ConstraintPatchApplier.PatchResult result =
                applier.apply(current, patch);

        assertEquals(new BigDecimal("80"),
                result.state().getPerCapitaBudget());
        assertTrue(result.changedFields().contains("businessTargetTime"));
        assertTrue(result.changedFields().contains("businessTargetNextDay"));
    }

    private ConstraintPatch patch() {
        ConstraintPatch patch = new ConstraintPatch();
        patch.setOperations(new ConstraintPatchOperations());
        return patch;
    }
}
