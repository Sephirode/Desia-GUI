package desia.item;

import desia.character.EnemyInstance;
import desia.progress.GameSession;
import desia.skill.SkillCastResult;
import desia.skill.SkillDef;
import desia.skill.SkillEngine;
import desia.status.StatusType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConsumableEngine {
    private ConsumableEngine() {}

    public static final class ApplyResult {
        public final boolean success;
        public final boolean spentTurn;
        public final boolean escaped;
        public final List<String> logs;

        private ApplyResult(boolean success, boolean spentTurn, boolean escaped, List<String> logs) {
            this.success = success;
            this.spentTurn = spentTurn;
            this.escaped = escaped;
            this.logs = (logs == null) ? List.of() : logs;
        }

        public static ApplyResult ok(boolean spentTurn, boolean escaped, List<String> logs) {
            return new ApplyResult(true, spentTurn, escaped, logs);
        }

        public static ApplyResult fail(String msg) {
            List<String> logs = new ArrayList<>();
            if (msg != null && !msg.isBlank()) logs.add(msg);
            return new ApplyResult(false, false, false, logs);
        }
    }

    public static ApplyResult applyOutOfBattle(GameSession session, Consumables c) {
        return apply(session, null, c, false, null);
    }

    public static ApplyResult applyInBattle(GameSession session, EnemyInstance enemy, Consumables c, SkillEngine skillEngine) {
        return apply(session, enemy, c, true, skillEngine);
    }

    private static ApplyResult apply(GameSession session, EnemyInstance enemy, Consumables c, boolean inBattle, SkillEngine skillEngine) {
        if (session == null) return ApplyResult.fail("세션이 null이라 아이템을 적용할 수 없다.");
        if (c == null) return ApplyResult.fail("아이템 정의가 null이다.");

        String type = safe(c.getEffectType()).toUpperCase(Locale.ROOT);
        List<String> logs = new ArrayList<>();
        logs.add("\n[" + safe(c.getName()) + "]");

        switch (type) {
            case "HEAL_HP" -> {
                double amount = computeHpHeal(session, c);
                double before = session.getHp();
                session.setHp(before + amount);
                logs.add("HP 회복: +" + Math.round(session.getHp() - before));
                return ApplyResult.ok(true, false, logs);
            }
            case "HEAL_MP" -> {
                double amount = computeMpHeal(session, c);
                double before = session.getMp();
                session.setMp(before + amount);
                logs.add("MP 회복: +" + Math.round(session.getMp() - before));
                return ApplyResult.ok(true, false, logs);
            }
            case "RESTORE_FULL" -> {
                session.setHp(session.getMaxHp());
                session.setMp(session.getMaxMp());
                logs.add("HP/MP 완전 회복");
                return ApplyResult.ok(true, false, logs);
            }
            case "ADD_SHIELD" -> {
                if (!inBattle) return ApplyResult.fail("이 아이템은 전투 중에만 사용할 수 있다.");
                double amount = computeShield(session, c);
                double before = session.getShield();
                session.addShield(amount);
                logs.add("실드: +" + Math.round(session.getShield() - before) + " (현재 실드: " + Math.round(session.getShield()) + ")");
                return ApplyResult.ok(true, false, logs);
            }
            case "PERM_STATS" -> {
                applyPermStats(session, c, logs);
                return ApplyResult.ok(true, false, logs);
            }
            case "MIXED" -> {
                if (c.getHp() != 0 || safe(c.getDescription()).contains("체력")) {
                    double amount = computeHpHeal(session, c);
                    if (amount > 0) {
                        double before = session.getHp();
                        session.setHp(before + amount);
                        logs.add("HP 회복: +" + Math.round(session.getHp() - before));
                    }
                }
                if (c.getMp() != 0 || safe(c.getDescription()).contains("마나")) {
                    double amount = computeMpHeal(session, c);
                    if (amount > 0) {
                        double before = session.getMp();
                        session.setMp(before + amount);
                        logs.add("MP 회복: +" + Math.round(session.getMp() - before));
                    }
                }
                applyPermStats(session, c, logs);
                return ApplyResult.ok(true, false, logs);
            }
            case "LEVEL_UP" -> {
                int inc = Math.max(1, c.getLevel());
                for (int i = 0; i < inc; i++) {
                    session.setLevel(session.getLevel() + 1);
                    // 레벨업 보상과 동일하게: 최대치의 절반 회복
                    session.setHp(session.getHp() + session.getMaxHp() * 0.5);
                    session.setMp(session.getMp() + session.getMaxMp() * 0.5);
                }
                logs.add("레벨 +" + inc + " (현재 Lv. " + session.getLevel() + ")");
                return ApplyResult.ok(true, false, logs);
            }
            case "REMOVE_STATUS" -> {
                removeStatus(session, c, logs);
                return ApplyResult.ok(true, false, logs);
            }
            case "ESCAPE" -> {
                if (!inBattle) return ApplyResult.fail("이 아이템은 전투 중에만 사용할 수 있다.");
                if (enemy == null) return ApplyResult.fail("전투 대상이 없어 도망칠 수 없다.");
                if (enemy.isBoss()) {
                    logs.add("보스에게는 통하지 않는다!");
                    return ApplyResult.ok(true, false, logs);
                }
                logs.add("연막탄! 도망쳤다.");
                return ApplyResult.ok(true, true, logs);
            }
            case "CAST_SKILL" -> {
                if (!inBattle) return ApplyResult.fail("이 아이템은 전투 중에만 사용할 수 있다.");
                if (enemy == null) return ApplyResult.fail("전투 대상이 없어 스킬을 시전할 수 없다.");
                if (skillEngine == null) return ApplyResult.fail("SkillEngine이 없어 스킬을 시전할 수 없다.");

                String desc = safe(c.getDescription());
                String skillName = parseTagValue(desc, "CAST_SKILL");
                if (skillName == null || skillName.isBlank())
                    return ApplyResult.fail("아이템 설명에 [CAST_SKILL:스킬명] 태그가 없다: " + safe(c.getName()));

                Integer mpOverride = parseIntTagValue(desc, "MP_OVERRIDE");

                SkillDef def = session.skillDef(skillName);
                if (def == null) return ApplyResult.fail("스킬 정의를 찾을 수 없다: " + skillName);

                SkillCastResult r = skillEngine.cast(skillName, def, session, enemy, mpOverride);
                logs.addAll(r.getLogs());

                if (!r.isSpentTurn()) {
                    return ApplyResult.fail("아이템 사용 실패: " + String.join(" / ", r.getLogs()));
                }
                return ApplyResult.ok(true, false, logs);
            }
            case "DEBUG" -> {
                logs.add("(DEBUG) 아무 효과 없음");
                return ApplyResult.ok(true, false, logs);
            }
            default -> {
                return ApplyResult.fail("미지원 아이템 effectType: " + safe(c.getEffectType()));
            }
        }
    }

    private static void applyPermStats(GameSession session, Consumables c, List<String> logs) {
        double bMaxHp = c.getMaxHp();
        double bMaxMp = c.getMaxMp();
        double bAtk = c.getAtk();
        double bMagic = c.getMagic();
        double bDef = c.getDef();
        double bMdef = c.getMdef();
        double bSpd = c.getSpd();

        boolean any = false;
        if (bMaxHp != 0) { logs.add("최대 HP: +" + Math.round(bMaxHp)); any = true; }
        if (bMaxMp != 0) { logs.add("최대 MP: +" + Math.round(bMaxMp)); any = true; }
        if (bAtk != 0) { logs.add("공격력: +" + Math.round(bAtk)); any = true; }
        if (bMagic != 0) { logs.add("마력: +" + Math.round(bMagic)); any = true; }
        if (bDef != 0) { logs.add("방어력: +" + Math.round(bDef)); any = true; }
        if (bMdef != 0) { logs.add("마법저항: +" + Math.round(bMdef)); any = true; }
        if (bSpd != 0) { logs.add("속도: +" + Math.round(bSpd)); any = true; }
        if (!any) logs.add("(경고) 영구 스탯 변화가 0이다.");

        session.addPermanentStats(bMaxHp, bMaxMp, bAtk, bMagic, bDef, bMdef, bSpd);
    }

    private static void removeStatus(GameSession session, Consumables c, List<String> logs) {
        String desc = safe(c.getDescription());
        if (desc.contains("모든")) {
            session.statuses().clearAll();
            logs.add("모든 상태이상 제거");
            return;
        }

        boolean any = false;
        if (desc.contains("출혈")) { session.statuses().clear(StatusType.BLEED); logs.add("출혈 제거"); any = true; }
        if (desc.contains("중독") || desc.contains("독")) { session.statuses().clear(StatusType.POISON); logs.add("중독 제거"); any = true; }
        if (desc.contains("화상")) { session.statuses().clear(StatusType.BURN); logs.add("화상 제거"); any = true; }

        if (!any) logs.add("(경고) 제거 대상 상태이상을 판별하지 못했다.");
    }

    private static double computeHpHeal(GameSession session, Consumables c) {
        String desc = safe(c.getDescription());
        Double v = parseBasePlusPercent(desc, "최대\s*체력", session.getMaxHp());
        if (v != null) return v;
        return Math.max(0, c.getHp());
    }

    private static double computeMpHeal(GameSession session, Consumables c) {
        String desc = safe(c.getDescription());
        Double v = parseBasePlusPercent(desc, "최대\s*마나", session.getMaxMp());
        if (v != null) return v;
        return Math.max(0, c.getMp());
    }

    private static double computeShield(GameSession session, Consumables c) {
        String desc = safe(c.getDescription());
        Double v = parseBasePlusPercent(desc, "최대\s*체력", session.getMaxHp());
        if (v != null) return v;
        // fallback: 데이터에 수치가 없어서 임시로 price 사용
        return Math.max(0, c.getPrice());
    }

    /**
     * description에서 "(base + 최대 xxx의 pct%)" 형태를 파싱한다.
     * - base, pct 둘 다 정수/실수 허용
     */
    private static Double parseBasePlusPercent(String desc, String maxKindRegex, double maxValue) {
        if (desc == null) return null;

        Pattern p = Pattern.compile("\\(\\s*([0-9]+(?:\\.[0-9]+)?)\\s*\\+\\s*" + maxKindRegex + "\\s*의\\s*([0-9]+(?:\\.[0-9]+)?)%\\s*\\)");
        Matcher m = p.matcher(desc);
        if (!m.find()) return null;

        double base = parseDoubleSafe(m.group(1));
        double pct = parseDoubleSafe(m.group(2));
        return Math.max(0, base + maxValue * (pct / 100.0));
    }

    private static String parseTagValue(String desc, String tag) {
        if (desc == null || tag == null) return null;
        Pattern p = Pattern.compile("\\[" + Pattern.quote(tag) + ":([^\\]]+)]");
        Matcher m = p.matcher(desc);
        if (!m.find()) return null;
        return safe(m.group(1)).trim();
    }

    private static Integer parseIntTagValue(String desc, String tag) {
        String v = parseTagValue(desc, tag);
        if (v == null) return null;
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static double parseDoubleSafe(String s) {
        if (s == null) return 0;
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static String safe(String s) {
        return (s == null) ? "" : s;
    }
}
