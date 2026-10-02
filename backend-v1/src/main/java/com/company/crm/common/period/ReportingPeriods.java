package com.company.crm.common.period;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * The time windows dashboards report on, in one place so every summary agrees on what
 * "this week" means: weeks start on Monday, months and quarters on their first day, all at
 * midnight in the application's time zone. Backed by an injectable Clock for tests.
 */
@Component
public class ReportingPeriods {

    private final Clock clock;

    public ReportingPeriods(Clock clock) {
        this.clock = clock;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public LocalDate startOfWeek() {
        return today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public LocalDate endOfWeek() {
        return startOfWeek().plusDays(6);
    }

    public LocalDateTime startOfWeekAt() {
        return startOfWeek().atStartOfDay();
    }

    public LocalDateTime startOfMonth() {
        return today().withDayOfMonth(1).atStartOfDay();
    }

    public LocalDateTime startOfQuarter() {
        LocalDate today = today();
        int firstMonthOfQuarter = ((today.getMonthValue() - 1) / 3) * 3 + 1;
        return LocalDate.of(today.getYear(), firstMonthOfQuarter, 1).atStartOfDay();
    }
}
