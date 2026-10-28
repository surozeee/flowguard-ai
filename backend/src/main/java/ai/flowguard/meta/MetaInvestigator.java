package ai.flowguard.meta;

public interface MetaInvestigator {
    InvestigationPlan propose(String objective, int remainingProbes);
}
