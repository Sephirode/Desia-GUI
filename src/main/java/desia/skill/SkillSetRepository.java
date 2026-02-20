package desia.skill;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import java.io.InputStream;
import java.util.*;

public class SkillSetRepository {
    private final ObjectMapper om = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Map<String, List<String>> sets;

    public synchronized Map<String, List<String>> rawSets() {
        if (sets != null) return sets;
        sets = load();
        return sets;
    }

    public List<SkillDef> skillsForClass(String classes, Map<String, SkillDef> skills) {
        if (classes == null || skills == null) return List.of();
        List<String> names = rawSets().getOrDefault(classes, List.of());
        List<SkillDef> out = new ArrayList<>();
        for (String n : names) {
            SkillDef def = skills.get(n);
            if (def != null) out.add(def);
        }
        return out;
    }

    private Map<String, List<String>> load() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("skillsets.json")) {
            if (in == null) return Collections.emptyMap();
            return om.readValue(in, new TypeReference<Map<String, List<String>>>() {});
        } catch (Exception e) {
            System.out.println("[skillsets] 로딩 실패: " + e.getMessage());
            return Collections.emptyMap();
        }
    }
}
