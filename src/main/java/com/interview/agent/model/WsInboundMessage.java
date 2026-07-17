package com.interview.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WsInboundMessage {
    private String type;
    private String content;
    private String jd;
    private String jdUrl;
    private String resumeText;
    private String resumeBase64;
    private String resumeFilename;
    private String answer;
    private String filename;
    private String fileBase64;
    private String sessionId;
}
