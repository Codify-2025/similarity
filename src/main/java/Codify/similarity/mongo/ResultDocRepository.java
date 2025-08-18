package Codify.similarity.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ResultDocRepository extends MongoRepository<ResultDoc, String> {
    // 해당 학생의 가장 최근 제출 1건 (submissionId 내림차순), 상의 필요
    Optional<ResultDoc> findTopByStudentIdOrderBySubmissionIdDesc(Long studentId);
}
