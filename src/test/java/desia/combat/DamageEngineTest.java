package desia.combat;

import desia.testutil.DummyCombatant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DamageEngineTest {

    @Test
    void deal_shieldAbsorbsFirst_thenHpReduced() {
        DummyCombatant attacker = new DummyCombatant("atk");
        DummyCombatant target = new DummyCombatant("tgt")
                .hp(100)
                .shield(30)
                .def(0);

        // raw 50, 물리, hitCount 1
        double hpDmg = DamageEngine.deal(attacker, target, 50, DamageType.PHYSICAL, 1);

        // 실드 30 먼저 -> HP로 20 들어가야 함
        assertEquals(0, target.getShield());
        assertEquals(80, target.getHp());
        assertEquals(20, hpDmg);
    }

    @Test
    void deal_physicalReducedByDefHalf_min1_andRounded() {
        DummyCombatant attacker = new DummyCombatant("atk");
        DummyCombatant target = new DummyCombatant("tgt")
                .hp(100)
                .shield(0)
                .def(10); // def*0.5 = 5 감산

        // raw 6 -> 6-5 = 1 -> round(1)=1
        double hpDmg = DamageEngine.deal(attacker, target, 6, DamageType.PHYSICAL, 1);

        assertEquals(99, target.getHp());
        assertEquals(1, hpDmg);
    }
}
