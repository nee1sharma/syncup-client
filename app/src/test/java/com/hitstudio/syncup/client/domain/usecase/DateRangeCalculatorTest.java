package com.hitstudio.syncup.client.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.hitstudio.syncup.client.domain.model.DateFilterMode;
import com.hitstudio.syncup.client.domain.model.DateRange;
import com.hitstudio.syncup.client.domain.usecase.DateRangeCalculator;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

public class DateRangeCalculatorTest {

    private static final Clock JULY_THIRD_2026 =
            Clock.fixed(Instant.parse("2026-07-03T10:00:00Z"), ZoneOffset.UTC);

    @Test
    public void todayUsesInclusiveStartAndExclusiveNextDay() {
        DateRange range = DateRangeCalculator.calculate(
                DateFilterMode.TODAY,
                null,
                null,
                ZoneOffset.UTC,
                JULY_THIRD_2026
        );

        assertEquals(Instant.parse("2026-07-03T00:00:00Z"), range.getFromInclusive());
        assertEquals(Instant.parse("2026-07-04T00:00:00Z"), range.getToExclusive());
    }

    @Test
    public void lastSevenDaysIncludesToday() {
        DateRange range = DateRangeCalculator.calculate(
                DateFilterMode.LAST_7_DAYS,
                null,
                null,
                ZoneOffset.UTC,
                JULY_THIRD_2026
        );

        assertEquals(Instant.parse("2026-06-27T00:00:00Z"), range.getFromInclusive());
        assertEquals(Instant.parse("2026-07-04T00:00:00Z"), range.getToExclusive());
    }

    @Test
    public void customRangeUsesDeviceTimezoneAndIncludesEndDate() {
        DateRange range = DateRangeCalculator.calculate(
                DateFilterMode.CUSTOM_RANGE,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 3),
                ZoneId.of("Asia/Kolkata"),
                JULY_THIRD_2026
        );

        assertEquals(Instant.parse("2026-06-30T18:30:00Z"), range.getFromInclusive());
        assertEquals(Instant.parse("2026-07-03T18:30:00Z"), range.getToExclusive());
    }

    @Test
    public void allDatesHasNoRange() {
        assertNull(DateRangeCalculator.calculate(
                DateFilterMode.ALL,
                null,
                null,
                ZoneOffset.UTC,
                JULY_THIRD_2026
        ));
    }
}
