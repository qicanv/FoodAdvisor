package com.foodadvisor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegressionJsonAssertionServiceTest {

    private ObjectMapper objectMapper;

    private RegressionJsonAssertionService assertionService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        assertionService =
                new RegressionJsonAssertionService(
                        objectMapper
                );
    }

    @Test
    void shouldMatchObjectSubset() throws Exception {
        JsonNode expected =
                objectMapper.readTree(
                        """
                        {
                          "cuisine": "川菜",
                          "budget": {
                            "max": 80
                          }
                        }
                        """
                );

        JsonNode actual =
                objectMapper.readTree(
                        """
                        {
                          "cuisine": "川菜",
                          "budget": {
                            "min": 40,
                            "max": 80
                          },
                          "distanceKm": 3
                        }
                        """
                );

        RegressionJsonAssertionService.AssertionResult result =
                assertionService.compare(
                        expected,
                        actual
                );

        assertTrue(result.passed());
        assertEquals(
                2,
                result.metrics()
                        .get("expectedLeafCount")
                        .asInt()
        );
        assertEquals(
                2,
                result.metrics()
                        .get("matchedLeafCount")
                        .asInt()
        );
        assertEquals(
                0,
                result.metrics()
                        .get("failureCount")
                        .asInt()
        );
        assertTrue(
                result.failureReasons()
                        .isEmpty()
        );
    }

    @Test
    void shouldMatchArrayWithoutDependingOnOrder()
            throws Exception {

        JsonNode expected =
                objectMapper.readTree(
                        """
                        {
                          "recommendations": [
                            {
                              "merchantId": 1,
                              "name": "商家A"
                            },
                            {
                              "merchantId": 2
                            }
                          ]
                        }
                        """
                );

        JsonNode actual =
                objectMapper.readTree(
                        """
                        {
                          "recommendations": [
                            {
                              "merchantId": 2,
                              "name": "商家B"
                            },
                            {
                              "merchantId": 1,
                              "name": "商家A",
                              "score": 95.5
                            },
                            {
                              "merchantId": 3,
                              "name": "商家C"
                            }
                          ]
                        }
                        """
                );

        RegressionJsonAssertionService.AssertionResult result =
                assertionService.compare(
                        expected,
                        actual
                );

        assertTrue(result.passed());
        assertEquals(
                3,
                result.metrics()
                        .get("expectedLeafCount")
                        .asInt()
        );
        assertEquals(
                3,
                result.metrics()
                        .get("matchedLeafCount")
                        .asInt()
        );
        assertTrue(
                result.failureReasons()
                        .isEmpty()
        );
    }

    @Test
    void shouldTreatEquivalentNumbersAsEqual()
            throws Exception {

        JsonNode expected =
                objectMapper.readTree(
                        """
                        {
                          "score": 1,
                          "confidence": 0.80
                        }
                        """
                );

        JsonNode actual =
                objectMapper.readTree(
                        """
                        {
                          "score": 1.0,
                          "confidence": 0.8000
                        }
                        """
                );

        RegressionJsonAssertionService.AssertionResult result =
                assertionService.compare(
                        expected,
                        actual
                );

        assertTrue(result.passed());

        BigDecimal accuracy =
                result.metrics()
                        .get("accuracy")
                        .decimalValue();

        assertEquals(
                0,
                BigDecimal.ONE.compareTo(
                        accuracy
                )
        );
    }

    @Test
    void shouldReportMissingField()
            throws Exception {

        JsonNode expected =
                objectMapper.readTree(
                        """
                        {
                          "cuisine": "川菜",
                          "budget": 80
                        }
                        """
                );

        JsonNode actual =
                objectMapper.readTree(
                        """
                        {
                          "cuisine": "川菜"
                        }
                        """
                );

        RegressionJsonAssertionService.AssertionResult result =
                assertionService.compare(
                        expected,
                        actual
                );

        assertFalse(result.passed());
        assertEquals(
                1,
                result.metrics()
                        .get("failureCount")
                        .asInt()
        );
        assertEquals(
                "$.budget",
                result.failureReasons()
                        .get(0)
                        .get("path")
                        .asText()
        );
        assertEquals(
                "MISSING_VALUE",
                result.failureReasons()
                        .get(0)
                        .get("type")
                        .asText()
        );
    }

    @Test
    void shouldReportValueMismatch()
            throws Exception {

        JsonNode expected =
                objectMapper.readTree(
                        """
                        {
                          "sentiment": "POSITIVE"
                        }
                        """
                );

        JsonNode actual =
                objectMapper.readTree(
                        """
                        {
                          "sentiment": "NEGATIVE"
                        }
                        """
                );

        RegressionJsonAssertionService.AssertionResult result =
                assertionService.compare(
                        expected,
                        actual
                );

        assertFalse(result.passed());
        assertEquals(
                "$.sentiment",
                result.failureReasons()
                        .get(0)
                        .get("path")
                        .asText()
        );
        assertEquals(
                "VALUE_MISMATCH",
                result.failureReasons()
                        .get(0)
                        .get("type")
                        .asText()
        );
        assertEquals(
                "POSITIVE",
                result.failureReasons()
                        .get(0)
                        .get("expected")
                        .asText()
        );
        assertEquals(
                "NEGATIVE",
                result.failureReasons()
                        .get(0)
                        .get("actual")
                        .asText()
        );
    }

    @Test
    void shouldReportNestedFailurePath()
            throws Exception {

        JsonNode expected =
                objectMapper.readTree(
                        """
                        {
                          "constraints": {
                            "budget": {
                              "perCapita": 80
                            }
                          }
                        }
                        """
                );

        JsonNode actual =
                objectMapper.readTree(
                        """
                        {
                          "constraints": {
                            "budget": {
                              "perCapita": 100
                            }
                          }
                        }
                        """
                );

        RegressionJsonAssertionService.AssertionResult result =
                assertionService.compare(
                        expected,
                        actual
                );

        assertFalse(result.passed());
        assertEquals(
                "$.constraints.budget.perCapita",
                result.failureReasons()
                        .get(0)
                        .get("path")
                        .asText()
        );
        assertEquals(
                "VALUE_MISMATCH",
                result.failureReasons()
                        .get(0)
                        .get("type")
                        .asText()
        );
        assertEquals(
                1,
                result.metrics()
                        .get("expectedLeafCount")
                        .asInt()
        );
        assertEquals(
                0,
                result.metrics()
                        .get("matchedLeafCount")
                        .asInt()
        );
    }
}