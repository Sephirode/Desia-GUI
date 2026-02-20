package desia.status;

import desia.testutil.DummyCombatant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatusEngineTest {

    @Test
    void endPhase_dotDamage_isStacksSum_times1_andPoisonReducesBy1() {
        DummyCombatant unit = new DummyCombatant("u")
                .hp(10)
                .shield(0);

        unit.statuses().addStacks(StatusType.BLEED, 2);
        unit.statuses().addStacks(StatusType.POISON, 3);
        unit.statuses().addStacks(StatusType.BURN, 1);

        // dot = (2+3+1)*1 = 6
        int dot = StatusEngine.applyEndPhase(unit);

        assertEquals(6, dot);
        assertEquals(4, unit.getHp()); // 10-6

        // 독은 엔드페이즈마다 1스택 감소
        assertEquals(2, unit.statuses().getStacks(StatusType.POISON));

        // 화상/출혈은 엔드페이즈 감소 없음
        assertEquals(1, unit.statuses().getStacks(StatusType.BURN));
        assertEquals(2, unit.statuses().getStacks(StatusType.BLEED));
    }

    @Test
    void endPhase_dotDamage_hitsShieldFirst() {
        DummyCombatant unit = new DummyCombatant("u")
                .hp(10)
                .shield(5);

        unit.statuses().addStacks(StatusType.POISON, 7); // dot=7

        StatusEngine.applyEndPhase(unit);

        // 실드 5 먼저 -> HP로 2
        assertEquals(0, unit.getShield());
        assertEquals(8, unit.getHp());
    }
}
