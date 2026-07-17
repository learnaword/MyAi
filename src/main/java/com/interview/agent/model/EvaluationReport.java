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
public class EvaluationReport {
    private int overallScore;
    private int technicalDepth;
    private int clarity;
    private int logic;
    private int projectDemo;
    private String summary;
    @Builder.Default
    private List<String> suggestions = new ArrayList<>();
    @Builder.Default
    private List<String> weakTopics = new ArrayList<>();
}
