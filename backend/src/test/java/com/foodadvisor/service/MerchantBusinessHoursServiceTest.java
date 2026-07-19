package com.foodadvisor.service;

import com.foodadvisor.dto.constraint.ConstraintState;
import com.foodadvisor.entity.MerchantBusinessHours;
import com.foodadvisor.mapper.BusinessHoursMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class MerchantBusinessHoursServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private MerchantBusinessHoursService service;

    @BeforeEach
    void setUp() {
        service = new MerchantBusinessHoursService(
                mock(BusinessHoursMapper.class),
                ZONE,
                Clock.fixed(
                        Instant.parse("2026-07-17T12:00:00Z"),
                        ZONE
                )
        );
    }

    @Test
    void shouldMatchNormalPeriodAndRejectOutsidePeriod() {
        List<MerchantBusinessHours> hours =
                List.of(period(5, "10:00", "22:00", false));

        assertTrue(service.match(target("20:30", false), hours).matched());
        assertFalse(service.match(target("22:30", false), hours).matched());
    }

    @Test
    void shouldRespectClosedDayAndMultiplePeriods() {
        ConstraintState nowOpen = fuzzy("NOW_OPEN");

        assertFalse(service.match(
                nowOpen,
                List.of(closed(5))
        ).matched());
        assertTrue(service.match(
                nowOpen,
                List.of(
                        period(5, "10:00", "14:00", false),
                        period(5, "17:00", "22:00", false)
                )
        ).matched());
    }

    @Test
    void shouldHandleCrossMidnightAndPreviousDay() {
        List<MerchantBusinessHours> hours =
                List.of(period(5, "18:00", "02:00", true));

        assertTrue(service.match(target("23:00", false), hours).matched());
        assertTrue(service.match(target("01:00", true), hours).matched());
        assertFalse(service.match(target("03:00", true), hours).matched());
    }

    @Test
    void shouldHandleSundayAcrossWeekBoundary() {
        service = new MerchantBusinessHoursService(
                mock(BusinessHoursMapper.class),
                ZONE,
                Clock.fixed(
                        Instant.parse("2026-07-19T12:00:00Z"),
                        ZONE
                )
        );
        assertTrue(service.match(
                target("01:00", true),
                List.of(period(7, "18:00", "02:00", true))
        ).matched());
    }

    @Test
    void shouldReturnNotMatchedWhenHoursAreMissing() {
        assertFalse(service.match(fuzzy("NOW_OPEN"), List.of()).matched());
    }

    @Test
    void shouldApplyTonightWindow() {
        assertTrue(service.match(
                fuzzy("TONIGHT"),
                List.of(period(5, "21:30", "23:00", false))
        ).matched());
        assertFalse(service.match(
                fuzzy("TONIGHT"),
                List.of(period(5, "10:00", "18:30", false))
        ).matched());
    }

    @Test
    void shouldApplyLateNightThreshold() {
        assertFalse(service.match(
                fuzzy("LATE_NIGHT"),
                List.of(period(5, "10:00", "22:00", false))
        ).matched());
        assertTrue(service.match(
                fuzzy("LATE_NIGHT"),
                List.of(period(5, "10:00", "23:30", false))
        ).matched());
        assertTrue(service.match(
                fuzzy("LATE_NIGHT"),
                List.of(period(5, "18:00", "02:00", true))
        ).matched());
    }

    private ConstraintState fuzzy(String value) {
        ConstraintState state = new ConstraintState();
        state.setBusinessTime(value);
        return state;
    }

    private ConstraintState target(String value, boolean nextDay) {
        ConstraintState state = new ConstraintState();
        state.setBusinessTargetTime(value);
        state.setBusinessTargetNextDay(nextDay);
        return state;
    }

    private MerchantBusinessHours period(
            int day,
            String open,
            String close,
            boolean crossesMidnight
    ) {
        MerchantBusinessHours row = new MerchantBusinessHours();
        row.setMerchantId(1L);
        row.setDayOfWeek(day);
        row.setOpenTime(LocalTime.parse(open));
        row.setCloseTime(LocalTime.parse(close));
        row.setIsClosed(false);
        row.setCrossesMidnight(crossesMidnight);
        return row;
    }

    private MerchantBusinessHours closed(int day) {
        MerchantBusinessHours row = new MerchantBusinessHours();
        row.setMerchantId(1L);
        row.setDayOfWeek(day);
        row.setIsClosed(true);
        row.setCrossesMidnight(false);
        return row;
    }
}
