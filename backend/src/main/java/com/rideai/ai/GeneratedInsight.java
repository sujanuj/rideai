package com.rideai.ai;

import java.util.List;

/** What the assistant writes about a trip. */
public record GeneratedInsight(String summary, List<Suggestion> suggestions, String model) {

    public record Suggestion(String title, String detail) {
    }
}
