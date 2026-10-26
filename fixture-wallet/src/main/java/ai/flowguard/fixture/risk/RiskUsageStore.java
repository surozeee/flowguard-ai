package ai.flowguard.fixture.risk;

import java.util.UUID;

public interface RiskUsageStore {

    long dailyVolumeUsed(UUID accountId);

    void addDailyVolume(UUID accountId, long amountMinor);

    long hourlyTxnCount(UUID accountId);

    void incrementHourlyTxn(UUID accountId);

    /** Clears in-memory counters between fixture resets (no-op for Redis-backed stores). */
    default void clearAll() {
    }
}
