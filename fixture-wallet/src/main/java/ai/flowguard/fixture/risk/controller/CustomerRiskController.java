package ai.flowguard.fixture.risk.controller;

import ai.flowguard.fixture.core.web.CustomFixtureRestController;
import ai.flowguard.fixture.risk.CustomerRiskProfile;
import ai.flowguard.fixture.risk.RiskEvaluationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;
import java.util.UUID;

@CustomFixtureRestController("/customers")
public class CustomerRiskController {

    private final RiskEvaluationService riskEvaluationService;

    public CustomerRiskController(RiskEvaluationService riskEvaluationService) {
        this.riskEvaluationService = riskEvaluationService;
    }

    @GetMapping("/{accountId}/risk-profile")
    public CustomerRiskProfile riskProfile(@PathVariable UUID accountId) {
        return riskEvaluationService.loadProfile(accountId);
    }

    @GetMapping("/{accountId}/risk-usage")
    public Map<String, Object> riskUsage(@PathVariable UUID accountId) {
        return riskEvaluationService.usageSnapshot(accountId);
    }
}
