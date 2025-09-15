package Codify.similarity.core;

import Codify.similarity.model.TreeNode;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public final class TreeMatcher {
    public record Match(TreeNode a, TreeNode b) {}
    public record Seg(int fs, int fe, int ts, int te) {}

    enum Op { DEL, INS, REN }
    static class Cell {
        int cost; Op op; int iPrev, jPrev; List<Match> matches;
        Cell(int cost, Op op, int iPrev, int jPrev, List<Match> matches) {
            this.cost = cost; this.op = op; this.iPrev=iPrev; this.jPrev=jPrev; this.matches = matches;
        }
    }

    // 구조적으로 유사한 노드 타입들 (이름이 달라도 매칭 가능)
    private static final Set<String> STRUCTURAL_TYPES = Set.of(
            "MethodDeclaration",  // 함수 선언
            "ForStmt",           // for 문
            "WhileStmt",         // while 문
            "IfStmt",            // if 문
            "SwitchStmt",        // switch 문
            "BlockStmt",         // 블록
            "ReturnStmt",        // return 문
            "VariableDeclaration" // 변수 선언
    );

    // 이름이 달라도 타입이 같으면 매칭 가능한 노드들
    private static final Set<String> NAME_INDEPENDENT_TYPES = Set.of(
            "FunctionName",      // 함수명
            "VariableName",      // 변수명
            "Parameter"          // 파라미터
    );

    public static List<Match> match(TreeNode A, TreeNode B) {
        // span 보장
        A.computeSpan();
        B.computeSpan();
        // return matchNode(A, B, 0);

        // 함수 매칭 수집
        // 1. 기존 DP 최적 경로 매칭
        List<Match> optimalMatches = matchNode(A, B, 0);

        // 2. MethodDeclaration 모든 매칭 추가 수집
        List<Match> methodMatches = collectAllMethodMatches(A, B);

        // 3. Loop 모든 매칭 추가 수집
        List<Match> loopMatches = collectAllLoopMatches(A, B);

        // 4. Condition 모든 매칭 추가 수집
        List<Match> conditionMatches = collectAllConditionMatches(A, B);

        // 5. Variable 모든 매칭 추가 수집
        List<Match> variableMatches = collectAllVariableMatches(A, B);

        // 6. 합치기 (중복 제거)
        /*List<Match> result = mergeMatches(optimalMatches, methodMatches);
        result = mergeMatches(result, loopMatches);
        result = mergeMatches(result, conditionMatches);
        return mergeMatches(result, variableMatches);*/
        return mergeAllMatches(optimalMatches, methodMatches, loopMatches, conditionMatches, variableMatches);
    }

    private static List<Match> matchNode(TreeNode a, TreeNode b, int depth) {
        // 구조적 유사성 기반 rename 비용 계산
        int rc = calculateStructuralCost(a, b);

        // 1. DP 테이블 구축
        Cell[][] dp = buildDpTable(a, b, depth);

        // 2. 역추적으로 매칭 수집
        List<Match> childMatches = backtrackMatches(dp, a, b);

        // 3. 현재 노드 매칭 여부 판단
        if (shouldMatchNodes(a, b, rc, depth)) {
            childMatches.add(new Match(a, b));

            // 매칭 누락 오류 해결: MethodDeclaration 매칭 추적
            if ("MethodDeclaration".equals(a.label)) {
                log.warn("*** METHOD MATCH CREATED: [{}-{}] <-> [{}-{}], rc: {} ***",
                        a.minLine, a.maxLine, b.minLine, b.maxLine, rc);
            }
            logMatchResult(a, b, true);
        } else {

            // 매칭 누락 오류 해결: MethodDeclaration 매칭 실패 추적
            if ("MethodDeclaration".equals(a.label)) {
                log.warn("*** METHOD MATCH FAILED: [{}-{}] <-> [{}-{}], rc: {}, reason: shouldMatch=false ***",
                        a.minLine, a.maxLine, b.minLine, b.maxLine, rc);
            }
            logMatchResult(a, b, false);
        }
        return childMatches;
    }

    // DP 테이블 구축
    private static Cell[][] buildDpTable(TreeNode a, TreeNode b, int depth) {
        var m = a.children.size();
        var n = b.children.size();
        Cell[][] dp = new Cell[m+1][n+1];

        // 초기화
        dp[0][0] = new Cell(0, null, -1, -1, new ArrayList<>());

        for (int i=1; i<=m; i++) {
            dp[i][0] = new Cell(dp[i-1][0].cost + EditCost.deleteCost(a.children.get(i-1)),
                    Op.DEL, i-1, 0, List.of());
        }
        for (int j=1; j<=n; j++) {
            dp[0][j] = new Cell(dp[0][j-1].cost + EditCost.insertCost(b.children.get(j-1)),
                    Op.INS, 0, j-1, List.of());
        }

        // DP 테이블 채우기
        for (int i=1; i<=m; i++) {
            for (int j=1; j<=n; j++) {
                fillDpCell(dp, i, j, a, b, depth);
            }
        }

        return dp;
    }

    // DP 셀 채우기
    private static void fillDpCell(Cell[][] dp, int i, int j, TreeNode a, TreeNode b, int depth) {
        int del = dp[i-1][j].cost + EditCost.deleteCost(a.children.get(i-1));
        int ins = dp[i][j-1].cost + EditCost.insertCost(b.children.get(j-1));

        // 재귀적으로 자식 매칭
        List<Match> sub = matchNode(a.children.get(i-1), b.children.get(j-1), depth+1);
        int renCost = calculateTreeEditCost(a.children.get(i-1), b.children.get(j-1));

        // 최소 비용 선택
        if (renCost + dp[i-1][j-1].cost <= del && renCost + dp[i-1][j-1].cost <= ins) {
            dp[i][j] = new Cell(renCost + dp[i-1][j-1].cost, Op.REN, i-1, j-1, sub);
        } else if (del <= ins) {
            dp[i][j] = new Cell(del, Op.DEL, i-1, j, List.of());
        } else {
            dp[i][j] = new Cell(ins, Op.INS, i, j-1, List.of());
        }
    }

    // 역추적
    private static List<Match> backtrackMatches(Cell[][] dp, TreeNode a, TreeNode b) {
        List<Match> matches = new ArrayList<>();
        int m = a.children.size();
        int n = b.children.size();
        int i = m, j = n;

        while (i > 0 || j > 0) {
            Cell c = dp[i][j];
            if (c.op == Op.REN) {
                matches.addAll(c.matches);
                i = c.iPrev;
                j = c.jPrev;
            } else if (c.op == Op.DEL) {
                i = c.iPrev;
                j = c.jPrev;
            } else { // INS
                i = c.iPrev;
                j = c.jPrev;
            }
        }

        return matches;
    }

    // 매칭 조건 판단
    private static boolean shouldMatchNodes(TreeNode a, TreeNode b, int rc, int depth) {
        boolean isRoot = "CompilationUnit".equals(a.label) && "CompilationUnit".equals(b.label);

        if (isRoot || a.minLine < 1 || b.minLine < 1) {
            return false;
        }

        // 1. 라벨이 완전히 같은 경우
        if (rc == 0) {
            return true;
        }

        // 2. 구조적으로 유사한 타입인 경우
        if (STRUCTURAL_TYPES.contains(a.label) && a.label.equals(b.label)) {
            // 함수 매칭 오류 해결
            if ("MethodDeclaration".equals(a.label)) {
                return isMethodContentSimilar(a, b);
            }
            return true;
        }

        // 3. 이름 독립적인 타입인 경우
        if (NAME_INDEPENDENT_TYPES.contains(a.label) && a.label.equals(b.label)) {
            return true;
        }

        return false;
    }

    private static boolean isMethodContentSimilar(TreeNode a, TreeNode b) {
        // 1. 파라미터 개수 비교
        int paramCountA = getParameterCount(a);
        int paramCountB = getParameterCount(b);
        if (paramCountA != paramCountB) {
            log.debug("Method parameter count differs: {} vs {}", paramCountA, paramCountB);
            return false;
        }

        // 2. 리턴 타입 비교
        String returnTypeA = getReturnType(a);
        String returnTypeB = getReturnType(b);
        if (!returnTypeA.equals(returnTypeB)) {
            log.debug("Method return type differs: {} vs {}", returnTypeA, returnTypeB);
            return false;
        }

        // 3. 함수 바디의 연산자 패턴 비교
        List<String> operatorsA = extractOperators(a);
        List<String> operatorsB = extractOperators(b);

        // 연산자가 완전히 같아야 함
        if (!operatorsA.equals(operatorsB)) {
            log.debug("Method operators differ: {} vs {}", operatorsA, operatorsB);
            return false;
        }

        log.debug("Method content is similar: [{}-{}] <-> [{}-{}]",
                a.minLine, a.maxLine, b.minLine, b.maxLine);
        return true;
    }

    private static int getParameterCount(TreeNode methodNode) {
        // ParameterList 찾기
        for (TreeNode child : methodNode.children) {
            if ("ParameterList".equals(child.label)) {
                return child.children.size();
            }
        }
        return 0;
    }

    private static String getReturnType(TreeNode methodNode) {
        // 첫 번째 Type 노드가 리턴 타입
        for (TreeNode child : methodNode.children) {
            if ("Type".equals(child.label)) {
                return child.toString(); // 또는 적절한 타입 추출 로직
            }
        }
        return "void";
    }

    private static List<String> extractOperators(TreeNode node) {
        List<String> operators = new ArrayList<>();
        extractOperatorsRecursive(node, operators);
        return operators;
    }

    private static void extractOperatorsRecursive(TreeNode node, List<String> operators) {
        if ("Operator".equals(node.label)) {
            // 연산자 텍스트 추출 (node.value 또는 적절한 방법으로)
            operators.add(node.toString()); // 실제 연산자 값으로 변경 필요
        }

        for (TreeNode child : node.children) {
            extractOperatorsRecursive(child, operators);
        }
    }

    // 로깅
    private static void logMatchResult(TreeNode a, TreeNode b, boolean matched) {
        if ("MethodDeclaration".equals(a.label)) {
            if (matched) {
                log.info("MethodDeclaration MATCHED: [{}-{}] <-> [{}-{}]",
                        a.minLine, a.maxLine, b.minLine, b.maxLine);
            } else {
                log.warn("MethodDeclaration NOT MATCHED: [{}-{}] <-> [{}-{}]",
                        a.minLine, a.maxLine, b.minLine, b.maxLine);
            }
        }
    }

    // 구조적 유사성 기반 비용 계산
    private static int calculateStructuralCost(TreeNode a, TreeNode b) {
        // 타입이 같으면 0
        if (a.label.equals(b.label)) {
            // MethodDeclaration의 경우 특별 처리
            if ("MethodDeclaration".equals(a.label)) {
                // 함수의 경우 내용이 같은지 더 관대하게 판단
                if (isSameMethodContent(a, b)) {
                    return 0;  // 함수 구조가 같음
                } else {
                    return 1;  // 함수 구조가 다름
                }
            }

            // 구조만 유사한 경우를 확인하기 위해 내용 검사
            if (STRUCTURAL_TYPES.contains(a.label)) {
                // 내용이 정말 같은지 체크
                if (isSameContent(a, b)) {
                    return 0;  // 내용도 같음
                } else {
                    return 1;  // 구조만 같음
                }
            }
            return 0;
        }

        // 구조적 타입이면 낮은 비용
        if (STRUCTURAL_TYPES.contains(a.label) && STRUCTURAL_TYPES.contains(b.label)) {
            return 1;  // 다른 구조 타입 간에도 낮은 비용
        }

        // 이름 독립적 타입이면 0
        if (NAME_INDEPENDENT_TYPES.contains(a.label) && a.label.equals(b.label)) {
            return 0;
        }

        // 그 외는 기본 비용
        return EditCost.renameCost(a, b);
    }

    private static boolean isSameContent(TreeNode a, TreeNode b) {
        // 자식 노드 수 비교
        if (a.children.size() != b.children.size()) {
            return false;
        }

        // 각 자식 노드의 라벨 비교 (순서 고려)
        for (int i = 0; i < a.children.size(); i++) {
            TreeNode childA = a.children.get(i);
            TreeNode childB = b.children.get(i);

            // 이름 독립적인 타입은 스킵
            if (NAME_INDEPENDENT_TYPES.contains(childA.label) && NAME_INDEPENDENT_TYPES.contains(childB.label)) {
                continue;
            }

            // 라벨이 다르면 내용이 다름
            if (!childA.label.equals(childB.label)) {
                return false;
            }

            // 재귀적으로 비교
            if (!isSameContent(childA, childB)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSameMethodContent(TreeNode a, TreeNode b) {
        // 자식 노드 수 비교
        if (a.children.size() != b.children.size()) {
            return false;
        }

        // 함수의 경우 리턴타입, 파라미터타입, 함수body만 비교
        for (int i = 0; i < a.children.size(); i++) {
            TreeNode childA = a.children.get(i);
            TreeNode childB = b.children.get(i);

            // 함수명 스킵
            if ("FunctionName".equals(childA.label) && "FunctionName".equals(childB.label)) {
                continue;
            }

            // ParameterList 처리
            if ("ParameterList".equals(childA.label) && "ParameterList".equals(childB.label)) {
                if (!isSameParameterList(childA, childB)) {
                    return false;
                }
                continue;
            }

            // 나머지는 정확히 같아야 함
            if (!childA.label.equals(childB.label)) {
                return false;
            }

            // 재귀적으로 비교
            if (!isSameContent(childA, childB)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isSameParameterList(TreeNode a, TreeNode b) {
        if (a.children.size() != b.children.size()) {
            return false;
        }

        for (int i = 0; i < a.children.size(); i++) {
            TreeNode paramA = a.children.get(i);
            TreeNode paramB = b.children.get(i);

            // Parameter 타입 확인
            if (!"Parameter".equals(paramA.label) || !"Parameter".equals(paramB.label)) {
                return false;
            }

            // Parameter 내부 비교
            if (!isSameParameter(paramA, paramB)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSameParameter(TreeNode a, TreeNode b) {
        if (a.children.size() != b.children.size()) {
            return false;
        }

        for (int i = 0; i < a.children.size(); i++) {
            TreeNode childA = a.children.get(i);
            TreeNode childB = b.children.get(i);

            // 변수명은 스킵
            if ("VariableName".equals(childA.label) && "VariableName".equals(childB.label)) {
                continue;
            }

            // 타입은 같아야 함
            if (!childA.label.equals(childB.label)) {
                return false;
            }

            if (!isSameContent(childA, childB)) {
                return false;
            }
        }
        return true;
    }

    // Tree Edit Cost 계산
    private static int calculateTreeEditCost(TreeNode a, TreeNode b) {
        // 구조적으로 유사한 타입들은 더 낮은 비용으로 계산
        if (STRUCTURAL_TYPES.contains(a.label) && a.label.equals(b.label)) {
            // 자식 수가 비슷하면 더 낮은 비용
            int childDiff = Math.abs(a.children.size() - b.children.size());
            return childDiff;  // 자식 수 차이만큼의 비용
        }

        // 기본 Tree Edit Distance 사용
        return TreeEditDistance.compute(a, b);
    }

    public static List<Seg> toSegments(List<Match> matches, int minLen) {
        List<Seg> segs = new ArrayList<>();
        log.debug("Converting {} matches to segments (minLen={})", matches.size(), minLen);

        // 매칭 누락 오류 해결: MethodDeclaration 매칭들 확인
        long methodMatches = matches.stream()
                .filter(m -> "MethodDeclaration".equals(m.a.label))
                .count();

        for (Match m : matches) {
            int a1 = m.a.minLine, a2 = m.a.maxLine;
            int b1 = m.b.minLine, b2 = m.b.maxLine;

            if (a1 >= 1 && b1 >= 1) {
                int len = Math.min(a2 - a1 + 1, b2 - b1 + 1);
                
                // 중요한 노드 타입은 길이와 관계없이 저장
                boolean isImportantNode = "MethodDeclaration".equals(m.a.label) || 
                                        "FunctionDeclaration".equals(m.a.label) ||
                                        "ClassDeclaration".equals(m.a.label);

                if (len >= minLen || isImportantNode) {
                    segs.add(new Seg(a1, a2, b1, b2));
                    log.debug("Added segment: from[{}-{}] to[{}-{}] (len={})", a1, a2, b1, b2, len);

                } else {
                    log.warn("Skipped segment (len={} < minLen={}): {} [{}-{}] <-> {} [{}-{}]", 
                        len, minLen, m.a.label, a1, a2, m.b.label, b1, b2);
                }
            }
        }
        
        log.debug("Total segments created before overlap resolution: {}", segs.size());
        
        // 단순한 중복 해결 로직
        List<Seg> resolvedSegs = resolveOverlappingSegmentsSimple(segs);
        log.debug("Total segments after resolving overlaps: {}", resolvedSegs.size());
        return resolvedSegs;
    }

    private static List<Seg> resolveOverlappingSegmentsSimple(List<Seg> segments) {
        log.debug("=== RESOLVING OVERLAPPING SEGMENTS START ===");
        log.debug("Input segments count: {}", segments.size());

        // 시작 라인 순으로 정렬
        List<Seg> sorted = segments.stream()
            .sorted(Comparator.comparingInt(Seg::fs))
            .collect(Collectors.toList());

        List<Seg> result = new ArrayList<>();
        
        for (Seg current : sorted) {
            boolean shouldAdd = true;
            
            // 이미 result에 있는 세그먼트들과 비교
            List<Seg> toRemove = new ArrayList<>();
            for (Seg existing : result) {
                if (isCompletelyContained(current, existing)) {
                    // current가 existing에 완전히 포함되면 추가하지 않음
                    log.info("Skipping segment [{}-{}] - contained in [{}-{}]",
                        current.fs(), current.fe(), existing.fs(), existing.fe());
                    shouldAdd = false;
                    break;
                } else if (isCompletelyContained(existing, current)) {
                    // existing이 current에 완전히 포함되면 existing 제거 예정
                    toRemove.add(existing);
                    log.info("Removing segment [{}-{}] - contained in [{}-{}]",
                        existing.fs(), existing.fe(), current.fs(), current.fe());
                }
            }
            
            // 포함된 세그먼트들 제거
            result.removeAll(toRemove);
            
            if (shouldAdd) {
                result.add(current);
                log.debug("*** SEGMENT KEPT: [{}-{}] <-> [{}-{}] ***",
                        current.fs(), current.fe(), current.ts(), current.te());
            }
        }

        log.debug("Resolved to {} total segments", result.size());
        return result;
    }

    // 세그먼트 포함 관계 확인
    private static boolean isCompletelyContained(Seg inner, Seg outer) {
        return inner.fs >= outer.fs && inner.fe <= outer.fe &&
               !(inner.fs == outer.fs && inner.fe == outer.fe);
    }

    // 여기서부터 함수 매칭 추가를 위한 메서드 추가
    private static List<TreeNode> findAllMethods(TreeNode node) {
        List<TreeNode> methods = new ArrayList<>();

        if ("MethodDeclaration".equals(node.label)) {
            methods.add(node);
        }

        for (TreeNode child : node.children) {
            methods.addAll(findAllMethods(child));
        }

        return methods;
    }

    private static List<Match> collectAllMethodMatches(TreeNode a, TreeNode b) {
        List<Match> methodMatches = new ArrayList<>();
        List<TreeNode> methodsA = findAllMethods(a);
        List<TreeNode> methodsB = findAllMethods(b);

        for (TreeNode methodA : methodsA) {
            for (TreeNode methodB : methodsB) {
                if (shouldMatchNodes(methodA, methodB, calculateStructuralCost(methodA, methodB), 0)) {
                    methodMatches.add(new Match(methodA, methodB));
                    log.warn("*** COLLECTED METHOD MATCH: [{}-{}] <-> [{}-{}] ***",
                            methodA.minLine, methodA.maxLine, methodB.minLine, methodB.maxLine);
                }
            }
        }

        return methodMatches;
    }

    // 반복문 매칭
    private static List<TreeNode> findAllLoops(TreeNode node) {
        List<TreeNode> loops = new ArrayList<>();

        if ("ForStmt".equals(node.label) || "WhileStmt".equals(node.label)) {
            loops.add(node);
        }

        for (TreeNode child : node.children) {
            loops.addAll(findAllLoops(child));
        }

        return loops;
    }

    private static List<Match> collectAllLoopMatches(TreeNode a, TreeNode b) {
        List<Match> loopMatches = new ArrayList<>();
        List<TreeNode> loopsA = findAllLoops(a);
        List<TreeNode> loopsB = findAllLoops(b);

        for (TreeNode loopA : loopsA) {
            for (TreeNode loopB : loopsB) {
                if (shouldMatchNodes(loopA, loopB, calculateStructuralCost(loopA, loopB), 0)) {
                    loopMatches.add(new Match(loopA, loopB));
                    log.warn("*** COLLECTED LOOP MATCH: [{}-{}] <-> [{}-{}] ***",
                            loopA.minLine, loopA.maxLine, loopB.minLine, loopB.maxLine);
                }
            }
        }

        return loopMatches;
    }

    private static List<TreeNode> findAllConditions(TreeNode node) {
        List<TreeNode> conditions = new ArrayList<>();

        if ("IfStmt".equals(node.label)) {
            conditions.add(node);
        }

        for (TreeNode child : node.children) {
            conditions.addAll(findAllConditions(child));
        }

        return conditions;
    }

    private static List<Match> collectAllConditionMatches(TreeNode a, TreeNode b) {
        List<Match> conditionMatches = new ArrayList<>();
        List<TreeNode> conditionsA = findAllConditions(a);
        List<TreeNode> conditionsB = findAllConditions(b);

        for (TreeNode conditionA : conditionsA) {
            for (TreeNode conditionB : conditionsB) {
                if (shouldMatchNodes(conditionA, conditionB, calculateStructuralCost(conditionA, conditionB), 0)) {
                    conditionMatches.add(new Match(conditionA, conditionB));
                    log.warn("*** COLLECTED CONDITION MATCH: [{}-{}] <-> [{}-{}] ***",
                            conditionA.minLine, conditionA.maxLine, conditionB.minLine, conditionB.maxLine);
                }
            }
        }

        return conditionMatches;
    }

    private static List<TreeNode> findAllVariableDeclarations(TreeNode node) {
        List<TreeNode> variables = new ArrayList<>();

        if ("VariableDeclaration".equals(node.label)) {
            variables.add(node);
        }

        for (TreeNode child : node.children) {
            variables.addAll(findAllVariableDeclarations(child));
        }

        return variables;
    }

    private static List<Match> collectAllVariableMatches(TreeNode a, TreeNode b) {
        List<Match> variableMatches = new ArrayList<>();
        List<TreeNode> variablesA = findAllVariableDeclarations(a);
        List<TreeNode> variablesB = findAllVariableDeclarations(b);

        for (TreeNode variableA : variablesA) {
            for (TreeNode variableB : variablesB) {
                if (shouldMatchNodes(variableA, variableB, calculateStructuralCost(variableA, variableB), 0)) {
                    variableMatches.add(new Match(variableA, variableB));
                    log.warn("*** COLLECTED VARIABLE MATCH: [{}-{}] <-> [{}-{}] ***",
                            variableA.minLine, variableA.maxLine, variableB.minLine, variableB.maxLine);
                }
            }
        }

        return variableMatches;
    }

    private static List<Match> mergeMatches(List<Match> optimal, List<Match> additional) {
        List<Match> result = new ArrayList<>(optimal);

        for (Match additionalMatch : additional) {
            boolean isDuplicate = optimal.stream().anyMatch(optimalMatch ->
                    optimalMatch.a.minLine == additionalMatch.a.minLine &&
                            optimalMatch.a.maxLine == additionalMatch.a.maxLine &&
                            optimalMatch.b.minLine == additionalMatch.b.minLine &&
                            optimalMatch.b.maxLine == additionalMatch.b.maxLine
            );

            if (!isDuplicate) {
                result.add(additionalMatch);
                log.warn("*** ADDED EXTRA METHOD MATCH: [{}-{}] <-> [{}-{}] ***",
                        additionalMatch.a.minLine, additionalMatch.a.maxLine,
                        additionalMatch.b.minLine, additionalMatch.b.maxLine);
            }
        }

        return result;
    }

    private static List<Match> mergeAllMatches(List<Match>... matchLists) {
        List<Match> result = new ArrayList<>();

        for (List<Match> matches : matchLists) {
            result = mergeMatches(result, matches);
        }

        return result;
    }

}