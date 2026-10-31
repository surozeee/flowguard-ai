package ai.flowguard.patch.payload;

public record ProposePatchRequest(
        String diff,
        boolean approved,
        Boolean generate
) {
    public boolean generateFromMeta() {
        return Boolean.TRUE.equals(generate) || diff == null || diff.isBlank();
    }
}
