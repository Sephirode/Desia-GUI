package desia.battle;

import desia.character.EnemyInstance;
import desia.io.Io;
import desia.progress.GameSession;
import desia.ui.UiText;
import desia.status.StatusEngine;
import desia.skill.*;
import desia.item.ConsumableEngine;
import desia.item.Consumables;
import desia.combat.DamageEngine;
import desia.combat.DamageType;
import desia.audio.AudioService;
import desia.audio.Sfx;

import java.util.Random;
import java.util.*;

public class BattleEngine {
    private final Io io;
    private final Random random = new Random();
    private final SkillSetRepository skillSets = new SkillSetRepository();
    private final SkillEngine skillEngine = new SkillEngine(random);


    public BattleEngine(Io io) {
        this.io = io;
    }

    public BattleOutcome fight(GameSession session, EnemyInstance enemy) {

        // Switch BGM to battle/boss track (if audio resources exist).
        AudioService audio = AudioService.get();
        audio.playBattleFor(session.getChapter(), enemy.getName(), enemy.isBoss());

        try {

        // 상태이상 초기화
        session.resetBattleStatuses();

        System.out.println("\n[전투] " + enemy.getName());
        System.out.println(enemy.getDescription());

        while (session.getHp() > 0 && enemy.getHp() > 0) {

            // HUD update for JavaFX (safe no-op for console).
            io.onBattleHud(
                    session.getChapter(),
                    session.getAct(),
                    session.getPlayerName(),
                    session.getLevel(),
                    session.getHp(),
                    session.getMaxHp(),
                    enemy.getName(),
                    enemy.getLevel(),
                    enemy.getHp(),
                    enemy.getMaxHp()
            );

            boolean playerFirst = (session.getSpd() >= enemy.getSpd());

            UiText.clear();
            String pLine = "\nLv. " + session.getLevel() + " " + session.getPlayerName()
                    + "\nHP: " + Math.round(session.getHp()) + "/" + Math.round(session.getMaxHp())
                    + " MP: " + Math.round(session.getMp()) + "/" + Math.round(session.getMaxMp());
            // 실드를 보유하지 않는 개체는 처음부터 표기하지 않는다.
            if (Math.round(session.getShield()) > 0 || Math.round(session.getEquipBaseShield()) > 0) {
                pLine += " SHD: " + Math.round(session.getShield());
            }
            System.out.println(pLine);

            UiText.separator(30);
            String eLine = "Lv. " + enemy.getLevel() + " " + enemy.getName()
                    + "\nHP: " + Math.round(enemy.getHp()) + "/" + Math.round(enemy.getMaxHp());
            if (Math.round(enemy.getShield()) > 0) {
                eLine += " SHD: " + Math.round(enemy.getShield());
            }
            System.out.println(eLine);

            if (playerFirst) {
                TurnResult r = playerTurn(session, enemy);
                if (r == TurnResult.NO_TURN) { io.anythingToContinue(); continue; }
                if (r == TurnResult.ESCAPE) {
                    session.endBattleCleanup();
                    return BattleOutcome.ESCAPE;
                }
                if (enemy.getHp() <= 0) break;

                enemyTurn(session, enemy);
            } else {
                enemyTurn(session, enemy);
                if (session.getHp() <= 0) break;

                TurnResult r = playerTurn(session, enemy);
                if (r == TurnResult.NO_TURN) { io.anythingToContinue(); continue; }
                if (r == TurnResult.ESCAPE) {
                    session.endBattleCleanup();
                    return BattleOutcome.ESCAPE;
                }
            }

            // === 엔드 페이즈 ===
            // 상태이상 스택 감소
            if (session.getHp() > 0 && enemy.getHp() > 0) {
                int pDot = StatusEngine.applyEndPhase(session);
                int eDot = StatusEngine.applyEndPhase(enemy);
                if (pDot > 0) System.out.println("\n[엔드] " + session.getPlayerName() + " 도트 피해: " + pDot);
                if (eDot > 0) System.out.println("\n[엔드] " + enemy.getName() + " 도트 피해: " + eDot);
            }

            io.anythingToContinue();
        }

        if (session.getHp() <= 0) {
            System.out.println("\n패배... 게임 오버");
            io.anythingToContinue();
            session.endBattleCleanup();
            return BattleOutcome.LOSE;
        }

        System.out.println("\n승리!");
        io.anythingToContinue();
        session.endBattleCleanup();
        return BattleOutcome.WIN;
        } finally {
            // Back to idle BGM (menus / non-battle)
            audio.playIdleForChapter(session.getChapter());
        }
    }


