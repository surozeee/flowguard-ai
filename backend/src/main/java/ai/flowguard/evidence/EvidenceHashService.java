package ai.flowguard.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@Service
public class EvidenceHashService {

    private final ObjectMapper objectMapper;

    public EvidenceHashService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String sha256(String prefix, String prevHash, Object body) {
        try {
            String canonical = objectMapper.writeValueAsString(body);
            String material = prefix + "|" + prevHash + "|" + canonical;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Hash computation failed", e);
        }
    }

    public Map<String, String> chainForExperiment(String prevHash, Map<String, Object> recordBody) {
        String recordHash = sha256("evidence", prevHash, recordBody);
        String manifestHash = sha256("manifest", recordHash, Map.of(
                "experimentId", recordBody.get("experimentId"),
                "verdict", recordBody.get("verdict")));
        return Map.of(
                "prevHash", prevHash,
                "recordHash", recordHash,
                "manifestHash", manifestHash);
    }
}
