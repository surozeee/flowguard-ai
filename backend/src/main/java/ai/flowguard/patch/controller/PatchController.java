package ai.flowguard.patch.controller;

import ai.flowguard.core.payload.response.GlobalResponse;
import ai.flowguard.core.web.CustomLedgerRestController;
import ai.flowguard.patch.PatchCommandService;
import ai.flowguard.patch.payload.PatchProposeResult;
import ai.flowguard.patch.payload.PatchVerifyResult;
import ai.flowguard.patch.payload.ProposePatchRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@CustomLedgerRestController(value = "/api/v1/investigations", name = "patchController")
public class PatchController {

    private final PatchCommandService patchCommandService;

    public PatchController(PatchCommandService patchCommandService) {
        this.patchCommandService = patchCommandService;
    }

    @PostMapping("/{id}/patches")
    public ResponseEntity<GlobalResponse<PatchProposeResult>> propose(
            @PathVariable UUID id,
            @Valid @RequestBody ProposePatchRequest request) {
        PatchProposeResult result = patchCommandService.proposePatch(
                id, request.diff(), request.approved(), request.generateFromMeta());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(GlobalResponse.success("Patch recorded", result));
    }

    @PostMapping("/patches/{patchId}/verify")
    public GlobalResponse<PatchVerifyResult> verify(@PathVariable UUID patchId) {
        return GlobalResponse.success(patchCommandService.verifyPatch(patchId));
    }
}
