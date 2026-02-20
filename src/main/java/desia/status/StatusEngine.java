package desia.status;

import desia.combat.ShieldCarrier;

public final class StatusEngine {

    private StatusEngine() {}

    public static final int DOT_DAMAGE_PER_STACK = 1;

    // "피격(히트)" 이벤트: 도트에는 호출하지 말 것
    public static void onHitTaken(StatusCarrier target, int hitCount) {
        if (target == null || hitCount <= 0) return;
        StatusContainer st = target.statuses();

        // 수면: 피격 즉시 해제
        if (st.has(StatusType.SLEEP)) {
            st.clear(StatusType.SLEEP);
        }

        // 화상: 피격 횟수만큼 스택 감소(연타면 연타 횟수만큼)
        if (st.has(StatusType.BURN)) {
            st.reduceStacks(StatusType.BURN, hitCount);
        }
    }

    // 행동 불가(스택형 제어계)
    public static boolean blocksAction(StatusCarrier unit) {
        if (unit == null) return false;
        StatusContainer st = unit.statuses();
        return st.has(StatusType.PARALYSIS)
                || st.has(StatusType.PANIC)
                || st.has(StatusType.FREEZE)
                || st.has(StatusType.SLEEP);
    }

    public static String blockReason(StatusCarrier unit) {
        if (unit == null) return "";
        StatusContainer st = unit.statuses();
        if (st.has(StatusType.SLEEP)) return "수면";
        if (st.has(StatusType.FREEZE)) return "빙결";
        if (st.has(StatusType.PARALYSIS)) return "마비";
        if (st.has(StatusType.PANIC)) return "패닉";
        return "상태이상";
    }

    // 엔드 페이즈: 도트 적용 + 스택 감소(규칙 고정)
    public static int applyEndPhase(StatusCarrier unit) {
        if (unit == null) return 0;
        StatusContainer st = unit.statuses();

        int bleed = st.getStacks(StatusType.BLEED);
        int poison = st.getStacks(StatusType.POISON);
        int burn = st.getStacks(StatusType.BURN);

        int dot = (bleed + poison + burn) * DOT_DAMAGE_PER_STACK;
        if (dot > 0) {
            // 도트는 경감 없이 고정 대미지(true)지만, 실드는 예외로 먼저 깎인다.
            if (unit instanceof ShieldCarrier sc) {
                double hpDmg = sc.absorbDamage(dot);
                if (hpDmg > 0) unit.setHp(unit.getHp() - hpDmg);
            } else {
                unit.setHp(unit.getHp() - dot);
            }
        }

        // 독: 엔드 페이즈마다 1스택 감소
        if (poison > 0) st.reduceStacks(StatusType.POISON, 1);

        // 제어계: 엔드 페이즈마다 1스택 감소
        reduceIfAny(st, StatusType.PARALYSIS, 1);
        reduceIfAny(st, StatusType.PANIC, 1);
        reduceIfAny(st, StatusType.FREEZE, 1);
        reduceIfAny(st, StatusType.SLEEP, 1);

        // 화상: 피격으로만 감소(엔드 페이즈 감소 없음)
        // 출혈: 전투 종료까지 유지(감소 없음)

        return dot;
    }

    private static void reduceIfAny(StatusContainer st, StatusType type, int n) {
        if (st.has(type)) st.reduceStacks(type, n);
    }
}
