package com.rideai.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideai.ai.GeneratedInsight.Suggestion;

/**
 * Minimal client for the Claude Messages API (https://docs.claude.com).
 *
 * For insights we force a "tool call" with a JSON schema, so Claude has to answer in exactly
 * the structure we store: {summary, suggestions[{title, detail}]}. That's more reliable than
 * asking for JSON in plain text.
 */
@Component
public class ClaudeClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);

    static final String INSIGHT_SYSTEM_PROMPT = """
        You are the trip assistant inside RideAI, a ride-hailing app. You receive facts about ONE
        completed trip as JSON, computed by our backend. Rules:
        - Only use numbers that appear in the input. Never invent or recalculate figures.
        - Write the summary in 2 short sentences, second person ("your driver"), friendly and specific.
        - Give at most 3 suggestions the RIDER can act on next time (timing, pickup spot, planning).
          Base each one on a flag or number in the input. If the trip went smoothly, return none.
        - The "flags" list marks what our rules detected: SLOW_MATCH, LATE_PICKUP, LONG_WAIT,
          LONG_BOARDING, SLOW_RIDE, ROUTE_DEVIATION, DELAY_ALERT, SMOOTH_TRIP.
        Record your answer with the record_trip_insight tool.""";

    static final String CHAT_SYSTEM_PROMPT = """
        You are the trip assistant inside RideAI. Answer the rider's question about the trip
        described in the JSON below, in at most 4 sentences. Use only facts from the JSON; if the
        answer isn't there, say you don't have that information. Don't discuss other topics.""";

    private static final Map<String, Object> INSIGHT_TOOL = Map.of(
        "name", "record_trip_insight",
        "description", "Record the trip summary and suggestions shown to the rider.",
        "input_schema", Map.of(
            "type", "object",
            "properties", Map.of(
                "summary", Map.of("type", "string", "description", "Two sentences about how the trip went."),
                "suggestions", Map.of(
                    "type", "array",
                    "maxItems", 3,
                    "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "title", Map.of("type", "string", "description", "Short action, max 6 words"),
                            "detail", Map.of("type", "string", "description", "One sentence citing the numbers")),
                        "required", List.of("title", "detail")))),
            "required", List.of("summary", "suggestions")));

    private final RestClient http;
    private final ObjectMapper json;
    private final String apiKey;
    private final String model;

    public ClaudeClient(RestClient.Builder builder, ObjectMapper json,
                        @Value("${rideai.ai.api-key:}") String apiKey,
                        @Value("${rideai.ai.model:claude-haiku-4-5-20251001}") String model,
                        @Value("${rideai.ai.base-url:https://api.anthropic.com}") String baseUrl) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(5));
        timeouts.setReadTimeout(Duration.ofSeconds(20));
        this.http = builder.baseUrl(baseUrl).requestFactory(timeouts).build();
        this.json = json;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    public String model() {
        return model;
    }

    /** Ask Claude for the insight. Throws on HTTP errors; returns null if the reply is unusable. */
    public GeneratedInsight insight(TripMetrics metrics) throws Exception {
        Map<String, Object> request = Map.of(
            "model", model,
            "max_tokens", 600,
            "system", INSIGHT_SYSTEM_PROMPT,
            "tools", List.of(INSIGHT_TOOL),
            "tool_choice", Map.of("type", "tool", "name", "record_trip_insight"),
            "messages", List.of(Map.of("role", "user",
                "content", "Trip facts:\n" + json.writeValueAsString(metrics))));
        return parseInsight(call(request), model);
    }

    /** Free-form question about a trip. */
    public String chat(String tripContextJson, String question) throws Exception {
        Map<String, Object> request = Map.of(
            "model", model,
            "max_tokens", 400,
            "system", CHAT_SYSTEM_PROMPT + "\n\nTrip:\n" + tripContextJson,
            "messages", List.of(Map.of("role", "user", "content", question)));
        JsonNode reply = call(request);
        StringBuilder text = new StringBuilder();
        for (JsonNode block : reply.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText());
            }
        }
        return text.toString().trim();
    }

    private JsonNode call(Map<String, Object> request) {
        JsonNode reply = http.post()
            .uri("/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(JsonNode.class);
        JsonNode usage = reply == null ? null : reply.path("usage");
        if (usage != null) {
            log.info("Claude call: model={} input_tokens={} output_tokens={}",
                model, usage.path("input_tokens").asInt(), usage.path("output_tokens").asInt());
        }
        return reply;
    }

    /** Pulls the tool input out of a Messages API reply and validates it. */
    static GeneratedInsight parseInsight(JsonNode reply, String model) {
        if (reply == null) {
            return null;
        }
        for (JsonNode block : reply.path("content")) {
            if (!"tool_use".equals(block.path("type").asText())) {
                continue;
            }
            JsonNode input = block.path("input");
            String summary = input.path("summary").asText("").trim();
            if (summary.isEmpty() || summary.length() > 600) {
                return null;
            }
            List<Suggestion> suggestions = new ArrayList<>();
            for (JsonNode s : input.path("suggestions")) {
                String title = s.path("title").asText("").trim();
                String detail = s.path("detail").asText("").trim();
                if (!title.isEmpty() && !detail.isEmpty() && suggestions.size() < 3) {
                    suggestions.add(new Suggestion(title, detail));
                }
            }
            return new GeneratedInsight(summary, suggestions, model);
        }
        return null;
    }
}
