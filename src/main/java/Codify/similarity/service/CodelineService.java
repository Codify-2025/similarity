package Codify.similarity.service;

import Codify.similarity.core.RangeUtil;
import Codify.similarity.core.TreeMatcher;
import Codify.similarity.domain.Codeline;
import Codify.similarity.repository.CodelineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodelineService {
    private final CodelineRepository codelineRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void saveMergedRanges(final Long resultId, final Long leftStudentId, final Long rightStudentId, final List<TreeMatcher.Seg> segs) {
        log.info("=== SAVING MERGED RANGES START ===");
        log.info("ResultId: {}, LeftStudentId: {}, RightStudentId: {}", resultId, leftStudentId, rightStudentId);
        log.info("Input segments count: {}", segs.size());
        
        // 입력 세그먼트들 출력
        for (int i = 0; i < segs.size(); i++) {
            TreeMatcher.Seg seg = segs.get(i);
            log.info("Input segment {}: from[{}-{}] to[{}-{}]", i, seg.fs(), seg.fe(), seg.ts(), seg.te());
        }

        // Interval 리스트
        final var left  = new ArrayList<RangeUtil.Interval>();
        final var right = new ArrayList<RangeUtil.Interval>();

        for (final var s : segs) {
            left.add(new RangeUtil.Interval(s.fs(), s.fe()));
            right.add(new RangeUtil.Interval(s.ts(), s.te()));
            log.info("Added to left: [{}-{}], Added to right: [{}-{}]", s.fs(), s.fe(), s.ts(), s.te());
        }

        log.info("Left intervals count: {}, Right intervals count: {}", left.size(), right.size());

        // 단순 병합
        final var leftM  = RangeUtil.mergeRanges(left);
        final var rightM = RangeUtil.mergeRanges(right);

        log.info("After merging - Left: {}, Right: {}", leftM.size(), rightM.size());
        
        codelineRepository.deleteByResultId(resultId);

        final var batch = new ArrayList<Codeline>();

        // 학생 1 데이터 저장
        for (final var iv : leftM) {
            log.info("Saving LEFT segment to DB: studentId={}, from[{}-{}]", leftStudentId, iv.start(), iv.end());
            batch.add(Codeline.builder()
                    .resultId(resultId)
                    .studentId(leftStudentId)
                    .startLine(iv.start())
                    .endLine(iv.end())
                    .build());
        }

        // 학생 2 데이터 저장
        for (final var iv : rightM) {
            log.info("Saving RIGHT segment to DB: studentId={}, from[{}-{}]", rightStudentId, iv.start(), iv.end());
            batch.add(Codeline.builder()
                    .resultId(resultId)
                    .studentId(rightStudentId)
                    .startLine(iv.start())
                    .endLine(iv.end())
                    .build());
        }

        codelineRepository.saveAll(batch);
        log.info("Saved {} codeline records for result {}", batch.size(), resultId);
        log.info("=== SAVING MERGED RANGES END ===");
    }
}