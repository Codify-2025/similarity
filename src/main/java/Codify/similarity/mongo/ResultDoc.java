package Codify.similarity.mongo;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(collection = "result")
public class ResultDoc {
    @Id
    private String id;

    private Integer submissionId;
    private Integer studentId;
    private Object ast;

    private Integer assignmentId;
}
