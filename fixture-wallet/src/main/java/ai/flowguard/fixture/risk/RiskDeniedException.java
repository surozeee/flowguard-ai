package ai.flowguard.fixture.risk;

public class RiskDeniedException extends RuntimeException {

    private final String code;

    public RiskDeniedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
