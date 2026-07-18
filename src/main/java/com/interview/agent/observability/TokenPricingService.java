package com.interview.agent.observability;

import com.interview.agent.config.AppConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@RequiredArgsConstructor
public class TokenPricingService {

    private final AppConfig appConfig;

    public record Cost(BigDecimal amount, String currency) {}

    public Cost estimate(String model, Integer promptTokens, Integer completionTokens) {
        AppConfig.ObservabilityProperties obs = appConfig.getObservability();
        String currency = obs.getCostCurrency() == null ? "CNY" : obs.getCostCurrency();
        if (promptTokens == null && completionTokens == null) {
            return new Cost(null, currency);
        }
        AppConfig.ModelPricing pricing = resolvePricing(model, obs);
        BigDecimal input = BigDecimal.valueOf(promptTokens == null ? 0 : promptTokens)
                .divide(BigDecimal.valueOf(1000), 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(pricing.getInputPer1k()));
        BigDecimal output = BigDecimal.valueOf(completionTokens == null ? 0 : completionTokens)
                .divide(BigDecimal.valueOf(1000), 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(pricing.getOutputPer1k()));
        return new Cost(input.add(output).setScale(8, RoundingMode.HALF_UP), currency);
    }

    private AppConfig.ModelPricing resolvePricing(String model, AppConfig.ObservabilityProperties obs) {
        if (model != null && obs.getPricing() != null && obs.getPricing().containsKey(model)) {
            return obs.getPricing().get(model);
        }
        AppConfig.ModelPricing fallback = new AppConfig.ModelPricing();
        fallback.setInputPer1k(0.0008);
        fallback.setOutputPer1k(0.002);
        if (obs.getPricing() != null && obs.getPricing().containsKey("default")) {
            return obs.getPricing().get("default");
        }
        return fallback;
    }
}
