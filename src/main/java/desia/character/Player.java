package desia.character;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@ToString

public class Player {

    private String classes;
    private String id;
    private String description;

    // 플레이어 기본 스탯
    private double maxHp;
    private double maxMp;
    private double atk;
    private double magic;
    private double spd;
    private double def;
    private double mdef;

    private int level;

    // 플레이어 성장 스탯
    private double growthMaxHp;
    private double growthMaxMp;
    private double growthAtk;
    private double growthMagic;
    private double growthDef;
    private double growthMdef;
    private double growthSpd;
}
