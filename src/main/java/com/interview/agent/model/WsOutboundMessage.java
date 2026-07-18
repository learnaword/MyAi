package com.interview.agent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WsOutboundMessage {
    private String type;
    private String content;
    private Object data;
    private String sessionId;
    private String error;
    /** Optional AI observability correlation id */
    private String traceId;

    public static WsOutboundMessage of(String type, String content) {
        return WsOutboundMessage.builder().type(type).content(content).build();
    }

    public static WsOutboundMessage error(String message) {
        return WsOutboundMessage.builder().type("error").error(message).content(message).build();
    }
}
