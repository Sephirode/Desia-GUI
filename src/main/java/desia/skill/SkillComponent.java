package desia.skill;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.util.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SkillComponent {
    private String kind;

    @JsonProperty("damage_type")
    private String damageType;

    private List<SkillTerm> terms = new ArrayList<>();
}
