package desia.inventory;

import desia.io.Io;
import desia.item.Consumables;
import desia.item.ConsumableEngine;
import desia.item.EquipmentDef;
import desia.item.EquipmentSetDef;
import desia.progress.GameSession;
import desia.ui.UiText;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 최소 인벤토리: 소모품 사용(전투 외) + 판매.
 */
public class InventoryService {
    private final Io io;

    public InventoryService(Io io) {
        this.io = io;
    }

    public void open(GameSession session) {
        while (true) {
            UiText.clear();
            UiText.heading("[인벤토리]",1);
            Map<String, Integer> owned = ownedView(session);
            if (owned.isEmpty()) {
                System.out.println("(인벤토리 비어있음)");
            }
            else{
                List<String> names = new ArrayList<>(owned.keySet());
                for (int i = 0; i < names.size(); i++) {
                    String name = names.get(i);
                    int cnt = owned.getOrDefault(name, 0);
                    System.out.println((i + 1) + ") " + name + itemKindTag(session, name) + equippedTag(session, name) + " x" + cnt);
                    String desc = itemDesc(session, name);
                    if (desc != null && !desc.isBlank()) {
                        System.out.println("   - " + desc);
                    }
                    String eff = itemEffect(session, name);
                    if (eff != null && !eff.isBlank()) {
                        System.out.println("   - 효과: " + eff);
                    }
                }
            }
            System.out.println("\n소지금: " + Math.round(session.getGold())+"골드");
            int cmd = io.choose("[인벤토리]", List.of("아이템 사용", "나가기"));

            if (cmd == 1) {
                useInvMenu(session);
            }
            else {
                return;
            }
        }
    }

