package com.interview.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewPlan {
    @Builder.Default
    private List<String> focusTopics = new ArrayList<>();
    @Builder.Default
    private List<String> resources = new ArrayList<>();
    @Builder.Default
    private List<String> practiceSuggestions = new ArrayList<>();
    private String summary;
}
