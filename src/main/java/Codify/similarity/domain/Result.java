package Codify.similarity.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(
        name = "Result",
        uniqueConstraints = @UniqueConstraint(
        name = "uk_result_assignment_from_to",
        columnNames = {"assignmentId", "submission_from_id", "submission_to_id"}
    )
)
public class Result {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "resultId")
    private Long id;

    @Column(name = "submission_from_id")
    private Long submissionFromId;

    @Column(name = "submission_to_id")
    private Long submissionToId;

    @Column(name = "student_from_id")
    private Long studentFromId;

    @Column(name = "student_to_id")
    private Long studentToId;

    @Column(name = "accumulateResult")
    private double accumulateResult;

    @Column(name = "assignmentId")
    private Long assignmentId;
}