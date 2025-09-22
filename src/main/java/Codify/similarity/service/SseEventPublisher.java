package Codify.similarity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseEventPublisher {

    private final ObjectMapper objectMapper;

    // groupId별로 SSE 연결 관리
    private final Map<String, SseEmitter> connections = new ConcurrentHashMap<>();

    public SseEmitter createConnection(String groupId) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5분 타임아웃

        // 연결 정리 콜백
        emitter.onCompletion(() -> {
            log.info("SSE 연결 완료: groupId={}", groupId);
            connections.remove(groupId);
        });

        emitter.onTimeout(() -> {
            log.info("SSE 연결 타임아웃: groupId={}", groupId);
            connections.remove(groupId);
        });

        emitter.onError((throwable) -> {
            log.error("SSE 연결 에러: groupId={}", groupId, throwable);
            connections.remove(groupId);
        });

        connections.put(groupId, emitter);

        // 연결 확인용 초기 메시지
        sendEvent(groupId, "connected", Map.of("status", "connected", "groupId", groupId));

        return emitter;
    }

    public void publishProgress(String groupId, int processed, int total) {
        Map<String, Object> data = Map.of(
            "type", "progress",
            "processed", processed,
            "total", total,
            "percentage", total > 0 ? (int)((double)processed / total * 100) : 0
        );
        sendEvent(groupId, "progress", data);
    }

    public void publishCompleted(String groupId) {
        Map<String, Object> data = Map.of(
            "type", "completed",
            "status", "done"
        );
        sendEvent(groupId, "completed", data);

        // 완료 이벤트 전송 후 약간의 지연 후 연결 정리
        new Thread(() -> {
            try {
                Thread.sleep(1000); // 1초 대기
                SseEmitter emitter = connections.remove(groupId);
                if (emitter != null) {
                    try {
                        emitter.complete();
                        log.info("SSE 연결 정리 완료: groupId={}", groupId);
                    } catch (Exception e) {
                        log.warn("SSE 연결 종료 중 에러: groupId={}", groupId, e);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    public void publishError(String groupId, String message) {
        Map<String, Object> data = Map.of(
            "type", "error",
            "status", "error",
            "message", message
        );
        sendEvent(groupId, "error", data);
    }

    private void sendEvent(String groupId, String eventName, Object data) {
        SseEmitter emitter = connections.get(groupId);
        if (emitter == null) {
            log.warn("SSE 연결이 없습니다: groupId={}", groupId);
            return;
        }

        try {
            String jsonData = objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event()
                .name(eventName)
                .data(jsonData));
            log.debug("SSE 이벤트 전송 성공: groupId={}, event={}", groupId, eventName);
        } catch (IOException e) {
            log.error("SSE 이벤트 전송 실패: groupId={}, event={}", groupId, eventName, e);
            connections.remove(groupId);
        }
    }
}