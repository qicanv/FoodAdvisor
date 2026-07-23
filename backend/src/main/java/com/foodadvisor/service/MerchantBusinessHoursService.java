package com.foodadvisor.service;

import com.foodadvisor.dto.constraint.ConstraintState;
import com.foodadvisor.entity.MerchantBusinessHours;
import com.foodadvisor.mapper.BusinessHoursMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MerchantBusinessHoursService {

    static final LocalTime TONIGHT_START = LocalTime.of(19, 0);
    static final LocalTime TONIGHT_END = LocalTime.of(22, 0);
    static final LocalTime LATE_NIGHT_THRESHOLD = LocalTime.of(23, 0);

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm");

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MerchantBusinessHoursService.class);

    private final BusinessHoursMapper businessHoursMapper;
    private final ZoneId businessZoneId;
    private final Clock clock;

    public MerchantBusinessHoursService(
            BusinessHoursMapper businessHoursMapper,
            ZoneId businessZoneId,
            Clock businessClock
    ) {
        this.businessHoursMapper = businessHoursMapper;
        this.businessZoneId = businessZoneId;
        this.clock = businessClock;
    }

    public Map<Long, List<MerchantBusinessHours>> loadGrouped(
            Collection<Long> merchantIds
    ) {
        Map<Long, List<MerchantBusinessHours>> grouped =
                new LinkedHashMap<>();
        if (merchantIds == null || merchantIds.isEmpty()) {
            return grouped;
        }

        List<Long> ids = merchantIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return grouped;
        }

        List<MerchantBusinessHours> rows =
                businessHoursMapper.selectByMerchantIds(ids);
        if (rows != null) {
            for (MerchantBusinessHours row : rows) {
                grouped.computeIfAbsent(
                        row.getMerchantId(),
                        ignored -> new ArrayList<>()
                ).add(row);
            }
        }
        return grouped;
    }

    public boolean hasBusinessTimeConstraint(
            ConstraintState constraints
    ) {
        return constraints != null
                && (hasText(constraints.getBusinessTargetTime())
                || hasText(constraints.getBusinessTime())
                || hasText(constraints.getBusinessTargetDate())
                || constraints.getBusinessTargetDayOfWeek() != null
                || hasText(constraints.getBusinessTimeWindow()));
    }

    public BusinessHoursMatch match(
            ConstraintState constraints,
            List<MerchantBusinessHours> hours
    ) {
        if (!hasBusinessTimeConstraint(constraints)) {
            return BusinessHoursMatch.unrestricted();
        }
        if (hours == null || hours.isEmpty()) {
            return BusinessHoursMatch.notMatched();
        }

        ZoneId targetZone = resolveZone(constraints.getTimezone());
        ZonedDateTime now = ZonedDateTime.now(clock)
                .withZoneSameInstant(targetZone);

        if (hasText(constraints.getBusinessTargetTime())
                || hasText(constraints.getBusinessTargetDate())
                || constraints.getBusinessTargetDayOfWeek() != null
                || hasText(constraints.getBusinessTimeWindow())) {
            LocalTime target = hasText(constraints.getBusinessTargetTime())
                    ? parseTargetTime(constraints.getBusinessTargetTime())
                    : timeForWindow(constraints.getBusinessTimeWindow());
            if (target == null) {
                return BusinessHoursMatch.notMatched();
            }
            LocalDate date = resolveTargetDate(constraints, now.toLocalDate());
            return matchAt(hours, date.atTime(target).atZone(targetZone),
                    target.format(TIME_FORMAT) + "仍处于营业时段");
        }

        return switch (constraints.getBusinessTime()) {
            case "NOW_OPEN" -> matchAt(
                    hours,
                    now,
                    "当前处于营业时段"
            );
            case "TONIGHT" -> matchTonight(hours, now);
            case "LATE_NIGHT" -> matchLateNight(hours, now.toLocalDate());
            default -> BusinessHoursMatch.notMatched();
        };
    }

    private BusinessHoursMatch matchTonight(
            List<MerchantBusinessHours> hours,
            ZonedDateTime now
    ) {
        LocalDate today = now.toLocalDate();
        ZonedDateTime end = today.atTime(TONIGHT_END)
                .atZone(businessZoneId);
        if (!now.isBefore(end)) {
            return BusinessHoursMatch.notMatched();
        }
        ZonedDateTime start = now.isAfter(
                today.atTime(TONIGHT_START).atZone(businessZoneId)
        ) ? now : today.atTime(TONIGHT_START).atZone(businessZoneId);

        for (TimePeriod period : periodsFor(hours, today)) {
            if (period.start().isBefore(end)
                    && period.end().isAfter(start)) {
                return matched(period, "今晚存在可用营业时段");
            }
        }
        return BusinessHoursMatch.notMatched();
    }

    private BusinessHoursMatch matchLateNight(
            List<MerchantBusinessHours> hours,
            LocalDate today
    ) {
        ZonedDateTime threshold = today.atTime(LATE_NIGHT_THRESHOLD)
                .atZone(businessZoneId);
        for (TimePeriod period : periodsFor(hours, today)) {
            if (!period.start().isAfter(threshold)
                    && !period.end().isBefore(threshold)) {
                return matched(period, "支持深夜营业");
            }
        }
        return BusinessHoursMatch.notMatched();
    }

    private BusinessHoursMatch matchAt(
            List<MerchantBusinessHours> hours,
            ZonedDateTime target,
            String prefix
    ) {
        for (TimePeriod period : periodsFor(
                hours,
                target.toLocalDate()
        )) {
            if (!target.isBefore(period.start())
                    && target.isBefore(period.end())) {
                return matched(period, prefix);
            }
        }
        return BusinessHoursMatch.notMatched();
    }

    private List<TimePeriod> periodsFor(
            List<MerchantBusinessHours> hours,
            LocalDate targetDate
    ) {
        List<TimePeriod> periods = new ArrayList<>();
        appendPeriods(hours, targetDate, periods);
        appendPeriods(hours, targetDate.minusDays(1), periods);
        return periods;
    }

    private void appendPeriods(
            List<MerchantBusinessHours> hours,
            LocalDate openingDate,
            List<TimePeriod> periods
    ) {
        int day = openingDate.getDayOfWeek().getValue();
        boolean hasClosed = false;
        boolean hasOpen = false;
        for (MerchantBusinessHours row : hours) {
            if (row.getDayOfWeek() == null
                    || row.getDayOfWeek() != day) {
                continue;
            }
            if (Boolean.TRUE.equals(row.getIsClosed())) {
                hasClosed = true;
                continue;
            }
            if (row.getOpenTime() == null || row.getCloseTime() == null) {
                continue;
            }
            hasOpen = true;
            ZonedDateTime start = openingDate
                    .atTime(row.getOpenTime())
                    .atZone(businessZoneId);
            boolean crossesMidnight =
                    Boolean.TRUE.equals(row.getCrossesMidnight());
            ZonedDateTime end = (crossesMidnight
                    ? openingDate.plusDays(1)
                    : openingDate)
                    .atTime(row.getCloseTime())
                    .atZone(businessZoneId);
            if (end.isAfter(start)) {
                periods.add(new TimePeriod(start, end, crossesMidnight));
            }
        }
        if (hasClosed && hasOpen) {
            LOGGER.warn(
                    "Merchant business hours contain both closed and open rows for day {}",
                    day
            );
        }
    }

    private BusinessHoursMatch matched(
            TimePeriod period,
            String prefix
    ) {
        String end = period.crossesMidnight()
                ? "次日" + period.end().toLocalTime().format(TIME_FORMAT)
                : period.end().toLocalTime().format(TIME_FORMAT);
        String evidence = prefix + " "
                + period.start().toLocalTime().format(TIME_FORMAT)
                + "–" + end;
        return new BusinessHoursMatch(true, evidence);
    }

    private LocalTime parseTargetTime(String value) {
        try {
            return LocalTime.parse(value, TIME_FORMAT);
        } catch (DateTimeParseException exception) {
            LOGGER.warn("Ignoring invalid business target time: {}", value);
            return null;
        }
    }

    /**
     * 周末固定解析为“从今天起最近的周六”；若今天是周六则使用今天。
     * 若模型明确给出周日（7），则按最近周日处理。
     */
    private LocalDate resolveTargetDate(
            ConstraintState constraints,
            LocalDate today
    ) {
        if (hasText(constraints.getBusinessTargetDate())) {
            try {
                return LocalDate.parse(constraints.getBusinessTargetDate());
            } catch (DateTimeParseException exception) {
                LOGGER.warn("Ignoring invalid business target date");
            }
        }
        if (constraints.getBusinessTargetDayOfWeek() != null
                && constraints.getBusinessTargetDayOfWeek() >= 1
                && constraints.getBusinessTargetDayOfWeek() <= 7) {
            return today.with(TemporalAdjusters.nextOrSame(
                    DayOfWeek.of(constraints.getBusinessTargetDayOfWeek())));
        }
        return Boolean.TRUE.equals(constraints.getBusinessTargetNextDay())
                ? today.plusDays(1) : today;
    }

    private LocalTime timeForWindow(String window) {
        if (window == null) return null;
        return switch (window) {
            case "LUNCH" -> LocalTime.NOON;
            case "EVENING", "TONIGHT" -> TONIGHT_START;
            case "LATE_NIGHT" -> LATE_NIGHT_THRESHOLD;
            default -> null;
        };
    }

    private ZoneId resolveZone(String zone) {
        if (!hasText(zone)) return businessZoneId;
        try {
            return ZoneId.of(zone);
        } catch (Exception exception) {
            LOGGER.warn("Ignoring invalid business timezone");
            return businessZoneId;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record TimePeriod(
            ZonedDateTime start,
            ZonedDateTime end,
            boolean crossesMidnight
    ) {
    }

    public record BusinessHoursMatch(
            boolean matched,
            String evidence
    ) {
        static BusinessHoursMatch unrestricted() {
            return new BusinessHoursMatch(true, null);
        }

        static BusinessHoursMatch notMatched() {
            return new BusinessHoursMatch(false, null);
        }
    }
}
