package com.foodadvisor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.constraint.ConstraintConflictVO;
import com.foodadvisor.dto.constraint.ConstraintPatch;
import com.foodadvisor.dto.constraint.ConstraintPatchOperations;
import com.foodadvisor.dto.constraint.ConstraintState;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ConstraintPatchApplier {

    private final ObjectMapper objectMapper;

    public ConstraintPatchApplier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PatchResult apply(ConstraintState current, ConstraintPatch patch) {
        ConstraintState before = copy(current);
        ConstraintState state = copy(current);
        ensureLists(before);
        ensureLists(state);
        normalize(before);
        normalize(state);
        validateNumbers(before);
        validateNumbers(state);
        List<ConstraintConflictVO> conflicts = new ArrayList<>();
        if (patch == null) {
            return new PatchResult(state, List.of(), conflicts);
        }
        if (patch.getConflicts() != null && !patch.getConflicts().isEmpty()) {
            return new PatchResult(before, List.of(),
                    new ArrayList<>(patch.getConflicts()));
        }

        ConstraintPatchOperations operations = patch.getOperations() == null
                ? new ConstraintPatchOperations() : patch.getOperations();
        applyClear(state, operations.getClear());
        applySet(state, safeMap(operations.getSetValues()));
        applyListOperations(state, safeMap(operations.getAdd()), true);
        applyListOperations(state, safeMap(operations.getRemove()), false);
        applyExclusions(state, safeMap(operations.getExclude()), true);
        applyExclusions(state, safeMap(operations.getUnexclude()), false);
        normalize(state);
        enforceMutualExclusion(state);
        validateNumbers(state);
        recalculatePerCapita(before, state, operations);

        return new PatchResult(
                state,
                detectChanges(before, state),
                conflicts
        );
    }

    private void applySet(ConstraintState state, Map<String, ?> values) {
        values.forEach((field, value) -> {
            switch (field) {
                case "partySize" -> state.setPartySize(integer(value));
                case "totalBudget" -> state.setTotalBudget(decimal(value));
                case "perCapitaBudget" -> state.setPerCapitaBudget(decimal(value));
                case "distanceKm" -> state.setDistanceKm(decimal(value));
                case "minRating" -> state.setMinRating(decimal(value));
                case "ratingPreference" -> {
                    if (ConstraintState.RATING_PREFERENCE_HIGH.equals(value)) {
                        state.setRatingPreference(
                                ConstraintState.RATING_PREFERENCE_HIGH);
                    }
                }
                case "businessTime" -> state.setBusinessTime(text(value));
                case "businessTargetTime" -> state.setBusinessTargetTime(text(value));
                case "businessTargetNextDay" ->
                        state.setBusinessTargetNextDay(bool(value));
                case "businessTargetDate" ->
                        state.setBusinessTargetDate(text(value));
                case "businessTargetDayOfWeek" ->
                        state.setBusinessTargetDayOfWeek(integer(value));
                case "businessTimeWindow" ->
                        state.setBusinessTimeWindow(text(value));
                case "timezone" -> state.setTimezone(text(value));
                case "merchantTypes" -> state.setMerchantTypes(strings(value));
                case "cuisines" -> state.setCuisines(strings(value));
                case "tastePreferences" -> state.setTastePreferences(strings(value));
                case "tasteRestrictions" -> state.setTasteRestrictions(strings(value));
                case "dishKeywords" -> state.setDishKeywords(strings(value));
                case "excludedCuisines" -> state.setExcludedCuisines(strings(value));
                case "excludedMerchantTypes" ->
                        state.setExcludedMerchantTypes(strings(value));
                case "scenes" -> state.setScenes(strings(value));
                case "environmentRequirements" ->
                        state.setEnvironmentRequirements(strings(value));
                default -> {
                    // Unknown fields are deliberately ignored; Java owns the whitelist.
                }
            }
        });
    }

    private void applyClear(ConstraintState state, List<String> fields) {
        if (fields == null) return;
        for (String field : fields) {
            applySet(state, Map.of(field, switch (field) {
                case "merchantTypes", "cuisines", "tastePreferences",
                     "tasteRestrictions", "dishKeywords", "excludedCuisines",
                     "excludedMerchantTypes", "scenes",
                     "environmentRequirements" -> List.of();
                default -> "";
            }));
            switch (field) {
                case "partySize" -> state.setPartySize(null);
                case "totalBudget" -> state.setTotalBudget(null);
                case "perCapitaBudget" -> state.setPerCapitaBudget(null);
                case "distanceKm" -> state.setDistanceKm(null);
                case "minRating" -> state.setMinRating(null);
                case "ratingPreference" -> state.setRatingPreference(null);
                case "businessTime" -> state.setBusinessTime(null);
                case "businessTargetTime" -> state.setBusinessTargetTime(null);
                case "businessTargetNextDay" -> state.setBusinessTargetNextDay(null);
                case "businessTargetDate" -> state.setBusinessTargetDate(null);
                case "businessTargetDayOfWeek" ->
                        state.setBusinessTargetDayOfWeek(null);
                case "businessTimeWindow" -> state.setBusinessTimeWindow(null);
                default -> {
                }
            }
        }
    }

    private void applyListOperations(
            ConstraintState state,
            Map<String, ? extends Object> values,
            boolean add
    ) {
        values.forEach((field, raw) -> {
            List<String> current = listFor(state, field);
            if (current == null) return;
            List<String> operationValues = strings(raw);
            if (add) {
                current.addAll(operationValues);
            } else {
                LinkedHashSet<String> removals =
                        new LinkedHashSet<>(operationValues);
                current.removeIf(value ->
                        removals.contains(DiningConstraintNormalizer.normalize(value)));
            }
            setList(state, field, current);
        });
    }

    private void applyExclusions(
            ConstraintState state,
            Map<String, ? extends Object> values,
            boolean exclude
    ) {
        values.forEach((field, raw) -> {
            List<String> operationValues = strings(raw);
            if ("cuisines".equals(field)
                    || "excludedCuisines".equals(field)) {
                if (exclude) {
                    state.getExcludedCuisines().addAll(operationValues);
                    state.getCuisines().removeAll(operationValues);
                } else {
                    state.getExcludedCuisines().removeAll(operationValues);
                }
            } else if ("merchantTypes".equals(field)
                    || "excludedMerchantTypes".equals(field)) {
                if (exclude) {
                    state.getExcludedMerchantTypes().addAll(operationValues);
                    state.getMerchantTypes().removeAll(operationValues);
                } else {
                    state.getExcludedMerchantTypes().removeAll(operationValues);
                }
            }
        });
    }

    private void normalize(ConstraintState state) {
        state.setMerchantTypes(DiningConstraintNormalizer.normalizeList(
                state.getMerchantTypes()));
        state.setCuisines(DiningConstraintNormalizer.normalizeList(
                state.getCuisines()));
        state.setTastePreferences(DiningConstraintNormalizer.normalizeList(
                state.getTastePreferences()));
        state.setTasteRestrictions(DiningConstraintNormalizer.normalizeList(
                state.getTasteRestrictions()));
        state.setDishKeywords(DiningConstraintNormalizer.normalizeList(
                state.getDishKeywords()));
        state.setExcludedCuisines(DiningConstraintNormalizer.normalizeList(
                state.getExcludedCuisines()));
        state.setExcludedMerchantTypes(DiningConstraintNormalizer.normalizeList(
                state.getExcludedMerchantTypes()));
        state.setScenes(DiningConstraintNormalizer.normalizeList(
                state.getScenes()));
        state.setEnvironmentRequirements(DiningConstraintNormalizer.normalizeList(
                state.getEnvironmentRequirements()));
    }

    private void ensureLists(ConstraintState state) {
        if (state.getMerchantTypes() == null) state.setMerchantTypes(new ArrayList<>());
        if (state.getCuisines() == null) state.setCuisines(new ArrayList<>());
        if (state.getTastePreferences() == null) state.setTastePreferences(new ArrayList<>());
        if (state.getTasteRestrictions() == null) state.setTasteRestrictions(new ArrayList<>());
        if (state.getDishKeywords() == null) state.setDishKeywords(new ArrayList<>());
        if (state.getExcludedCuisines() == null) state.setExcludedCuisines(new ArrayList<>());
        if (state.getExcludedMerchantTypes() == null) {
            state.setExcludedMerchantTypes(new ArrayList<>());
        }
        if (state.getScenes() == null) state.setScenes(new ArrayList<>());
        if (state.getEnvironmentRequirements() == null) {
            state.setEnvironmentRequirements(new ArrayList<>());
        }
    }

    private void enforceMutualExclusion(ConstraintState state) {
        state.getCuisines().removeAll(state.getExcludedCuisines());
        state.getMerchantTypes().removeAll(state.getExcludedMerchantTypes());
    }

    private void validateNumbers(ConstraintState state) {
        if (state.getPartySize() != null
                && (state.getPartySize() < 1 || state.getPartySize() > 20)) {
            state.setPartySize(null);
        }
        if (!positive(state.getTotalBudget())) state.setTotalBudget(null);
        if (!positive(state.getPerCapitaBudget())) state.setPerCapitaBudget(null);
        if (!positive(state.getDistanceKm())
                || (state.getDistanceKm() != null
                && state.getDistanceKm().compareTo(new BigDecimal("100")) > 0)) {
            state.setDistanceKm(null);
        }
        if (state.getMinRating() != null
                && (state.getMinRating().compareTo(BigDecimal.ZERO) < 0
                || state.getMinRating().compareTo(new BigDecimal("5")) > 0)) {
            state.setMinRating(null);
        }
        if (!ConstraintState.RATING_PREFERENCE_HIGH.equals(
                state.getRatingPreference())) {
            state.setRatingPreference(null);
        }
    }

    private void recalculatePerCapita(
            ConstraintState before,
            ConstraintState state,
            ConstraintPatchOperations operations
    ) {
        Map<String, Object> setValues = safeMap(operations.getSetValues());
        if (setValues.containsKey("perCapitaBudget")) return;
        if (!setValues.containsKey("partySize")
                && !setValues.containsKey("totalBudget")) return;
        if (state.getPartySize() != null && state.getPartySize() > 0
                && state.getTotalBudget() != null) {
            state.setPerCapitaBudget(state.getTotalBudget().divide(
                    BigDecimal.valueOf(state.getPartySize()),
                    2, RoundingMode.HALF_UP));
        } else if (setValues.containsKey("totalBudget")
                || !Objects.equals(before.getPartySize(), state.getPartySize())) {
            state.setPerCapitaBudget(null);
        }
    }

    private List<String> detectChanges(ConstraintState before, ConstraintState after) {
        List<String> changes = new ArrayList<>();
        compare(changes, "partySize", before.getPartySize(), after.getPartySize());
        compare(changes, "totalBudget", before.getTotalBudget(), after.getTotalBudget());
        compare(changes, "perCapitaBudget", before.getPerCapitaBudget(),
                after.getPerCapitaBudget());
        compare(changes, "merchantTypes", before.getMerchantTypes(), after.getMerchantTypes());
        compare(changes, "cuisines", before.getCuisines(), after.getCuisines());
        compare(changes, "tastePreferences", before.getTastePreferences(),
                after.getTastePreferences());
        compare(changes, "tasteRestrictions", before.getTasteRestrictions(),
                after.getTasteRestrictions());
        compare(changes, "dishKeywords", before.getDishKeywords(), after.getDishKeywords());
        compare(changes, "excludedCuisines", before.getExcludedCuisines(),
                after.getExcludedCuisines());
        compare(changes, "excludedMerchantTypes", before.getExcludedMerchantTypes(),
                after.getExcludedMerchantTypes());
        compare(changes, "distanceKm", before.getDistanceKm(), after.getDistanceKm());
        compare(changes, "minRating", before.getMinRating(), after.getMinRating());
        compare(changes, "ratingPreference", before.getRatingPreference(),
                after.getRatingPreference());
        compare(changes, "scenes", before.getScenes(), after.getScenes());
        compare(changes, "environmentRequirements",
                before.getEnvironmentRequirements(), after.getEnvironmentRequirements());
        compare(changes, "businessTime", before.getBusinessTime(), after.getBusinessTime());
        compare(changes, "businessTargetTime", before.getBusinessTargetTime(),
                after.getBusinessTargetTime());
        compare(changes, "businessTargetNextDay", before.getBusinessTargetNextDay(),
                after.getBusinessTargetNextDay());
        compare(changes, "businessTargetDate", before.getBusinessTargetDate(),
                after.getBusinessTargetDate());
        compare(changes, "businessTargetDayOfWeek", before.getBusinessTargetDayOfWeek(),
                after.getBusinessTargetDayOfWeek());
        compare(changes, "businessTimeWindow", before.getBusinessTimeWindow(),
                after.getBusinessTimeWindow());
        return changes;
    }

    private void compare(List<String> changes, String field, Object left, Object right) {
        if (!Objects.equals(left, right)) changes.add(field);
    }

    private ConstraintState copy(ConstraintState state) {
        return state == null ? new ConstraintState()
                : objectMapper.convertValue(state, ConstraintState.class);
    }

    @SuppressWarnings("unchecked")
    private <T> Map<String, T> safeMap(Map<String, T> value) {
        return value == null ? Map.of() : value;
    }

    private List<String> listFor(ConstraintState state, String field) {
        return switch (field) {
            case "merchantTypes" -> new ArrayList<>(state.getMerchantTypes());
            case "cuisines" -> new ArrayList<>(state.getCuisines());
            case "tastePreferences" -> new ArrayList<>(state.getTastePreferences());
            case "tasteRestrictions" -> new ArrayList<>(state.getTasteRestrictions());
            case "dishKeywords" -> new ArrayList<>(state.getDishKeywords());
            case "excludedCuisines" -> new ArrayList<>(state.getExcludedCuisines());
            case "excludedMerchantTypes" ->
                    new ArrayList<>(state.getExcludedMerchantTypes());
            case "scenes" -> new ArrayList<>(state.getScenes());
            case "environmentRequirements" ->
                    new ArrayList<>(state.getEnvironmentRequirements());
            default -> null;
        };
    }

    private void setList(ConstraintState state, String field, List<String> values) {
        applySet(state, Map.of(field, values));
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (Object item : list) {
            if (item != null) values.add(String.valueOf(item));
        }
        return DiningConstraintNormalizer.normalizeList(values);
    }

    private Integer integer(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private BigDecimal decimal(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Boolean bool(Object value) {
        return value == null ? null : Boolean.valueOf(String.valueOf(value));
    }

    private String text(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private boolean positive(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) > 0;
    }

    public record PatchResult(
            ConstraintState state,
            List<String> changedFields,
            List<ConstraintConflictVO> conflicts
    ) {
    }
}
