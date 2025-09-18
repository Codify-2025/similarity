package Codify.similarity.repository;

import Codify.similarity.domain.Codeline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface CodelineRepository extends JpaRepository<Codeline, Long> {
    @Modifying
    @Query("delete from Codeline c where c.resultId = :resultId")
    void deleteByResultId(@Param("resultId") Long resultId);

    @Modifying
    @Query("DELETE FROM Codeline c WHERE c.resultId IN:resultIds")
    void deleteByResultIdIn(Collection<Long> resultIds);
}
