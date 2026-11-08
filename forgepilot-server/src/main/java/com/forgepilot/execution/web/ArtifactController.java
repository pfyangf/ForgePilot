package com.forgepilot.execution.web;

import com.forgepilot.execution.domain.Artifact;
import com.forgepilot.execution.stream.SessionLogArchiveService;
import com.forgepilot.system.security.AuthContext;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1")
public class ArtifactController {

    private final SessionLogArchiveService archiveService;

    public ArtifactController(SessionLogArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    @PostMapping("/tasks/{taskId}/sessions/{sessionId}/archive-logs")
    public Map<String, Object> archive(@PathVariable String taskId, @PathVariable String sessionId) {
        AuthContext.require();
        var result = archiveService.archiveAndIndex(taskId, sessionId, null);
        return Map.of("objectKey", result.objectKey(), "uploaded", result.uploaded());
    }

    @GetMapping("/tasks/{taskId}/sessions/{sessionId}/archived-log")
    public ResponseEntity<byte[]> fetch(@PathVariable String taskId, @PathVariable String sessionId) {
        AuthContext.require();
        Artifact meta = archiveService
                .findLogArtifact(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "尚无归档日志"));
        byte[] body = archiveService
                .fetchArchived(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "对象不存在"));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + sessionId + ".log\"")
                .header("X-ForgePilot-Object-Key", meta.getObjectKey())
                .contentType(MediaType.TEXT_PLAIN)
                .body(body);
    }

    @GetMapping("/tasks/{taskId}/sessions/{sessionId}/archived-log/meta")
    public Map<String, Object> meta(@PathVariable String taskId, @PathVariable String sessionId) {
        AuthContext.require();
        Artifact meta = archiveService
                .findLogArtifact(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "尚无归档日志"));
        return Map.of(
                "objectKey", meta.getObjectKey(),
                "byteSize", meta.getByteSize() == null ? 0 : meta.getByteSize(),
                "checksum", meta.getChecksum() == null ? "" : meta.getChecksum(),
                "kind", meta.getKind());
    }
}
