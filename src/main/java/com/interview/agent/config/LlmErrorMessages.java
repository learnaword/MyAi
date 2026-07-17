package com.interview.agent.config;

import org.springframework.util.StringUtils;

/**
 * 把 Spring AI / DashScope 的难懂反序列化错误转成可读提示。
 */
public final class LlmErrorMessages {

    private LlmErrorMessages() {
    }

    public static String friendly(Throwable error) {
        String raw = rootMessage(error);
        String lower = raw.toLowerCase();
        if (lower.contains("extracting response") && lower.contains("chatcompletion")) {
            return """
                    DashScope 返回了错误 JSON，无法解析为对话结果（常见原因：API Key 无效）。
                    请检查项目根目录 .env 中 DASHSCOPE_API_KEY 是否为阿里云百炼有效密钥，修改后重启应用。
                    控制台：https://bailian.console.aliyun.com/
                    原始错误：%s
                    """.formatted(trim(raw));
        }
        if (lower.contains("invalidapikey") || lower.contains("invalid api-key")) {
            return "DashScope API Key 无效，请更新 .env 中的 DASHSCOPE_API_KEY 后重启。原始错误：" + trim(raw);
        }
        if (lower.contains("total timeout") || lower.contains("read timed out") || lower.contains("resourceaccessexception")) {
            return """
                    调用 DashScope 超时。已支持通过 DASHSCOPE_READ_TIMEOUT 放大超时（默认 300 秒）。
                    请确认已重启最新代码；若仍超时，检查网络访问 https://dashscope.aliyuncs.com 。
                    原始错误：%s
                    """.formatted(trim(raw));
        }
        if (lower.contains("insufficient") || lower.contains("quota") || lower.contains("throttl")) {
            return "DashScope 额度/限流异常：" + trim(raw);
        }
        return trim(raw);
    }

    private static String rootMessage(Throwable error) {
        Throwable cur = error;
        String last = error == null ? "" : String.valueOf(error.getMessage());
        while (cur != null) {
            if (StringUtils.hasText(cur.getMessage())) {
                last = cur.getMessage();
            }
            cur = cur.getCause();
        }
        return last == null ? "" : last;
    }

    private static String trim(String msg) {
        if (msg.length() <= 400) {
            return msg;
        }
        return msg.substring(0, 400) + "...";
    }
}
