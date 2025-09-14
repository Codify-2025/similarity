package Codify.similarity.repository;

import Codify.similarity.domain.Codeline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CodelineRepository extends JpaRepository<Codeline, Long> {
    @Modifying
    @Transactional
    @Query("delete from Codeline c where c.resultId = :resultId")
    void deleteByResultId(@Param("resultId") Long resultId);
}
