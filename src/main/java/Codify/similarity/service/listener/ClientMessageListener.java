package Codify.similarity.service.listener;

import Codify.similarity.web.dto.MessageDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
@Slf4j
public class ClientMessageListener {
    private final BlockingQueue<MessageDto> queue = new LinkedBlockingQueue<>();

    @RabbitListener(queues = "client.queue", containerFactory = "rabbitListenerContainerFactory")
    public void handleSimilarityComplete(MessageDto message) {
        log.info("Received similarity complete message: {}", message);
        try {
            queue.offer(message);
            log.info("Message added to queue. Queue size: {}", queue.size());
        } catch (Exception e) {
            log.error("Failed to process similarity complete message", e);
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
