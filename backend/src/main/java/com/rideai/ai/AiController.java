package com.rideai.ai;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rideai.ai.InsightService.AnswerResponse;
import com.rideai.ai.InsightService.InsightResponse;
import com.rideai.common.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/trips/{id}")
public class AiController {

    private final InsightService insights;

    public AiController(InsightService insights) {
        this.insights = insights;
    }

    public record Question(@NotBlank @Size(max = 500) String question) {
    }

    /** 200 with the AI summary, or 404 INSIGHT_PENDING while it's being written. */
    @GetMapping("/insight")
    public InsightResponse insight(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return insights.get(id, CurrentUser.id(jwt));
    }

    @PostMapping("/assistant")
    public AnswerResponse ask(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody Question q) {
        return insights.ask(id, CurrentUser.id(jwt), q.question());
    }
}
