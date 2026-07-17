package com.interview.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Question {
    private String id;
    private String topic;
    private String type;
    private Difficulty difficulty;
    private String content;
    private String referenceAnswer;
    private QuestionSource source;
}
