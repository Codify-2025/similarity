package Codify.similarity.mongo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@NoArgsConstructor
@Document(collection = "result")
public class ResultDoc {
    @Id
    private String id;

    private Long submissionId;
    private Long studentId;
    private Object ast;
}
