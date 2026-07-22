package com.foodadvisor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Iterator;
import java.util.Map;

/**
 * 回归测试 JSON 子集断言。
 *
 * expected 中声明的字段必须在 actual 中存在并匹配；
 * actual 可以包含 expected 未声明的其他字段。
 */
@Service
public class RegressionJsonAssertionService {

    private final ObjectMapper objectMapper;

    public RegressionJsonAssertionService(
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
    }

    public AssertionResult compare(
            JsonNode expected,
            JsonNode actual
    ) {
        ArrayNode failureReasons =
                objectMapper.createArrayNode();

        ComparisonCounter counter =
                new ComparisonCounter();

        compareNode(
                "$",
                expected,
                actual,
                failureReasons,
                counter
        );

        ObjectNode metrics =
                objectMapper.createObjectNode();

        metrics.put(
                "expectedLeafCount",
                counter.expectedLeafCount
        );
        metrics.put(
                "matchedLeafCount",
                counter.matchedLeafCount
        );
        metrics.put(
                "failureCount",
                failureReasons.size()
        );

        BigDecimal accuracy =
                counter.expectedLeafCount == 0
                        ? BigDecimal.ONE
                        : BigDecimal.valueOf(
                                counter.matchedLeafCount
                        ).divide(
                                BigDecimal.valueOf(
                                        counter.expectedLeafCount
                                ),
                                4,
                                RoundingMode.HALF_UP
                        );

        metrics.put("accuracy", accuracy);

        return new AssertionResult(
                failureReasons.isEmpty(),
                metrics,
                failureReasons
        );
    }

    private void compareNode(
            String path,
            JsonNode expected,
            JsonNode actual,
            ArrayNode failureReasons,
            ComparisonCounter counter
    ) {
        if (expected == null) {
            return;
        }

        if (expected.isObject()) {
            compareObject(
                    path,
                    expected,
                    actual,
                    failureReasons,
                    counter
            );
            return;
        }

        if (expected.isArray()) {
            compareArray(
                    path,
                    expected,
                    actual,
                    failureReasons,
                    counter
            );
            return;
        }

        counter.expectedLeafCount++;

        if (scalarEquals(expected, actual)) {
            counter.matchedLeafCount++;
            return;
        }

        addFailure(
                failureReasons,
                path,
                actual == null
                        ? "MISSING_VALUE"
                        : "VALUE_MISMATCH",
                "实际值与期望值不一致",
                expected,
                actual
        );
    }

    private void compareObject(
            String path,
            JsonNode expected,
            JsonNode actual,
            ArrayNode failureReasons,
            ComparisonCounter counter
    ) {
        if (actual == null || !actual.isObject()) {
            counter.expectedLeafCount +=
                    countLeaves(expected);

            addFailure(
                    failureReasons,
                    path,
                    actual == null
                            ? "MISSING_OBJECT"
                            : "TYPE_MISMATCH",
                    "期望JSON对象，但实际值不是对象",
                    expected,
                    actual
            );
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> fields =
                expected.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field =
                    fields.next();

            compareNode(
                    path + "." + field.getKey(),
                    field.getValue(),
                    actual.get(field.getKey()),
                    failureReasons,
                    counter
            );
        }
    }

    private void compareArray(
            String path,
            JsonNode expected,
            JsonNode actual,
            ArrayNode failureReasons,
            ComparisonCounter counter
    ) {
        if (actual == null || !actual.isArray()) {
            counter.expectedLeafCount +=
                    countLeaves(expected);

            addFailure(
                    failureReasons,
                    path,
                    actual == null
                            ? "MISSING_ARRAY"
                            : "TYPE_MISMATCH",
                    "期望JSON数组，但实际值不是数组",
                    expected,
                    actual
            );
            return;
        }

        int expectedIndex = 0;

        for (JsonNode expectedItem : expected) {
            int leafCount =
                    Math.max(1, countLeaves(expectedItem));

            counter.expectedLeafCount += leafCount;

            boolean found = false;

            for (JsonNode actualItem : actual) {
                if (matchesNode(
                        expectedItem,
                        actualItem
                )) {
                    found = true;
                    break;
                }
            }

            if (found) {
                counter.matchedLeafCount += leafCount;
            } else {
                addFailure(
                        failureReasons,
                        path + "[" + expectedIndex + "]",
                        "ARRAY_ELEMENT_MISSING",
                        "实际数组中不存在符合期望的元素",
                        expectedItem,
                        actual
                );
            }

            expectedIndex++;
        }
    }

    private boolean matchesNode(
            JsonNode expected,
            JsonNode actual
    ) {
        if (expected == null) {
            return true;
        }

        if (expected.isObject()) {
            if (actual == null || !actual.isObject()) {
                return false;
            }

            Iterator<Map.Entry<String, JsonNode>> fields =
                    expected.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field =
                        fields.next();

                if (!matchesNode(
                        field.getValue(),
                        actual.get(field.getKey())
                )) {
                    return false;
                }
            }

            return true;
        }

        if (expected.isArray()) {
            if (actual == null || !actual.isArray()) {
                return false;
            }

            for (JsonNode expectedItem : expected) {
                boolean found = false;

                for (JsonNode actualItem : actual) {
                    if (matchesNode(
                            expectedItem,
                            actualItem
                    )) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    return false;
                }
            }

            return true;
        }

        return scalarEquals(expected, actual);
    }

    private boolean scalarEquals(
            JsonNode expected,
            JsonNode actual
    ) {
        if (actual == null) {
            return false;
        }

        if (expected.isNull()) {
            return actual.isNull();
        }

        /*
         * 允许 1、1.0、1.00 被视为同一数值。
         */
        if (expected.isNumber() && actual.isNumber()) {
            return expected.decimalValue()
                    .compareTo(actual.decimalValue()) == 0;
        }

        return expected.equals(actual);
    }

    private int countLeaves(JsonNode node) {
        if (node == null) {
            return 0;
        }

        if (node.isObject()) {
            int count = 0;

            Iterator<JsonNode> elements =
                    node.elements();

            while (elements.hasNext()) {
                count += countLeaves(elements.next());
            }

            return count;
        }

        if (node.isArray()) {
            int count = 0;

            for (JsonNode item : node) {
                count += Math.max(
                        1,
                        countLeaves(item)
                );
            }

            return count;
        }

        return 1;
    }

    private void addFailure(
            ArrayNode failureReasons,
            String path,
            String type,
            String message,
            JsonNode expected,
            JsonNode actual
    ) {
        ObjectNode failure =
                failureReasons.addObject();

        failure.put("path", path);
        failure.put("type", type);
        failure.put("message", message);

        failure.set(
                "expected",
                expected == null
                        ? NullNode.getInstance()
                        : expected.deepCopy()
        );

        failure.set(
                "actual",
                actual == null
                        ? NullNode.getInstance()
                        : actual.deepCopy()
        );
    }

    public record AssertionResult(
            boolean passed,
            JsonNode metrics,
            JsonNode failureReasons
    ) {
    }

    private static final class ComparisonCounter {
        private int expectedLeafCount;
        private int matchedLeafCount;
    }
}