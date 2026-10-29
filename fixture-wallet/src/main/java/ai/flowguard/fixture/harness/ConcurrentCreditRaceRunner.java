package ai.flowguard.fixture.harness;

import ai.flowguard.fixture.race.RaceCheckpoint;
import ai.flowguard.fixture.service.PaymentLedgerService;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ConcurrentCreditRaceRunner {

    private ConcurrentCreditRaceRunner() {
    }

    public static void runDuplicateCreditRace(
            PaymentLedgerService ledgerService,
            UUID targetAccountId,
            String idempotencyKey,
            long amountMinor) throws InterruptedException {

        RaceCheckpoint.configure(RaceCheckpoint.Point.AFTER_IDEMPOTENCY_LOOKUP, 2);

        CountDownLatch workersReady = new CountDownLatch(2);
        CountDownLatch startGun = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            CompletableFuture<Void> first = CompletableFuture.runAsync(
                    () -> executeAfterStartGun(ledgerService, targetAccountId, idempotencyKey, amountMinor, workersReady, startGun),
                    pool);
            CompletableFuture<Void> second = CompletableFuture.runAsync(
                    () -> executeAfterStartGun(ledgerService, targetAccountId, idempotencyKey, amountMinor, workersReady, startGun),
                    pool);

            if (!workersReady.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Workers did not become ready in time");
            }
            startGun.countDown();
            CompletableFuture.allOf(first, second).join();
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(30, TimeUnit.SECONDS);
            RaceCheckpoint.releaseAll();
        }
    }

    private static void executeAfterStartGun(
            PaymentLedgerService ledgerService,
            UUID targetAccountId,
            String idempotencyKey,
            long amountMinor,
            CountDownLatch workersReady,
            CountDownLatch startGun) {
        workersReady.countDown();
        try {
            if (!startGun.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Start gun timeout");
            }
            ledgerService.credit("race-ref", idempotencyKey, targetAccountId, amountMinor);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Race runner interrupted", e);
        }
    }
}
