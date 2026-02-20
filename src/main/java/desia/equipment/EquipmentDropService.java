package desia.equipment;

import desia.character.EnemyInstance;
import desia.io.Io;
import desia.item.EquipmentDef;
import desia.progress.GameSession;

import java.util.*;

/**
 * 전투/스토리 보상으로 장비를 랜덤 드랍하고, 3개 중 1개를 고르게 한다.
 *
 * 요구사항:
 * - 전투 승리마다 3개 선택지 제공(1개 획득)
 * - 챕터가 진행될수록 고등급 확률 증가
 * - 챕터 4에서 드래곤 장비 드랍 확률 크게 증가
 * - 드래곤 적 처치 시 드래곤 장비만 드랍(선택지 3개 모두)
 */
public class EquipmentDropService {

    private final Io io;

    public EquipmentDropService(Io io) {
        this.io = io;
    }

    public void onBattleWin(GameSession session, EnemyInstance enemy) {
        if (session == null) return;

        int options = 3;
        String forceSet = null;
        boolean enemyIsDragon = (enemy != null) && isDragonEnemy(enemy.getName());
        if (enemyIsDragon) {
            forceSet = "드래곤";
        }

        offerEquipmentChoice(session, options, forceSet, "전투 보상");
    }

    /**
     * 장비 선택지 제공.
     * @param forceSetName null이면 일반 드랍. 예: "드래곤"이면 해당 세트에서만 뽑는다.
     */
    public void offerEquipmentChoice(GameSession session, int optionCount, String forceSetName, String reason) {
        if (session == null) return;
        int n = Math.max(1, optionCount);

        List<EquipmentDef> picks = rollOptions(session, n, forceSetName);
        if (picks.isEmpty()) return;

        System.out.println("\n[장비 드랍] " + (reason == null ? "" : reason));
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < picks.size(); i++) {
            EquipmentDef d = picks.get(i);
            labels.add(d.getName());
            // 상세는 로그로 보여주고, 선택은 버튼(또는 콘솔 번호)로 받는다.
            System.out.println("- " + formatLine(d));
            String eff = formatStats(d);
            if (!eff.isBlank()) System.out.println("   - " + eff);
            if (d.getDescription() != null && !d.getDescription().isBlank()) {
                System.out.println("   - " + d.getDescription());
            }
        }

