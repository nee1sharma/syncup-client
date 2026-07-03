package com.hitstudio.syncup.domain.usecase;

import com.hitstudio.syncup.domain.model.DateFilterMode;
import com.hitstudio.syncup.domain.model.DateRange;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

public final class DateRangeCalculator {

    private DateRangeCalculator() {
    }

    public static DateRange calculate(
            DateFilterMode mode,
            LocalDate customFrom,
            LocalDate customTo,
            ZoneId zoneId,
            Clock clock
    ) {
        LocalDate today = LocalDate.now(clock.withZone(zoneId));
        LocalDate from;
        LocalDate toExclusive;

        switch (mode) {
            case TODAY:
                from = today;
                toExclusive = today.plusDays(1);
                break;
            case YESTERDAY:
                from = today.minusDays(1);
                toExclusive = today;
                break;
            case LAST_7_DAYS:
                from = today.minusDays(6);
                toExclusive = today.plusDays(1);
                break;
            case CUSTOM_RANGE:
                if (customFrom == null || customTo == null) {
                    throw new IllegalArgumentException("Custom dates are required");
                }
                if (customTo.isBefore(customFrom)) {
                    throw new IllegalArgumentException("Custom end date is before start date");
                }
                from = customFrom;
                toExclusive = customTo.plusDays(1);
                break;
            case ALL:
                return null;
            default:
                throw new IllegalArgumentException("Unsupported date mode: " + mode);
        }

        return new DateRange(
                from.atStartOfDay(zoneId).toInstant(),
                toExclusive.atStartOfDay(zoneId).toInstant()
        );
    }
}
