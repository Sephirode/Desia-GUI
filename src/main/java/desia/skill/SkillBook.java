package desia.skill;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class SkillBook {
    @JsonProperty("skills")
    private Map<String, SkillDef> skills = new LinkedHashMap<>();
}