    // 이하 3개는 헬퍼 메소드
    // 플레이어 턴
    private enum TurnResult { NO_TURN, TURN_SPENT, ESCAPE }

    private TurnResult playerTurn(GameSession session, EnemyInstance enemy) {

        // 행동 불가(스택형 제어계)
        if (StatusEngine.blocksAction(session)) {
            System.out.println("\n" + StatusEngine.blockReason(session) + " 상태로 행동할 수 없다!");
            return TurnResult.TURN_SPENT;
        }

        int cmd = io.choose("[행동 선택]", List.of("기본 공격", "스킬", "아이템", "도망"));

        switch (cmd) {
            case 1 -> {
                double raw = Math.max(1, session.getAtk());
                AudioService.get().playSfx(Sfx.HIT);
                double dealtToHp = DamageEngine.deal(session, enemy, raw, DamageType.PHYSICAL, 1);
                double finalDmg = Math.max(1, raw - enemy.getDef() * 0.5);
                double absorbed = Math.max(0, finalDmg - dealtToHp);
                if (absorbed > 0) {
                    System.out.println("플레이어의 공격! 실드 " + Math.round(absorbed) + " 흡수 + HP " + Math.round(dealtToHp) + " 피해");
                } else {
                    System.out.println("플레이어의 공격! " + Math.round(dealtToHp) + " 피해");
                }
                return TurnResult.TURN_SPENT;
            }
            case 2 -> {
                // 레벨업/룰 기반으로 해금된 스킬만 표시
                List<String> nameList = session.knownSkillsList();
                List<String> validNames = new ArrayList<>();
                List<SkillDef> defs = new ArrayList<>();

                for (String n : nameList) {
                    SkillDef def = session.skillDef(n);
                    if (def != null) {
                        validNames.add(n);
                        defs.add(def);
                    }
                }

                if (defs.isEmpty()) {
                    System.out.println("\n사용 가능한 스킬이 없다. (resources/skillsets.json / skill_unlocks.json 확인)");
                    return TurnResult.NO_TURN;
                }

                List<String> labels = new ArrayList<>();
                for (int i = 0; i < defs.size(); i++) {
                    SkillDef d = defs.get(i);
                    labels.add(validNames.get(i) + "  (MP: " + d.getMpCost() + ")");
                }
                int pick = io.chooseAllowCancel("[스킬 선택]", labels, "뒤로");
                if (pick == 0) return TurnResult.NO_TURN;

                String chosenName = validNames.get(pick - 1);
                SkillDef chosen = defs.get(pick - 1);

                SkillCastResult r = skillEngine.cast(chosenName, chosen, session, enemy);
                for (String line : r.getLogs()) System.out.println(line);
                io.anythingToContinue();

                return r.isSpentTurn() ? TurnResult.TURN_SPENT : TurnResult.NO_TURN;
            }

            case 3 -> {
                return itemTurn(session, enemy);
            }
            case 4 -> {
                // 도망 시도는 "턴 소모"
                boolean ok = tryEscape(session, enemy);
                if (ok) {
                    System.out.println("\n도망 성공!");
                    return TurnResult.ESCAPE;
                } else {
                    System.out.println("\n도망 실패!");
                    return TurnResult.TURN_SPENT;
                }
            }
            default -> {
                return TurnResult.NO_TURN;
            }
        }
    }

