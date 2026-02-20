package desia.shop;

import desia.audio.AudioService;
import desia.io.Io;
import desia.item.Consumables;
import desia.item.EquipmentDef;
import desia.item.EquipmentSetDef;
import desia.progress.GameSession;
import desia.ui.UiText;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 최소 상점: 소모품 구매/판매.
 * - 아이템 풀은 consumables.json
 * - 밸런스는 나중에 조정
 */
public class ShopService {

    private final Io io;

    public ShopService(Io io) {
        this.io = io;
    }

    public void open(GameSession session) {
        AudioService.get().playIdleForChapter(session.getChapter());
        List<ShopEntry> stock = rollStock(session, 10);

        while (true) {
            UiText.clear();
            System.out.println("\n[상점] 보유 골드: " + Math.round(session.getGold())+"골드");
            int cmd = io.choose("[상점]", List.of("구매", "판매", "나가기"));

            if (cmd == 1) {
                buyMenu(session, stock);
            } else if (cmd == 2) {
                sellMenu(session);
            } else {
                return;
            }
        }
    }

    private enum ItemType { CONSUMABLE, EQUIPMENT }

    private static final class ShopEntry {
        final ItemType type;
        final String name;
        final int price;
        final String extra;
        boolean soldOut;

        ShopEntry(ItemType type, String name, int price, String extra) {
            this.type = type;
            this.name = name;
            this.price = price;
            this.extra = extra;
            this.soldOut = false;
        }

        String label() {
            String base;
            if (extra == null || extra.isBlank()) base = name;
            else base = name + " " + extra;
            if (type == ItemType.EQUIPMENT && soldOut) return base + " (품절)";
            return base;
        }
    }

    /**
     * 상점 재고(총 size개)
     * - 초반(챕터 1~3): 소모품 7 / 장비 3
     * - 중반(챕터 4):   소모품 6 / 장비 4
     * - 후반(챕터 5+):  소모품 5 / 장비 5
     */
    private List<ShopEntry> rollStock(GameSession session, int size) {
        int chapter = session.getChapter();
        int equipCount = (chapter <= 3) ? 3 : (chapter == 4 ? 4 : 5);
        equipCount = Math.min(equipCount, size);
        int consumableCount = size - equipCount;

        List<ShopEntry> out = new ArrayList<>();
        out.addAll(rollConsumables(session, consumableCount));
        out.addAll(rollEquipments(session, equipCount));
        Collections.shuffle(out, session.rng());
        return out;
    }

    private List<ShopEntry> rollConsumables(GameSession session, int count) {
        List<Consumables> all = new ArrayList<>(session.allConsumables());
        Collections.shuffle(all, session.rng());
        return all.stream()
                .limit(count)
                .map(c -> new ShopEntry(ItemType.CONSUMABLE, c.getName(), (int) Math.round(c.getPrice()), ""))
                .collect(Collectors.toList());
    }

