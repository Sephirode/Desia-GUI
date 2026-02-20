package desia.skill;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SkillStatusEffect {
    private String status;   // poison/burn/bleed/...
    private String target;   // enemy/ally
    private double chance = 1.0;
    private int stacks = 1;
}