    private void useInvMenu(GameSession session) {
        while (true) {
            Map<String, Integer> inv = session.inventoryView();
            if (inv.isEmpty()) {
                System.out.println("인벤토리가 비었습니다.");
                io.anythingToContinue();
                return;
            }

            List<String> names = new ArrayList<>(inv.keySet());
            System.out.println("\n[사용] 아이템을 선택하세요");
            List<String> labels = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                String name = names.get(i);
                int cnt = inv.get(name);
                labels.add(name + itemKindTag(session, name) + " x" + cnt);
                // 상세(플레이버/효과) 출력은 기존대로 유지
                System.out.println("- " + name + itemKindTag(session, name) + " x" + cnt);
                String desc = itemDesc(session, name);
                if (desc != null && !desc.isBlank()) {
                    System.out.println("   - " + desc);
                }
                String eff = itemEffect(session, name);
                if (eff != null && !eff.isBlank()) {
                    System.out.println("   - 효과: " + eff);
                }
            }
            int input = io.chooseAllowCancel("[아이템 사용]", labels, "뒤로");
            if (input == 0) return;

            String name = names.get(input - 1);

            // 장비는 인벤토리에서 '사용'하지 않는다.
            if (session.equipmentDef(name) != null) {
                System.out.println("장비 착용은 장비 탭에서 해야 합니다.");
                io.anythingToContinue();
                continue;
            }

            Consumables c = session.consumableDef(name);
            if (c == null) {
                System.out.println("정의되지 않은 아이템이라 사용 불가: " + name);
                    io.anythingToContinue();
                    continue;
            }
            if (!c.isUseOutOfBattle()) {
                System.out.println("전투 밖에서는 사용할 수 없습니다.");
                io.anythingToContinue();
                continue;
            }
            if (!session.removeItem(name, 1)) {
                System.out.println("사용 실패");
                io.anythingToContinue();
                continue;
            }
            boolean ok = applyConsumable(session, c);
            if (!ok) {
                // 실패 시 환불
                session.addItem(name, 1);
                System.out.println("사용이 취소되었다: " + name);
            } else {
                System.out.println("사용 완료: " + name);
            }
            io.anythingToContinue();
        }
    }

    private boolean applyConsumable(GameSession session, Consumables c) {
        ConsumableEngine.ApplyResult r = ConsumableEngine.applyOutOfBattle(session, c);
        for (String line : r.logs) System.out.println(line);
        if (!r.success) {
            System.out.println("아이템 적용 실패");
        }
        return r.success;
    }

    private String itemDesc(GameSession session, String name) {
        if (name == null) return "";
        Consumables c = session.consumableDef(name);
        if (c != null) return c.getDescription();
        var eq = session.equipmentDef(name);
        if (eq != null) return eq.getDescription();
        return "";
    }

    private String itemEffect(GameSession session, String name) {
        if (name == null) return "";
        Consumables c = session.consumableDef(name);
        if (c != null) return describeConsumable(session, c);
        EquipmentDef eq = session.equipmentDef(name);
        if (eq != null) return describeEquipment(session, eq);
        return "";
    }

    private String itemKindTag(GameSession session, String name) {
        if (session.consumableDef(name) != null) return "";
        var eq = session.equipmentDef(name);
        if (eq == null) return "";
        String slot = (eq.getSlot() == null) ? "장비" : eq.getSlot();
        String rar = (eq.getRarity() == null) ? "" : eq.getRarity();
        return " (" + slot + (rar.isBlank() ? "" : "/" + rar) + ")";
    }

    private String equippedTag(GameSession session, String name) {
        if (session == null || name == null) return "";
        return session.equippedView().containsValue(name) ? " (장착 중)" : "";
    }

    /**
     * 인벤토리(소지품) + 장착 중인 장비를 합쳐서 보여주기 위한 뷰.
     * - 장착 중인 장비는 개수 +1로 합산된다.
     */
    private Map<String, Integer> ownedView(GameSession session) {
        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        if (session == null) return out;
        // 인벤토리 먼저
        for (Map.Entry<String, Integer> e : session.inventoryView().entrySet()) {
            if (e == null) continue;
            String k = e.getKey();
            int v = (e.getValue() == null) ? 0 : e.getValue();
            if (k == null || k.isBlank() || v <= 0) continue;
            out.put(k, v);
        }
        // 장착 장비 합산
        for (String v : session.equippedView().values()) {
            if (v == null || v.isBlank()) continue;
            out.put(v, out.getOrDefault(v, 0) + 1);
        }
        return out;
    }

    // ===== 효과 요약 =====

    private String describeConsumable(GameSession session, Consumables c) {
        if (session == null || c == null) return "";

        String type = safe(c.getEffectType()).toUpperCase(Locale.ROOT);
        List<String> parts = new ArrayList<>();

        switch (type) {
            case "HEAL_HP" -> {
                long amount = Math.round(computeBasePlusPercent(safe(c.getDescription()), "최대\\s*체력", session.getMaxHp(), c.getHp()));
                parts.add("HP " + signed(amount));
            }
            case "HEAL_MP" -> {
                long amount = Math.round(computeBasePlusPercent(safe(c.getDescription()), "최대\\s*마나", session.getMaxMp(), c.getMp()));
                parts.add("MP " + signed(amount));
            }
            case "RESTORE_FULL" -> {
                parts.add("HP/MP 완전 회복");
            }
            case "ADD_SHIELD" -> {
                // 실드는 (base + 최대 체력의 pct%) 형태를 쓰는 아이템이 있어서 최대HP 기준으로 계산
                long amount = Math.round(computeBasePlusPercent(safe(c.getDescription()), "최대\\s*체력", session.getMaxHp(), c.getPrice()));
                parts.add("실드 " + signed(amount) + " (전투)");
            }
            case "PERM_STATS" -> {
                addPermStatParts(parts, c, true);
            }
            case "MIXED" -> {
                // MIXED는 회복 + 영구 스탯이 섞여있을 수 있다
                long hp = Math.round(computeBasePlusPercent(safe(c.getDescription()), "최대\\s*체력", session.getMaxHp(), c.getHp()));
                long mp = Math.round(computeBasePlusPercent(safe(c.getDescription()), "최대\\s*마나", session.getMaxMp(), c.getMp()));
                if (hp != 0) parts.add("HP " + signed(hp));
                if (mp != 0) parts.add("MP " + signed(mp));
                addPermStatParts(parts, c, true);
            }
            case "LEVEL_UP" -> {
                int inc = Math.max(1, c.getLevel());
                parts.add("레벨 +" + inc);
                // 실제 회복량은 레벨업/레벨증가에 따라 변동하므로 규칙을 표시
                parts.add("레벨당 HP/MP 최대치 50% 회복");
            }
            case "REMOVE_STATUS" -> {
                String desc = safe(c.getDescription());
                if (desc.contains("모든")) parts.add("상태이상 제거(모두)");
                else {
                    List<String> st = new ArrayList<>();
                    if (desc.contains("출혈")) st.add("출혈");
                    if (desc.contains("중독") || desc.contains("독")) st.add("독");
                    if (desc.contains("화상")) st.add("화상");
                    parts.add(st.isEmpty() ? "상태이상 제거" : ("상태이상 제거(" + String.join("/", st) + ")"));
                }
            }
            case "ESCAPE" -> {
                parts.add("도주 (전투, 보스 제외)");
            }
            case "CAST_SKILL" -> {
                String skillName = parseTagValue(safe(c.getDescription()), "CAST_SKILL");
                if (skillName != null && !skillName.isBlank()) parts.add("스킬 시전: " + skillName.trim());
                else parts.add("스킬 시전");
            }
            default -> {
                // fallback: 데이터에 들어있는 모든 수치(0 제외)를 최대한 보여준다
                addIfNonZero(parts, "HP", c.getHp());
                addIfNonZero(parts, "MP", c.getMp());
                addIfNonZero(parts, "공격", c.getAtk());
                addIfNonZero(parts, "마력", c.getMagic());
                addIfNonZero(parts, "방어", c.getDef());
                addIfNonZero(parts, "마저", c.getMdef());
                addIfNonZero(parts, "최대HP", c.getMaxHp());
                addIfNonZero(parts, "최대MP", c.getMaxMp());
                addIfNonZero(parts, "속도", c.getSpd());
                addIfNonZero(parts, "레벨", c.getLevel());
                if (!type.isBlank()) parts.add("타입=" + type);
            }
        }

        // 사용 가능 조건도 같이 노출
        if (!c.isUseInBattle() && !type.isBlank()) parts.add("전투 사용 불가");
        if (!c.isUseOutOfBattle() && !type.isBlank()) parts.add("전투 밖 사용 불가");

        // 아무것도 없으면 빈 문자열
        parts.removeIf(s -> s == null || s.isBlank());
        return String.join(", ", parts);
    }

    private String describeEquipment(GameSession session, EquipmentDef e) {
        if (e == null) return "";

        List<String> parts = new ArrayList<>();
        if (e.getStats() != null) {
            for (Map.Entry<String, Integer> en : e.getStats().entrySet()) {
                if (en == null) continue;
                String k = en.getKey();
                int v = (en.getValue() == null) ? 0 : en.getValue();
                if (v == 0) continue;
                parts.add(statLabel(k) + " " + signed(v));
            }
        }

        // 세트 정보(있으면)
        String setName = safe(e.getSetName());
        if (!setName.isBlank()) {
            String setInfo = "세트=" + setName;
            if (session != null) {
                EquipmentSetDef set = session.equipmentSetsView().get(setName);
                if (set != null && set.getPieces() != null && !set.getPieces().isEmpty()) {
                    int cnt = 0;
                    for (String piece : set.getPieces()) {
                        if (piece == null) continue;
                        if (session.equippedView().containsValue(piece)) cnt++;
                    }
                    setInfo += " (" + cnt + "/" + set.getPieces().size() + ")";
                }
            }
            parts.add(setInfo);
        }

        return String.join(", ", parts);
    }

    private void addPermStatParts(List<String> parts, Consumables c, boolean markPermanent) {
        if (parts == null || c == null) return;
        String suf = markPermanent ? " (영구)" : "";
        addIfNonZero(parts, "최대HP", c.getMaxHp(), suf);
        addIfNonZero(parts, "최대MP", c.getMaxMp(), suf);
        addIfNonZero(parts, "공격", c.getAtk(), suf);
        addIfNonZero(parts, "마력", c.getMagic(), suf);
        addIfNonZero(parts, "방어", c.getDef(), suf);
        addIfNonZero(parts, "마저", c.getMdef(), suf);
        addIfNonZero(parts, "속도", c.getSpd(), suf);
    }

    private void addIfNonZero(List<String> parts, String label, double value) {
        addIfNonZero(parts, label, value, "");
    }

    private void addIfNonZero(List<String> parts, String label, double value, String suffix) {
        if (parts == null) return;
        long v = Math.round(value);
        if (v == 0) return;
        parts.add(label + " " + signed(v) + (suffix == null ? "" : suffix));
    }

    private void addIfNonZero(List<String> parts, String label, int value) {
        if (parts == null) return;
        if (value == 0) return;
        parts.add(label + " " + signed(value));
    }

    private String signed(long v) {
        return (v > 0 ? "+" : "") + v;
    }

    private String signed(int v) {
        return (v > 0 ? "+" : "") + v;
    }

    private String statLabel(String key) {
        if (key == null) return "스탯";
        return switch (key) {
            case "attack" -> "공격";
            case "defense" -> "방어";
            case "spell_power" -> "마력";
            case "magic_resist" -> "마저";
            case "max_hp" -> "최대HP";
            case "max_mp" -> "최대MP";
            case "speed" -> "속도";
            case "max_shield" -> "실드";
            default -> key;
        };
    }

    /**
     * description에서 "(base + 최대 xxx의 pct%)" 형태를 파싱한다.
     * 없으면 fallback을 그대로 반환한다.
     */
    private double computeBasePlusPercent(String desc, String maxKindRegex, double maxValue, double fallback) {
        Double v = parseBasePlusPercent(desc, maxKindRegex, maxValue);
        if (v != null) return v;
        return fallback;
    }

    private Double parseBasePlusPercent(String desc, String maxKindRegex, double maxValue) {
        if (desc == null) return null;
        Pattern p = Pattern.compile("\\(\\s*([0-9]+(?:\\.[0-9]+)?)\\s*\\+\\s*" + maxKindRegex + "\\s*의\\s*([0-9]+(?:\\.[0-9]+)?)%\\s*\\)");
        Matcher m = p.matcher(desc);
        if (!m.find()) return null;
        double base = parseDoubleSafe(m.group(1));
        double pct = parseDoubleSafe(m.group(2));
        return base + maxValue * (pct / 100.0);
    }

    private String parseTagValue(String desc, String tag) {
        if (desc == null || tag == null) return null;
        Pattern p = Pattern.compile("\\[" + Pattern.quote(tag) + ":([^\\]]+)]");
        Matcher m = p.matcher(desc);
        if (!m.find()) return null;
        return safe(m.group(1)).trim();
    }

    private double parseDoubleSafe(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
