package Codify.similarity.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "Result")
public class Result {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "resultId")
    private Long id;

    @Column(name = "submission_from_id")
    private Long submissionFromId;

    @Column(name = "submission_to_id")
    private Long submissionToId;

    @Column(name = "submissionId")
    private Long submissionId;

    @Column(name = "accumulateResult")
    private double accumulateResult;
}