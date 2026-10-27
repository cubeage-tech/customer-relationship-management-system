package com.company.crm.ai.perf;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * NFR-7.1c: how fast each AI feature answers, against its budget — 2 s for the statistical ones
 * (scores, forecasts, recommendations, predictions), 8 s for the generative ones (a language model).
 * Keeps the last 500 timings per feature on this server; a slow answer is logged.
 */
@Component
@Slf4j
public class AiLatency {

    public static final long STATISTICAL_BUDGET_MS = 2_000;
    public static final long GENERATIVE_BUDGET_MS = 8_000;
    private static final int SAMPLES = 500;

    public enum Kind { STATISTICAL, GENERATIVE }

    public record FeatureLatency(String feature, String kind, long budgetMs, int samples, long p50Ms, long p95Ms, long maxMs,
                                 double withinBudgetPercent) {}

    private static final class Window {
        final Kind kind;
        final long[] ms = new long[SAMPLES];
        int count;
        int next;

        Window(Kind kind) {
            this.kind = kind;
        }

        synchronized void add(long value) {
            ms[next] = value;
            next = (next + 1) % SAMPLES;
            count = Math.min(count + 1, SAMPLES);
        }

        synchronized long[] values() {
            return Arrays.copyOf(ms, count);
        }
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /** Runs {@code work}, timing it as {@code feature} — also when it fails. */
    public <T> T time(String feature, Kind kind, Supplier<T> work) {
        long start = System.nanoTime();
        try {
            return work.get();
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            windows.computeIfAbsent(feature, f -> new Window(kind)).add(ms);
            long budget = budget(kind);
            if (ms > budget) {
                log.warn("AI feature {} took {} ms (budget {} ms)", feature, ms, budget);
            }
        }
    }

    public List<FeatureLatency> snapshot() {
        List<FeatureLatency> out = new ArrayList<>();
        windows.forEach((feature, w) -> {
            long[] v = w.values();
            if (v.length == 0) {
                return;
            }
            Arrays.sort(v);
            long budget = budget(w.kind);
            long within = Arrays.stream(v).filter(ms -> ms <= budget).count();
            out.add(new FeatureLatency(feature, w.kind.name().toLowerCase(), budget, v.length, percentile(v, 0.5), percentile(v, 0.95),
                    v[v.length - 1], Math.round(within * 1000.0 / v.length) / 10.0));
        });
        out.sort((a, b) -> a.feature().compareTo(b.feature()));
        return out;
    }

    private static long budget(Kind kind) {
        return kind == Kind.STATISTICAL ? STATISTICAL_BUDGET_MS : GENERATIVE_BUDGET_MS;
    }

    /** Nearest-rank percentile of sorted values. */
    static long percentile(long[] sorted, double p) {
        int rank = (int) Math.ceil(p * sorted.length);
        return sorted[Math.max(0, Math.min(sorted.length - 1, rank - 1))];
    }
}
