package desia.skill;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.*;

/**
 * 직업별 레벨 도달 시 스킬을 습득하게 하는 규칙.
 *
 * - resources/skill_unlocks.json이 존재하면 이를 우선 사용한다.
 * - 없거나 해당 클래스 엔트리가 없으면 기본 규칙을 사용한다:
 *   skillsets.json의 리스트 기준으로
 *   레벨 1: 앞 2개, 레벨 10/20/30/...: 이후 1개씩 해금
 */
public class SkillUnlockRepository {

    public static final class Rule {
        public int level;
        public List<String> skills;
    }

    private final Map<String, List<Rule>> rulesByClass;
    private final SkillSetRepository skillSets;

    public SkillUnlockRepository() {
        this.skillSets = new SkillSetRepository();
        this.rulesByClass = loadRules();
    }

    /** 현재 레벨까지 습득 가능한 스킬 목록(중복 제거, 순서 유지). */
    public LinkedHashSet<String> knownSkillsUpTo(String clazz, int level) {
        String c = (clazz == null) ? "" : clazz;
        int lv = Math.max(1, level);

        LinkedHashSet<String> out = new LinkedHashSet<>();
        List<Rule> rules = rulesByClass.get(c);

        if (rules != null && !rules.isEmpty()) {
            rules.sort(Comparator.comparingInt(r -> r.level));
            for (Rule r : rules) {
                if (r == null) continue;
                if (r.level <= lv && r.skills != null) out.addAll(r.skills);
            }
            return out;
        }

        // fallback: skillsets.json 기반 기본 규칙
        List<String> list = skillSets.rawSets().getOrDefault(c, List.of());
        if (list.isEmpty()) return out;

        // 시작 스킬: 2개
        for (int i = 0; i < list.size() && i < 2; i++) out.add(list.get(i));

        // 10렙마다 1개씩 추가
        // ex) lv 10 => idx 2, lv 20 => idx 3 ...
        int unlockedExtra = lv / 10; // 10~19 =>1, 20~29=>2...
        for (int k = 0; k < unlockedExtra; k++) {
            int idx = 2 + k;
            if (idx >= 0 && idx < list.size()) out.add(list.get(idx));
        }
        return out;
    }

    /** 정확히 특정 레벨에 새로 해금되는 스킬 목록(룰/기본규칙 기준). */
    public List<String> skillsUnlockedAt(String clazz, int level) {
        String c = (clazz == null) ? "" : clazz;
        int lv = Math.max(1, level);

        List<Rule> rules = rulesByClass.get(c);
        if (rules != null && !rules.isEmpty()) {
            List<String> out = new ArrayList<>();
            for (Rule r : rules) {
                if (r != null && r.level == lv && r.skills != null) out.addAll(r.skills);
            }
            return out;
        }

        // fallback
        if (lv % 10 != 0) return List.of();
        List<String> list = skillSets.rawSets().getOrDefault(c, List.of());
        int idx = 2 + (lv / 10 - 1);
        if (idx >= 0 && idx < list.size()) return List.of(list.get(idx));
        return List.of();
    }

    private Map<String, List<Rule>> loadRules() {
        try (InputStream in = getClass().getResourceAsStream("/skill_unlocks.json")) {
            if (in == null) return Collections.emptyMap();
            ObjectMapper om = new ObjectMapper();
            Map<String, List<Rule>> m = om.readValue(in, new TypeReference<>() {});
            if (m == null) return Collections.emptyMap();
            return m;
        } catch (Exception e) {
            System.out.println("skill_unlocks.json 로드 실패: " + e.getMessage());
            return Collections.emptyMap();
        }
    }
}
