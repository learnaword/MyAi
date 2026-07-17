package com.interview.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewTurn {
    private Question question;
    private String answer;
    private String followUpQuestion;
    private String followUpAnswer;
    /** good | partial | poor */
    private String verdict;
    private int score;
}
