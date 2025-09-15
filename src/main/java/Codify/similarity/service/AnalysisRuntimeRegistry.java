package Codify.similarity.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class AnalysisRuntimeRegistry {
    private final ConcurrentMap<String, Instant> startedAt = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> lastProgressAt = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> lastErrorAt    = new ConcurrentHashMap<>();

    private static String key(Integer assignmentId, Integer studentId, Integer submissionId) {
        return assignmentId + ":" + studentId + ":" + submissionId;
    }

    public void markStarted(Integer assignmentId, Integer studentId, Integer submissionId) {
        startedAt.putIfAbsent(key(assignmentId, studentId, submissionId), Instant.now());
    }
    public void markProgress(Integer assignmentId, Integer studentId, Integer submissionId) {
        lastProgressAt.put(key(assignmentId, studentId, submissionId), Instant.now());
    }
    public void markError(Integer assignmentId, Integer studentId, Integer submissionId) {
        lastErrorAt.put(key(assignmentId, studentId, submissionId), Instant.now());
    }

    public Optional<Instant> getStartedAt(Integer assignmentId, Integer studentId, Integer submissionId) {
        return Optional.ofNullable(startedAt.get(key(assignmentId, studentId, submissionId)));
    }
    public Optional<Instant> getLastProgressAt(Integer assignmentId, Integer studentId, Integer submissionId) {
        return Optional.ofNullable(lastProgressAt.get(key(assignmentId, studentId, submissionId)));
    }
    public Optional<Instant> getLastErrorAt(Integer assignmentId, Integer studentId, Integer submissionId) {
        return Optional.ofNullable(lastErrorAt.get(key(assignmentId, studentId, submissionId)));
    }

    public void clear(Integer assignmentId, Integer studentId, Integer submissionId) {
        var k = key(assignmentId, studentId, submissionId);
        startedAt.remove(k);
        lastProgressAt.remove(k);
        lastErrorAt.remove(k);
    }
}
