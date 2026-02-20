package desia.combat;

import desia.status.StatusCarrier;

public interface Combatant extends StatusCarrier, ShieldCarrier {
    int getLevel();
    double getMaxHp();
    double getMaxMp();
    double getAtk();
    double getMagic();
    double getDef();
    double getMdef();
    double getSpd();

    double getHp();
    double getMp();
    void setHp(double hp);
    void setMp(double mp);
}
