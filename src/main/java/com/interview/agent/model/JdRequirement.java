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
public class JdRequirement {
    private String title;
    @Builder.Default
    private List<String> techStack = new ArrayList<>();
    @Builder.Default
    private List<String> requiredSkills = new ArrayList<>();
    private String experience;
    private String summary;
}