    private List<ShopEntry> rollEquipments(GameSession session, int count) {
        List<EquipmentDef> pool = new ArrayList<>(session.equipmentsView().values());
        // deterministic-ish ordering + rng for selection without repeats
        pool.sort(Comparator.comparing(EquipmentDef::getName, Comparator.nullsLast(String::compareTo)));
        Random rng = session.rng();

        // rarity별 풀
        List<EquipmentDef> common = filterByRarity(pool, "COMMON");
        List<EquipmentDef> uncommon = filterByRarity(pool, "UNCOMMON");
        List<EquipmentDef> rare = filterByRarity(pool, "RARE");
        List<EquipmentDef> epic = filterByRarity(pool, "EPIC");
        List<EquipmentDef> legendary = filterByRarity(pool, "LEGENDARY");

        int chapter = session.getChapter();
        // 확률(퍼센트)
        int wCommon, wUncommon, wRare, wEpic, wLegend;
        if (chapter <= 3) {
            // 초반: common~uncommon 위주 + rare 10%
            wCommon = 45; wUncommon = 45; wRare = 10; wEpic = 0; wLegend = 0;
        } else if (chapter >= 5) {
            // 후반: rare~epic 위주 + legendary 15%
            wCommon = 0; wUncommon = 0; wRare = 45; wEpic = 40; wLegend = 15;
        } else {
            // 챕터 4: 중간 가중치(완충)
            wCommon = 20; wUncommon = 20; wRare = 40; wEpic = 15; wLegend = 5;
        }

        Set<String> picked = new HashSet<>();
        List<ShopEntry> out = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            EquipmentDef chosen = null;
            // 최대 몇 번까지 시도 후, 남은 풀에서 강제 선택
            for (int tries = 0; tries < 12 && chosen == null; tries++) {
                String r = pickRarity(rng, wCommon, wUncommon, wRare, wEpic, wLegend);
                List<EquipmentDef> bucket = switch (r) {
                    case "COMMON" -> common;
                    case "UNCOMMON" -> uncommon;
                    case "RARE" -> rare;
                    case "EPIC" -> epic;
                    case "LEGENDARY" -> legendary;
                    default -> pool;
                };
                chosen = pickOneNotPicked(bucket, picked, rng);
            }
            if (chosen == null) {
                chosen = pickOneNotPicked(pool, picked, rng);
            }
            if (chosen == null) break;

            picked.add(chosen.getName());
            int price = equipmentShopPrice(chosen);
            String extra = "(장비:" + safe(chosen.getSlot()) + "/" + safe(chosen.getRarity()) + ")";
            out.add(new ShopEntry(ItemType.EQUIPMENT, chosen.getName(), price, extra));
        }
        return out;
    }

    private List<EquipmentDef> filterByRarity(List<EquipmentDef> pool, String rarity) {
        return pool.stream()
                .filter(d -> d != null && d.getRarity() != null && d.getRarity().equalsIgnoreCase(rarity))
                .collect(Collectors.toList());
    }

    private EquipmentDef pickOneNotPicked(List<EquipmentDef> list, Set<String> picked, Random rng) {
        if (list == null || list.isEmpty()) return null;
        // 최대 n번 랜덤 시도 후 선형 탐색
        int n = list.size();
        for (int t = 0; t < Math.min(8, n); t++) {
            EquipmentDef d = list.get(rng.nextInt(n));
            if (d != null && d.getName() != null && !picked.contains(d.getName())) return d;
        }
        for (EquipmentDef d : list) {
            if (d != null && d.getName() != null && !picked.contains(d.getName())) return d;
        }
        return null;
    }

    private String pickRarity(Random rng, int wCommon, int wUncommon, int wRare, int wEpic, int wLegend) {
        int sum = Math.max(0, wCommon) + Math.max(0, wUncommon) + Math.max(0, wRare) + Math.max(0, wEpic) + Math.max(0, wLegend);
        if (sum <= 0) return "COMMON";
        int r = rng.nextInt(sum);
        int acc = 0;
        acc += Math.max(0, wCommon);
        if (r < acc) return "COMMON";
        acc += Math.max(0, wUncommon);
        if (r < acc) return "UNCOMMON";
        acc += Math.max(0, wRare);
        if (r < acc) return "RARE";
        acc += Math.max(0, wEpic);
        if (r < acc) return "EPIC";
        return "LEGENDARY";
    }

    private int equipmentShopPrice(EquipmentDef def) {
        if (def == null) return 10;
        int p = def.getPrice();
        if (p > 0) return p;

        // 가격이 json에 없을 때: rarity + 스탯 합산으로 추정
        int base = switch (safe(def.getRarity()).toUpperCase()) {
            case "UNCOMMON" -> 120;
            case "RARE" -> 300;
            case "EPIC" -> 650;
            case "LEGENDARY" -> 1200;
            default -> 60; // COMMON
        };

        int sum = 0;
        Map<String, Integer> stats = def.getStats();
        if (stats != null) {
            for (int v : stats.values()) sum += Math.abs(v);
        }
        return Math.max(10, base + sum * 25);
    }

    private String safe(String s) {
        return (s == null) ? "" : s;
    }

    // 상점에서 상품 목록을 출력하고 구매를 담당하는 메소드
    private void buyMenu(GameSession session, List<ShopEntry> stock) {
        while (true) {
            UiText.clear();
            System.out.println("\n[상점] 보유 골드: " + Math.round(session.getGold())+"골드");
            System.out.println("\n[구매] 아이템을 선택하세요");
            List<String> labels = new ArrayList<>();
            for (int i = 0; i < stock.size(); i++) {
                ShopEntry e = stock.get(i);
                labels.add(e.label() + " - " + e.price + "골드");
                // 상세는 로그로 보여주고, 선택은 버튼(또는 콘솔 번호)로 받는다.
                System.out.println("- " + e.label() + " - " + e.price + "골드");

                // 플레이버/효과 출력
                String desc = itemDesc(session, e.name);
                if (desc != null && !desc.isBlank()) {
                    System.out.println("   - " + desc);
                }
                String eff = itemEffect(session, e.name);
                if (eff != null && !eff.isBlank()) {
                    System.out.println("   - 효과: " + eff);
                }
            }
            int input = io.chooseAllowCancel("[구매]", labels, "뒤로");
            if (input == 0) return;

            ShopEntry chosen = stock.get(input - 1);

            // 장비는 1개 구매하면 품절 처리
            if (chosen.type == ItemType.EQUIPMENT && chosen.soldOut) {
                System.out.println("품절된 장비입니다.");
                io.anythingToContinue();
                continue;
            }

            double price = chosen.price;
            if (session.getGold() < price) {
                System.out.println("골드가 부족합니다.");
                io.anythingToContinue();
                continue;
            }
            session.addGold(-price);
            session.addItem(chosen.name, 1);
            System.out.println("구매 완료: " + chosen.name);

            if (chosen.type == ItemType.EQUIPMENT) {
                chosen.soldOut = true;
            }

            io.anythingToContinue();
        }
    }

    private void sellMenu(GameSession session) {
        while (true) {
            UiText.clear();
            var sellable = sellableView(session);
            if (sellable.isEmpty()) {
                System.out.println("\n[판매] 인벤토리가 비었습니다.");
                io.anythingToContinue();
                return;
            }

            UiText.clear();
            System.out.println("\n[상점] 보유 골드: " + Math.round(session.getGold())+"골드");
            System.out.println("\n[판매] 아이템을 선택하세요");
            List<String> names = new ArrayList<>(sellable.keySet());
            List<String> labels = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                String name = names.get(i);
                int count = sellable.getOrDefault(name, 0);
                double unit = sellPrice(session, name);
                String tag = itemKindTag(session, name);

                String eqTag = equippedTag(session, name);
                String line = name + tag + eqTag + " x" + count + " - 개당 " + Math.round(unit) + "골드";
                labels.add(line);
                // 상세는 로그로 보여주고, 선택은 버튼(또는 콘솔 번호)로 받는다.
                System.out.println("- " + line);

                // 플레이버/효과 출력
                String desc = itemDesc(session, name);
                if (desc != null && !desc.isBlank()) {
                    System.out.println("   - " + desc);
                }
                String eff = itemEffect(session, name);
                if (eff != null && !eff.isBlank()) {
                    System.out.println("   - 효과: " + eff);
                }
            }
            int input = io.chooseAllowCancel("[판매]", labels, "뒤로");
            if (input == 0) return;

            String name = names.get(input - 1);

            // 인벤토리에서 먼저 빼고, 없으면(=장착 중) 장착 슬롯에서 제거한다.
            boolean removed = session.removeItem(name, 1);
            if (!removed) {
                removed = removeEquippedOne(session, name);
            }
            if (!removed) {
                System.out.println("판매 실패");
                io.anythingToContinue();
                continue;
            }
            double gain = sellPrice(session, name);
            session.addGold(gain);
            System.out.println("판매 완료: " + name + " (" + Math.round(gain) + "골드)");
            io.anythingToContinue();
        }
    }

    private double sellPrice(GameSession session, String itemName) {
        Consumables def = session.consumableDef(itemName);
        double base;
        if (def != null) {
            base = def.getPrice();
        } else {
            EquipmentDef eq = session.equipmentDef(itemName);
            base = (eq == null) ? 10 : equipmentShopPrice(eq);
        }
        // 판매 가격: 원가의 70%
        return Math.max(1, Math.floor(base * 0.7));
    }

    private String itemKindTag(GameSession session, String name) {
        if (session.consumableDef(name) != null) return "";
        EquipmentDef eq = session.equipmentDef(name);
        if (eq == null) return "";
        String slot = safe(eq.getSlot());
        String rar = safe(eq.getRarity());
        if (slot.isBlank() && rar.isBlank()) return "";
        return "(" + (slot.isBlank() ? "장비" : slot) + "/" + rar + ")";
    }

    private String equippedTag(GameSession session, String name) {
        if (session == null || name == null) return "";
        return session.equippedView().containsValue(name) ? " (장착 중)" : "";
    }

    /**
     * 판매 목록은 인벤토리 + 장착 장비를 합친다.
     * - 장착 장비는 개수 +1로 합산된다.
     */
    private Map<String, Integer> sellableView(GameSession session) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (session == null) return out;
        for (Map.Entry<String, Integer> e : session.inventoryView().entrySet()) {
            if (e == null) continue;
            String k = e.getKey();
            int v = (e.getValue() == null) ? 0 : e.getValue();
            if (k == null || k.isBlank() || v <= 0) continue;
            out.put(k, v);
        }
        for (String v : session.equippedView().values()) {
            if (v == null || v.isBlank()) continue;
            out.put(v, out.getOrDefault(v, 0) + 1);
        }
        return out;
    }

    /**
     * 장착 중인 동일 이름 장비 1개를 슬롯에서 제거한다(인벤으로 돌려놓지 않음).
     */
    private boolean removeEquippedOne(GameSession session, String equipName) {
        if (session == null || equipName == null) return false;
        for (Map.Entry<String, String> e : session.equippedView().entrySet()) {
            if (e == null) continue;
            if (equipName.equals(e.getValue())) {
                session.setEquippedSlot(e.getKey(), null);
                return true;
            }
        }
        return false;
    }

    // ===== 플레이버/효과 출력 =====

    private String itemDesc(GameSession session, String name) {
        if (name == null || session == null) return "";
        Consumables c = session.consumableDef(name);
        if (c != null) return c.getDescription();
        EquipmentDef eq = session.equipmentDef(name);
        if (eq != null) return eq.getDescription();
        return "";
    }

    private String itemEffect(GameSession session, String name) {
        if (name == null || session == null) return "";
        Consumables c = session.consumableDef(name);
        if (c != null) return describeConsumable(session, c);
        EquipmentDef eq = session.equipmentDef(name);
        if (eq != null) return describeEquipment(session, eq);
        return "";
    }

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
            case "RESTORE_FULL" -> parts.add("HP/MP 완전 회복");
            case "ADD_SHIELD" -> {
                long amount = Math.round(computeBasePlusPercent(safe(c.getDescription()), "최대\\s*체력", session.getMaxHp(), c.getPrice()));
                parts.add("실드 " + signed(amount) + " (전투)");
            }
            case "PERM_STATS" -> addPermStatParts(parts, c, true);
            case "MIXED" -> {
                long hp = Math.round(computeBasePlusPercent(safe(c.getDescription()), "최대\\s*체력", session.getMaxHp(), c.getHp()));
                long mp = Math.round(computeBasePlusPercent(safe(c.getDescription()), "최대\\s*마나", session.getMaxMp(), c.getMp()));
                if (hp != 0) parts.add("HP " + signed(hp));
                if (mp != 0) parts.add("MP " + signed(mp));
                addPermStatParts(parts, c, true);
            }
            case "LEVEL_UP" -> {
                int inc = Math.max(1, c.getLevel());
                parts.add("레벨 +" + inc);
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
            case "ESCAPE" -> parts.add("도주 (전투, 보스 제외)");
            case "CAST_SKILL" -> {
                String skillName = parseTagValue(safe(c.getDescription()), "CAST_SKILL");
                if (skillName != null && !skillName.isBlank()) parts.add("스킬 시전: " + skillName.trim());
                else parts.add("스킬 시전");
            }
            default -> {
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

        if (!c.isUseInBattle() && !type.isBlank()) parts.add("전투 사용 불가");
        if (!c.isUseOutOfBattle() && !type.isBlank()) parts.add("전투 밖 사용 불가");

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

        String setName = safe(e.getSetName());
        if (!setName.isBlank()) {
            String setInfo = "세트=" + setName;
            EquipmentSetDef set = session.equipmentSetsView().get(setName);
            if (set != null && set.getPieces() != null && !set.getPieces().isEmpty()) {
                int cnt = 0;
                for (String piece : set.getPieces()) {
                    if (piece == null) continue;
                    if (session.equippedView().containsValue(piece)) cnt++;
                }
                setInfo += " (" + cnt + "/" + set.getPieces().size() + ")";
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

    private double computeBasePlusPercent(String desc, String maxKindRegex, double maxValue, double fallback) {
        Double v = parseBasePlusPercent(desc, maxKindRegex, maxValue);
        if (v != null) return v;
        return fallback;
    }

    private Double parseBasePlusPercent(String desc, String maxKindRegex, double maxValue) {
        if (desc == null) return null;
        Pattern p = Pattern.compile("\\(\\s*([0-9]+(?:\\.[0-9]+)?)\\s*\\+\\s*" + maxKindRegex + "\\s*\\uC758\\s*([0-9]+(?:\\.[0-9]+)?)%\\s*\\)");
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
}
