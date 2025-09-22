package Codify.similarity.service.listener;

import Codify.similarity.service.SseEventPublisher;
import Codify.similarity.web.dto.MessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClientMessageListener {
    private final BlockingQueue<MessageDto> queue = new LinkedBlockingQueue<>();
    private final SseEventPublisher sseEventPublisher;

    @RabbitListener(queues = "client.queue", containerFactory = "rabbitListenerContainerFactory")
    public void handleSimilarityComplete(MessageDto message) {
        log.info("Received similarity complete message from client.queue: {}", message.getGroupId());
        try {
            queue.offer(message);
            log.info("Message added to queue. Queue size: {}", queue.size());

            // SSE 완료 이벤트 발행
            if (message.getGroupId() != null) {
                sseEventPublisher.publishCompleted(message.getGroupId());
                log.info("SSE completed event published for groupId: {}", message.getGroupId());
            } else {
                log.warn("GroupId is null, cannot publish SSE event");
            }
        } catch (Exception e) {
            log.error("Failed to process similarity complete message from client.queue", e);
            throw new AmqpRejectAndDontRequeueException("similarity failed", e);
        }
    }

    public MessageDto pollMessage() {
        return queue.poll();
    }

    public MessageDto takeMessage() throws InterruptedException {
        return queue.take();
    }
}
