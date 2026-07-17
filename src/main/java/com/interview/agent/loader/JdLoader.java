package com.interview.agent.loader;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

@Component
public class JdLoader {

    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern SCRIPT = Pattern.compile("(?is)<script.*?>.*?</script>");
    private static final Pattern STYLE = Pattern.compile("(?is)<style.*?>.*?</style>");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public String load(String jdText, String jdUrl) {
        if (jdText != null && !jdText.isBlank()) {
            return jdText.trim();
        }
        if (jdUrl == null || jdUrl.isBlank()) {
            throw new IllegalArgumentException("JD text or URL is required");
        }
        return fetchUrl(jdUrl.trim());
    }

    public String fetchUrl(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "InterviewAgent/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalArgumentException("JD URL fetch failed: HTTP " + response.statusCode());
            }
            String html = response.body();
            html = SCRIPT.matcher(html).replaceAll(" ");
            html = STYLE.matcher(html).replaceAll(" ");
            String text = TAG.matcher(html).replaceAll(" ");
            text = text.replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (text.length() < 40) {
                throw new IllegalArgumentException("JD URL content too short after HTML strip");
            }
            return text.length() > 12000 ? text.substring(0, 12000) : text;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to fetch JD URL: " + e.getMessage(), e);
        }
    }
}
