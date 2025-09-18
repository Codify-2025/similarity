package Codify.similarity.service.listener;

import Codify.similarity.service.SimilarityService;
import Codify.similarity.web.dto.MessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SimilarityMessageListener {
    private final SimilarityService similarityService;
    //parsing service에서 push한 message 소비
    // READY, DONE, ERROR
    //ready -> upload service에서 파일 업로드 후 바로 보내야 할듯
    //done -> 유사도 분석 완료 후 done
    @RabbitListener(queues = "similarity.queue", containerFactory =
            "rabbitListenerContainerFactory")
    public void handleParsingComplete(MessageDto message) {
        log.info("Received similarity message: {}", message.getGroupId());
        try {
            similarityService.analyzeAndSaveRefactor(message);
        } catch (Exception e) {
            log.error("Failed to process similarity message", e);
            throw new AmqpRejectAndDontRequeueException("similarity failed", e);
        }
    }
}