    /**
     * 전투 중 아이템 사용.
     * - 인벤토리에서 전투 중 사용 가능한 소모품(useInBattle=true)만 보여준다.
     * - 사용 성공 시 기본적으로 턴을 소모한다.
     * - ESCAPE 타입 아이템은 보스가 아니면 즉시 전투를 종료한다.
     */
    private TurnResult itemTurn(GameSession session, EnemyInstance enemy) {
        Map<String, Integer> inv = session.inventoryView();
        if (inv.isEmpty()) {
            System.out.println("\n전투 중 사용할 아이템이 없다.");
            return TurnResult.NO_TURN;
        }

        // 전투 중 사용 가능한 아이템만 추린다.
        List<String> usable = new ArrayList<>();
        for (Map.Entry<String, Integer> e : inv.entrySet()) {
            String name = e.getKey();
            int cnt = (e.getValue() == null) ? 0 : e.getValue();
            if (cnt <= 0) continue;
            Consumables def = session.consumableDef(name);
            if (def != null && def.isUseInBattle()) {
                usable.add(name);
            }
        }

        if (usable.isEmpty()) {
            System.out.println("\n전투 중 사용 가능한 아이템이 없다.");
            return TurnResult.NO_TURN;
        }

        List<String> itemLabels = new ArrayList<>();
        for (int i = 0; i < usable.size(); i++) {
            String name = usable.get(i);
            int cnt = inv.getOrDefault(name, 0);
            itemLabels.add(name + " x" + cnt);
        }
        int pick = io.chooseAllowCancel("[아이템]", itemLabels, "뒤로");
        if (pick == 0) return TurnResult.NO_TURN;

        String name = usable.get(pick - 1);
        Consumables c = session.consumableDef(name);
        if (c == null) {
            System.out.println("정의되지 않은 아이템이라 사용 불가: " + name);
            return TurnResult.NO_TURN;
        }

        // 소비 먼저(실패 시 환불)
        if (!session.removeItem(name, 1)) {
            System.out.println("아이템 사용 실패(수량 부족): " + name);
            return TurnResult.NO_TURN;
        }

        ConsumableEngine.ApplyResult r = ConsumableEngine.applyInBattle(session, enemy, c, skillEngine);
        for (String line : r.logs) System.out.println(line);

        if (!r.success) {
            // 실패하면 환불
            session.addItem(name, 1);
            System.out.println("아이템 적용 실패: " + name);
            return TurnResult.NO_TURN;
        }

        if (r.escaped) {
            return TurnResult.ESCAPE;
        }
        return r.spentTurn ? TurnResult.TURN_SPENT : TurnResult.NO_TURN;
    }


    // 도주 확률 공식 메소드
    private boolean tryEscape(GameSession session, EnemyInstance enemy) {

        // 속도 차이 1당 2%
        double escapeChance = (session.getSpd() - enemy.getSpd()) * 2;
        if (escapeChance <= 0)
            escapeChance = 0;

        int roll = random.nextInt(100) + 1; // 1~100

        return roll <= escapeChance;
    }


    // 적 턴
    private void enemyTurn(GameSession session, EnemyInstance enemy) {

        // 행동 불가(스택형 제어계)
        if (StatusEngine.blocksAction(enemy)) {
            System.out.println("\n" + enemy.getName() + "은(는) " + StatusEngine.blockReason(enemy) + " 상태로 행동할 수 없다!");
            return;
        }

        // 적도 기본/스킬만 있다고 치자(임시)
        // 적 스킬 사용 여부는 "적 MP"로만 내부 판단(표시는 절대 하지 않음)
        boolean canSkill = enemy.getMp() >= 5;
        boolean useSkill = canSkill && random.nextInt(100) < 40; // 40%

        double dmg;

        if (useSkill) {
            enemy.setMp(enemy.getMp() - 5);
            dmg = Math.max(1, enemy.getMagic());
            System.out.println("\n" + enemy.getName() + "의 스킬 공격!");
            AudioService.get().playSfx(Sfx.HIT);
            double dealtToHp = DamageEngine.deal(enemy, session, dmg, DamageType.MAGIC, 1);
            double finalDmg = Math.max(1, dmg - session.getMdef() * 0.5);
            double absorbed = Math.max(0, finalDmg - dealtToHp);
            if (absorbed > 0) System.out.println("실드 " + Math.round(absorbed) + " 흡수 + HP " + Math.round(dealtToHp) + " 피해를 입었다.");
            else System.out.println(Math.round(dealtToHp) + " 피해를 입었다.");
        } else {
            dmg = Math.max(1, enemy.getAtk());
            System.out.println("\n" + enemy.getName() + "의 공격!");
            AudioService.get().playSfx(Sfx.HIT);
            double dealtToHp = DamageEngine.deal(enemy, session, dmg, DamageType.PHYSICAL, 1);
            double finalDmg = Math.max(1, dmg - session.getDef() * 0.5);
            double absorbed = Math.max(0, finalDmg - dealtToHp);
            if (absorbed > 0) System.out.println("실드 " + Math.round(absorbed) + " 흡수 + HP " + Math.round(dealtToHp) + " 피해를 입었다.");
            else System.out.println(Math.round(dealtToHp) + " 피해를 입었다.");
        }
    }
}
