package desia.skill;

import desia.combat.*;
import desia.status.*;
import java.util.*;

public class SkillEngine {
    private final Random rng;

    public SkillEngine(Random rng) {
        this.rng = (rng == null) ? new Random() : rng;
    }

    public SkillCastResult cast(String skillName, SkillDef skill, Combatant caster, Combatant enemyTarget) {
        return cast(skillName, skill, caster, enemyTarget, null);
    }

    // mpCostOverride: null이면 스킬 정의의 mp_cost 사용, 아니면 override 값 사용
    public SkillCastResult cast(String skillName, SkillDef skill, Combatant caster, Combatant enemyTarget, Integer mpCostOverride) {
        if (skill == null) return SkillCastResult.noTurn("스킬 데이터가 없다: " + skillName);
        if (caster == null) return SkillCastResult.noTurn("시전자가 없다.");

        Combatant target = resolveTarget(skill, caster, enemyTarget);

        int cost = (mpCostOverride == null) ? Math.max(0, skill.getMpCost()) : Math.max(0, mpCostOverride);
        if (caster.getMp() < cost) return SkillCastResult.noTurn("MP가 부족하다! (필요 MP: " + cost + ")");
        caster.setMp(caster.getMp() - cost);

        List<String> logs = new ArrayList<>();
        logs.add("\n[" + skillName + "]");

        boolean did = false;

        if (skill.getComponents() != null) {
            for (SkillComponent c : skill.getComponents()) {
                if (c == null) continue;
                String kind = safe(c.getKind());

                switch (kind) {
                    case "damage" -> {
                        double raw = evalTerms(c.getTerms(), caster, target, cost);
                        DamageType dt = DamageType.from(c.getDamageType());
                        double dealt = DamageEngine.deal(caster, target, raw, dt, 1);
                        logs.add("피해: " + Math.round(dealt));
                        did = true;
                    }
                    case "heal" -> {
                        double amount = Math.max(0, evalTerms(c.getTerms(), caster, target, cost));
                        boolean mpHeal = isMpHealSkill(skillName, skill);
                        if (mpHeal) {
                            double before = target.getMp();
                            target.setMp(target.getMp() + amount);
                            logs.add("MP 회복: " + Math.round(target.getMp() - before));
                        } else {
                            double before = target.getHp();
                            target.setHp(target.getHp() + amount);
                            logs.add("HP 회복: " + Math.round(target.getHp() - before));
                        }
                        did = true;
                    }
                    case "shield" -> {
                        double amount = Math.max(0, evalTerms(c.getTerms(), caster, target, cost));
                        target.addShield(amount);
                        logs.add("실드: +" + Math.round(amount) + " (현재 실드: " + Math.round(target.getShield()) + ")");
                        did = true;
                    }
                    default -> logs.add("(미지원 효과) kind=" + kind);
                }
            }
        }

        if (skill.getStatusEffects() != null) {
            for (SkillStatusEffect eff : skill.getStatusEffects()) {
                if (eff == null) continue;

                Combatant effTarget = resolveEffectTarget(eff, caster, enemyTarget);
                StatusType st = toStatusType(eff.getStatus());
                if (st == null) continue;

                double chance = eff.getChance();
                int stacks = Math.max(1, eff.getStacks());

                if (rng.nextDouble() <= chance) {
                    effTarget.statuses().addStacks(st, stacks);
                    logs.add("상태이상: " + st + " +" + stacks + " (현재 " + effTarget.statuses().getStacks(st) + ")");
                    did = true;
                } else {
                    logs.add("상태이상 실패: " + st);
                }
            }
        }

        if (!did) logs.add("(주의) 이 스킬은 현재 구현된 효과가 없다(components/status_effects 비어 있음).");

        return SkillCastResult.turn(logs);
    }

    private Combatant resolveTarget(SkillDef skill, Combatant caster, Combatant enemyTarget) {
        return safe(skill.getTarget()).equals("enemy") ? enemyTarget : caster;
    }

    private Combatant resolveEffectTarget(SkillStatusEffect eff, Combatant caster, Combatant enemyTarget) {
        return safe(eff.getTarget()).equals("enemy") ? enemyTarget : caster;
    }

    private double evalTerms(List<SkillTerm> terms, Combatant self, Combatant target, int spentMp) {
        if (terms == null || terms.isEmpty()) return 0;
        double sum = 0;
        for (SkillTerm t : terms) {
            if (t == null) continue;
            String stat = safe(t.getStat());
            double coef = t.getCoef();

            double base = switch (stat) {
                case "self_attack" -> self.getAtk();
                case "self_magic" -> self.getMagic();
                case "self_def" -> self.getDef();
                case "self_mdef" -> self.getMdef();
                case "self_spd" -> self.getSpd();
                case "self_hp" -> self.getHp();
                case "self_max_hp" -> self.getMaxHp();
                case "self_missing_hp" -> (self.getMaxHp() - self.getHp());
                case "target_hp" -> target.getHp();
                case "target_max_hp" -> target.getMaxHp();
                case "target_missing_hp" -> (target.getMaxHp() - target.getHp());
                case "self_spent_mp" -> spentMp;
                case "constant" -> 1.0; // coef 자체가 값
                default -> 0.0;
            };

            sum += base * coef;
        }
        return sum;
    }

    private StatusType toStatusType(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "bleed" -> StatusType.BLEED;
            case "poison" -> StatusType.POISON;
            case "burn" -> StatusType.BURN;
            case "paralysis" -> StatusType.PARALYSIS;
            case "panic" -> StatusType.PANIC;
            case "freeze" -> StatusType.FREEZE;
            case "sleep" -> StatusType.SLEEP;
            default -> null;
        };
    }

    private boolean isMpHealSkill(String name, SkillDef skill) {
        if (name != null && (name.contains("마나") || name.toLowerCase(Locale.ROOT).contains("mp"))) return true;
        return skill != null && skill.getDescription() != null && skill.getDescription().contains("마나");
    }

    private String safe(String s) {
        return (s == null) ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
