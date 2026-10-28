package ai.flowguard.investigation.controller;

import ai.flowguard.investigation.service.InvestigationEventService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
public class InvestigationSseController {

    private final InvestigationEventService investigationEventService;

    public InvestigationSseController(InvestigationEventService investigationEventService) {
        this.investigationEventService = investigationEventService;
    }

    @GetMapping(value = "/api/v1/investigations/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID id) {
        return investigationEventService.subscribe(id);
    }
}
