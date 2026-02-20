package desia.combat;

import desia.status.StatusEngine;

public final class DamageEngine {
    private DamageEngine() {}

    public static double deal(Combatant attacker, Combatant target, double raw, DamageType type, int hitCount) {
        if (target == null) return 0;

        double dmg = Math.max(0, raw);

        // (지금 프로젝트의 기본 감산 철학 유지) 방어/마저 * 0.5 감산, 최소 1
        if (type == DamageType.PHYSICAL) dmg = Math.max(1, dmg - target.getDef() * 0.5);
        else if (type == DamageType.MAGIC) dmg = Math.max(1, dmg - target.getMdef() * 0.5);
        else dmg = Math.max(1, dmg);

        // 이 프로젝트는 스탯/자원을 "정수"로 다룬다.
        long dmgInt = Math.max(1, Math.round(dmg));

        // 실드 흡수
        double hpDmg = target.absorbDamage(dmgInt);

        if (hpDmg > 0) target.setHp(target.getHp() - hpDmg);

        // 피격 처리(수면 즉시 해제, 화상 스택 감소)
        StatusEngine.onHitTaken(target, Math.max(1, hitCount));

        return hpDmg;
    }
}
