package ai.flowguard.fixture.ops.controller;

import ai.flowguard.fixture.core.web.CustomFixtureRestController;
import ai.flowguard.fixture.race.RaceCheckpoint;
import ai.flowguard.fixture.service.FixtureResetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@CustomFixtureRestController("/internal")
public class InternalOpsController {

    private final FixtureResetService resetService;

    public InternalOpsController(FixtureResetService resetService) {
        this.resetService = resetService;
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        resetService.resetToSeed();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/race/configure")
    public ResponseEntity<Void> configureRace(@RequestBody Map<String, Object> body) {
        String checkpoint = (String) body.get("checkpoint");
        int threads = ((Number) body.getOrDefault("parallelRequests", 2)).intValue();
        RaceCheckpoint.Point point = RaceCheckpoint.Point.valueOf(checkpoint);
        RaceCheckpoint.configure(point, threads);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/race/release")
    public ResponseEntity<Void> releaseRace() {
        RaceCheckpoint.releaseAll();
        return ResponseEntity.noContent().build();
    }
}
