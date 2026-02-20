package desia.skill;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.util.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class SkillDef {
    private String role;
    private String element;
    private String description;
    private String category;
    private String target;

    @JsonProperty("mp_cost")
    private int mpCost;

    private List<SkillComponent> components = new ArrayList<>();

    @JsonProperty("status_effects")
    private List<SkillStatusEffect> statusEffects = new ArrayList<>();

    private Map<String, Object> special;
}
