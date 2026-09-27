package com.rideai.ai;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "trip_insights")
public class TripInsight {

    @Id
    @Column(name = "trip_id")
    private Long tripId;

    @Column(nullable = false)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Map<String, String>> suggestions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> flags;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private TripMetrics metrics;

    @Column(nullable = false)
    private String model;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected TripInsight() {
        // for JPA
    }

    public TripInsight(long tripId, GeneratedInsight insight, TripMetrics metrics) {
        this.tripId = tripId;
        this.summary = insight.summary();
        this.suggestions = insight.suggestions().stream()
            .map(s -> Map.of("title", s.title(), "detail", s.detail()))
            .toList();
        this.flags = metrics.flags();
        this.metrics = metrics;
        this.model = insight.model();
    }

    public Long getTripId() {
        return tripId;
    }

    public String getSummary() {
        return summary;
    }

    public List<Map<String, String>> getSuggestions() {
        return suggestions;
    }

    public List<String> getFlags() {
        return flags;
    }

    public TripMetrics getMetrics() {
        return metrics;
    }

    public String getModel() {
        return model;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
