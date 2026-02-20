package desia.skill;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class SkillTerm {
    private String stat;   // self_attack, target_max_hp, constant ...
    private double coef;   // constant의 경우 = 고정값
}
