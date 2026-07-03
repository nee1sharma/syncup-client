package com.hitstudio.syncup.domain.model;

import java.time.Instant;
import java.util.Objects;

public final class DateRange {

    private final Instant fromInclusive;
    private final Instant toExclusive;

    public DateRange(Instant fromInclusive, Instant toExclusive) {
        this.fromInclusive = Objects.requireNonNull(fromInclusive);
        this.toExclusive = Objects.requireNonNull(toExclusive);
        if (!toExclusive.isAfter(fromInclusive)) {
            throw new IllegalArgumentException("Range end must be after its start");
        }
    }

    public Instant getFromInclusive() {
        return fromInclusive;
    }

    public Instant getToExclusive() {
        return toExclusive;
    }
}
