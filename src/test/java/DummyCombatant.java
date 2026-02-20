package desia.testutil;

import desia.combat.Combatant;
import desia.status.StatusContainer;

public class DummyCombatant implements Combatant {
    private final String name;

    private int level = 1;
    private double maxHp = 100;
    private double maxMp = 0;

    private double atk = 0;
    private double magic = 0;
    private double def = 0;
    private double mdef = 0;
    private double spd = 0;

    private double hp = 100;
    private double mp = 0;
    private double shield = 0;

    private final StatusContainer statuses = new StatusContainer();

    public DummyCombatant(String name) {
        this.name = name;
    }

    // setters (테스트에서 값 세팅용)
    public DummyCombatant level(int v) { this.level = v; return this; }
    public DummyCombatant maxHp(double v) { this.maxHp = v; return this; }
    public DummyCombatant def(double v) { this.def = v; return this; }
    public DummyCombatant mdef(double v) { this.mdef = v; return this; }
    public DummyCombatant hp(double v) { this.hp = v; return this; }
    public DummyCombatant shield(double v) { this.shield = v; return this; }

    @Override public String getNameForStatus() { return name; }

    @Override public int getLevel() { return level; }
    @Override public double getMaxHp() { return maxHp; }
    @Override public double getMaxMp() { return maxMp; }
    @Override public double getAtk() { return atk; }
    @Override public double getMagic() { return magic; }
    @Override public double getDef() { return def; }
    @Override public double getMdef() { return mdef; }
    @Override public double getSpd() { return spd; }

    @Override public double getHp() { return hp; }
    @Override public double getMp() { return mp; }
    @Override public void setHp(double hp) { this.hp = hp; }
    @Override public void setMp(double mp) { this.mp = mp; }

    @Override public double getShield() { return shield; }
    @Override public void setShield(double shield) { this.shield = shield; }

    @Override public StatusContainer statuses() { return statuses; }
}
