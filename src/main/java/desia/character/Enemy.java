package desia.character;

import lombok.*;
import lombok.ToString;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@ToString

public class Enemy {

    private String name;
    private String tier;
    private String property;
    private String description;

    private double maxHp;
    private double maxMp;
    private double atk;
    private double magic;
    private double spd;
    private double def;
    private double mdef;
    private int baseLevel;

    private double growthMaxHp;
    private double growthMaxMp;
    private double growthAtk;
    private double growthMagic;
    private double growthDef;
    private double growthMdef;
    private double growthSpd;
}
