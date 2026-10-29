package ai.flowguard.fixture.verification;

import ai.flowguard.fixture.race.RaceCheckpoint;
import ai.flowguard.fixture.service.PaymentService;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ConcurrentDebitRaceRunner {

    private ConcurrentDebitRaceRunner() {
    }

    public static void runConcurrentDebitRace(
            PaymentService paymentService,
            UUID sourceAccountId,
            UUID targetAccountId,
            long amountMinor,
            String idempotencyKeyA,
            String idempotencyKeyB) throws InterruptedException {

        RaceCheckpoint.configure(RaceCheckpoint.Point.AFTER_BALANCE_READ, 2);

        CountDownLatch workersReady = new CountDownLatch(2);
        CountDownLatch startGun = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            CompletableFuture<Void> first = CompletableFuture.runAsync(
                    () -> executeTransfer(paymentService, sourceAccountId, targetAccountId, amountMinor,
                            idempotencyKeyA, workersReady, startGun),
                    pool);
            CompletableFuture<Void> second = CompletableFuture.runAsync(
                    () -> executeTransfer(paymentService, sourceAccountId, targetAccountId, amountMinor,
                            idempotencyKeyB, workersReady, startGun),
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

    private static void executeTransfer(
            PaymentService paymentService,
            UUID sourceAccountId,
            UUID targetAccountId,
            long amountMinor,
            String idempotencyKey,
            CountDownLatch workersReady,
            CountDownLatch startGun) {
        workersReady.countDown();
        try {
            if (!startGun.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Start gun timeout");
            }
            paymentService.debitTransfer(sourceAccountId, targetAccountId, amountMinor, idempotencyKey);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Race runner interrupted", e);
        }
    }
}
