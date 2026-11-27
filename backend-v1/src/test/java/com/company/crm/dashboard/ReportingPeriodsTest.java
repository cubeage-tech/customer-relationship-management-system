package com.company.crm.dashboard;

import com.company.crm.common.period.ReportingPeriods;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReportingPeriodsTest {

    private static ReportingPeriods at(String isoDateTime) {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        return new ReportingPeriods(Clock.fixed(ZonedDateTime.of(LocalDateTime.parse(isoDateTime), zone).toInstant(), zone));
    }

    @Test
    void weekRunsMondayToSunday() {
        ReportingPeriods thursday = at("2026-10-01T15:30:00");
        assertThat(thursday.startOfWeek()).isEqualTo(LocalDate.of(2026, 9, 28));
        assertThat(thursday.endOfWeek()).isEqualTo(LocalDate.of(2026, 10, 4));
        assertThat(thursday.startOfWeekAt()).isEqualTo(LocalDateTime.of(2026, 9, 28, 0, 0));

        ReportingPeriods sunday = at("2026-10-04T23:59:00");
        assertThat(sunday.startOfWeek()).isEqualTo(LocalDate.of(2026, 9, 28));

        ReportingPeriods monday = at("2026-10-05T00:00:00");
        assertThat(monday.startOfWeek()).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @Test
    void monthAndQuarterStartAtMidnightOnTheirFirstDay() {
        ReportingPeriods periods = at("2026-11-17T09:00:00");
        assertThat(periods.startOfMonth()).isEqualTo(LocalDateTime.of(2026, 11, 1, 0, 0));
        assertThat(periods.startOfQuarter()).isEqualTo(LocalDateTime.of(2026, 10, 1, 0, 0));

        assertThat(at("2026-03-31T23:00:00").startOfQuarter()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(at("2026-04-01T00:00:00").startOfQuarter()).isEqualTo(LocalDateTime.of(2026, 4, 1, 0, 0));
    }
}
