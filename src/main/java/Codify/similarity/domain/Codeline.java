package Codify.similarity.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name="Codeline")
public class Codeline {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long codelineId;
    private Long resultId;
    private Long studentId;
    private Integer startLine;
    private Integer endLine;
}