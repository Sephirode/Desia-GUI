package desia.skill;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 스킬 정의 조회 전용 레포지토리.
 *
 * 로딩 책임은 DataLoader로 이동했다.
 */
public class SkillRepository {
    private final Map<String, SkillDef> skills;

    public SkillRepository(Map<String, SkillDef> skills) {
        if (skills == null) {
            this.skills = Collections.emptyMap();
        } else {
            // 원본 보호(외부 맵 변경 차단)
            this.skills = Collections.unmodifiableMap(new LinkedHashMap<>(skills));
        }
    }

    public Map<String, SkillDef> all() {
        return skills;
    }

    public SkillDef get(String name) {
        if (name == null) return null;
        return skills.get(name);
    }
}
