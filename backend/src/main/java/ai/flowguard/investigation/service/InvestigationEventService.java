package ai.flowguard.investigation.service;

import ai.flowguard.persistence.entity.InvestigationEventEntity;
import ai.flowguard.persistence.jpa.InvestigationEventJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class InvestigationEventService {

    private final InvestigationEventJpaRepository events;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Map<UUID, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Object> sequenceLocks = new ConcurrentHashMap<>();

    public InvestigationEventService(
            InvestigationEventJpaRepository events, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.events = events;
        this.jdbc = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public SseEmitter subscribe(UUID investigationId) {
        SseEmitter emitter = new SseEmitter(0L);
        subscribers.computeIfAbsent(investigationId, id -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(investigationId, emitter));
        emitter.onTimeout(() -> remove(investigationId, emitter));
        for (Map<String, Object> row : listEvents(investigationId)) {
            try {
                String eventType = String.valueOf(row.get("eventType"));
                Map<String, Object> envelope = new LinkedHashMap<>();
                envelope.put("id", String.valueOf(row.get("id")));
                envelope.put("investigationId", investigationId.toString());
                envelope.put("sequence", row.get("sequence"));
                envelope.put("eventType", eventType);
                envelope.put("timestamp", String.valueOf(row.get("createdAt")));
                envelope.put("payload", row.getOrDefault("payload", Map.of()));
                emitter.send(SseEmitter.event().name(eventType).data(envelope));
            } catch (IOException e) {
                remove(investigationId, emitter);
                break;
            }
        }
        return emitter;
    }

    public void publish(UUID investigationId, String eventType, Map<String, Object> payload) {
        Object lock = sequenceLocks.computeIfAbsent(investigationId, ignored -> new Object());
        SavedEvent saved;
        synchronized (lock) {
            saved = appendEvent(investigationId, eventType, payload);
        }

        Map<String, Object> envelope = Map.of(
                "id", saved.eventId().toString(),
                "investigationId", investigationId.toString(),
                "sequence", saved.sequence(),
                "eventType", eventType,
                "timestamp", Instant.now().toString(),
                "payload", payload);

        for (SseEmitter emitter : subscribers.getOrDefault(investigationId, List.of())) {
            try {
                emitter.send(SseEmitter.event().name(eventType).data(envelope));
            } catch (IOException e) {
                remove(investigationId, emitter);
            }
        }
    }

    public List<Map<String, Object>> listEvents(UUID investigationId) {
        return events.findByInvestigationIdOrderBySequenceNoAsc(investigationId).stream()
                .map(this::toMap)
                .toList();
    }

    private SavedEvent appendEvent(UUID investigationId, String eventType, Map<String, Object> payload) {
        long sequence = events.maxSequenceNo(investigationId) + 1;
        UUID eventId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO metadata.investigation_events (id, investigation_id, sequence_no, event_type, payload_json, created_at)
                VALUES (?, ?, ?, ?, ?::jsonb, now())
                """,
                eventId,
                investigationId,
                sequence,
                eventType,
                writeJson(payload));
        return new SavedEvent(eventId, sequence);
    }

    private Map<String, Object> toMap(InvestigationEventEntity entity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", entity.getId());
        row.put("sequence", entity.getSequenceNo());
        row.put("eventType", entity.getEventType());
        row.put("payload", entity.getPayloadJson());
        row.put("createdAt", entity.getCreatedAt());
        return row;
    }

    private void remove(UUID investigationId, SseEmitter emitter) {
        List<SseEmitter> list = subscribers.get(investigationId);
        if (list != null) {
            list.remove(emitter);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private record SavedEvent(UUID eventId, long sequence) {
    }
}