        int chosen = io.choose("[장비 선택]", labels);
        EquipmentDef got = picks.get(chosen - 1);
        session.addItem(got.getName(), 1);
        System.out.println("\n획득: " + got.getName());
    }

    private List<EquipmentDef> rollOptions(GameSession session, int n, String forceSetName) {
        Map<String, EquipmentDef> all = session.equipmentsView();
        if (all == null || all.isEmpty()) return List.of();

        // 후보 목록 구성
        List<EquipmentDef> pool = new ArrayList<>();
        for (EquipmentDef d : all.values()) {
            if (d == null) continue;
            if (forceSetName != null && !forceSetName.isBlank()) {
                if (!forceSetName.equalsIgnoreCase(safe(d.getSetName()))) continue;
            }
            pool.add(d);
        }
        if (pool.isEmpty()) return List.of();

        // 중복 방지: 이름 기준
        Set<String> used = new HashSet<>();
        List<EquipmentDef> out = new ArrayList<>();
        int guard = 0;
        while (out.size() < n && guard++ < 200) {
            EquipmentDef d = rollOne(session, pool, forceSetName);
            if (d == null) break;
            if (!used.add(d.getName())) continue;
            out.add(d);
        }

        // 드랍이 너무 부족하면 남은 칸은 그냥 랜덤으로 채운다(중복은 허용하지 않음)
        if (out.size() < n) {
            Collections.shuffle(pool, session.rng());
            for (EquipmentDef d : pool) {
                if (out.size() >= n) break;
                if (d == null) continue;
                if (!used.add(d.getName())) continue;
                out.add(d);
            }
        }

        return out;
    }

    private EquipmentDef rollOne(GameSession session, List<EquipmentDef> pool, String forceSetName) {
        Random rng = session.rng();

        // 챕터 4 강화(드래곤 세트 강제인 경우는 별도 처리)
        if ((forceSetName != null && !forceSetName.isBlank()) && "드래곤".equalsIgnoreCase(forceSetName)) {
            // 이미 드래곤 세트 풀로 들어온 상태
            return pool.get(rng.nextInt(pool.size()));
        }

        // 일반: 희귀도 먼저 롤링, 해당 희귀도에서 아이템 선택
        String rar = rollRarity(rng, session.getChapter());

        // 챕터 4에서 드래곤 세트 확률 증가(드래곤 적 전투가 아니어도)
        boolean preferDragon = (session.getChapter() == 4);
        if (preferDragon && rng.nextDouble() < 0.60) {
            // 35%는 드래곤 세트에서 뽑아본다(없으면 일반 진행)
            EquipmentDef d = pickFromSetAndRarity(pool, "드래곤", rar, rng);
            if (d != null) return d;
        }

        EquipmentDef d = pickFromRarity(pool, rar, rng);
        if (d != null) return d;

        // fallback: 아무거나
        return pool.get(rng.nextInt(pool.size()));
    }

    private static EquipmentDef pickFromRarity(List<EquipmentDef> pool, String rarity, Random rng) {
        if (rarity == null) return null;
        List<EquipmentDef> list = new ArrayList<>();
        for (EquipmentDef d : pool) {
            if (d == null) continue;
            if (rarity.equalsIgnoreCase(safe(d.getRarity()))) list.add(d);
        }
        if (list.isEmpty()) return null;
        return list.get(rng.nextInt(list.size()));
    }

    private static EquipmentDef pickFromSetAndRarity(List<EquipmentDef> pool, String setName, String rarity, Random rng) {
        List<EquipmentDef> list = new ArrayList<>();
        for (EquipmentDef d : pool) {
            if (d == null) continue;
            if (!setName.equalsIgnoreCase(safe(d.getSetName()))) continue;
            if (rarity != null && !rarity.isBlank()) {
                if (!rarity.equalsIgnoreCase(safe(d.getRarity()))) continue;
            }
            list.add(d);
        }
        if (list.isEmpty()) return null;
        return list.get(rng.nextInt(list.size()));
    }

    /**
     * 챕터 기반 희귀도 롤링.
     * - 1~2: COMMON/UNCOMMON 위주
     * - 3~4: UNCOMMON/RARE 위주
     * - 5~7: RARE/EPIC 위주, LEGENDARY 소량
     */
    private static String rollRarity(Random rng, int chapter) {
        int c = Math.max(1, chapter);

        // weights 합 100
        int common;
        int uncommon;
        int rare;
        int epic;
        int legendary;

        if (c <= 2) {
            common = 45; uncommon = 40; rare = 12; epic = 3; legendary = 0;
        } else if (c <= 4) {
            common = 20; uncommon = 35; rare = 30; epic = 13; legendary = 2;
        } else if (c <= 6) {
            common = 10; uncommon = 25; rare = 35; epic = 25; legendary = 5;
        } else {
            common = 5; uncommon = 15; rare = 35; epic = 30; legendary = 15;
        }

        int roll = rng.nextInt(100) + 1;
        int acc = common;
        if (roll <= acc) return "COMMON";
        acc += uncommon;
        if (roll <= acc) return "UNCOMMON";
        acc += rare;
        if (roll <= acc) return "RARE";
        acc += epic;
        if (roll <= acc) return "EPIC";
        return "LEGENDARY";
    }

    private static boolean isDragonEnemy(String enemyName) {
        if (enemyName == null) return false;
        return enemyName.contains("드래곤") || enemyName.toLowerCase(Locale.ROOT).contains("dragon");
    }

    private static String formatLine(EquipmentDef d) {
        if (d == null) return "(null)";
        String rar = safe(d.getRarity());
        String slot = safe(d.getSlot());
        String set = safe(d.getSetName());
        StringBuilder sb = new StringBuilder();
        if (!rar.isBlank()) sb.append("[").append(rar).append("] ");
        sb.append(d.getName());
        if (!slot.isBlank()) sb.append(" (").append(slot).append(")");
        if (!set.isBlank()) sb.append(" <세트:").append(set).append(">" );
        return sb.toString();
    }

    private static String formatStats(EquipmentDef d) {
        if (d == null || d.getStats() == null || d.getStats().isEmpty()) return "";
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : d.getStats().entrySet()) {
            if (e == null) continue;
            String k = e.getKey();
            Integer v0 = e.getValue();
            int v = (v0 == null) ? 0 : v0;
            if (v == 0) continue;
            parts.add(statLabel(k) + " " + signed(v));
        }
        return String.join(", ", parts);
    }

    private static String statLabel(String key) {
        if (key == null) return "스탯";
        return switch (key) {
            case "max_hp" -> "최대HP";
            case "max_mp" -> "최대MP";
            case "attack" -> "공격";
            case "spell_power", "magic" -> "마력";
            case "defense" -> "방어";
            case "magic_resist", "mdef" -> "마저";
            case "speed" -> "속도";
            case "shield" -> "실드";
            default -> key;
        };
    }

    private static String signed(int v) {
        return (v > 0 ? "+" : "") + v;
    }

    private static String safe(String s) {
        return (s == null) ? "" : s;
    }
}
