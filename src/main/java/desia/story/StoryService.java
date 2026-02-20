package desia.story;

import com.fasterxml.jackson.databind.JsonNode;
import desia.equipment.EquipmentDropService;
import desia.io.Io;
import desia.progress.ChapterConfig;
import desia.progress.GameSession;
import desia.ui.UiText;
import java.util.*;

public class StoryService {

    private final Io io;
    private final StoryRepository repo;

    public StoryService(Io io, StoryRepository repo) {
        this.io = io;
        this.repo = repo;
    }

    /**
     * 기존 호환: 문자열 스토리 출력.
     * (선택지 노드도 text만 출력하고 종료)
     */
    public void printStory(String key) {
        UiText.clear();
        UiText.heading(repo.getText(key), 1);
        io.anythingToContinue();
    }

    /**
     * 선택지/이벤트 스토리 노드 실행.
     * - story.json 값이 문자열이면 기존 출력만 수행한다.
     * - 객체면 text + choices를 보여주고 선택에 따른 효과를 적용한다.
     * - 전투가 필요하면 BattleRequest로 반환하고, 실제 전투는 CampaignEngine이 수행한다.
     */
    public StoryAction play(GameSession session, ChapterConfig cfg, String key, EquipmentDropService drops) {
        JsonNode node = repo.getNode(key);
        if (node == null || node.isTextual()) {
            printStory(key);
            return StoryAction.none();
        }

        if (!node.isObject()) {
            printStory(key);
            return StoryAction.none();
        }

        // (1) 본문 출력
        UiText.clear();
        UiText.heading(repo.getText(key), 1);

        JsonNode choices = node.get("choices");
        if (choices == null || !choices.isArray() || choices.size() == 0) {
            io.anythingToContinue();
            return StoryAction.none();
        }

        // (2) 선택지 출력 + 입력
        int n = choices.size();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            JsonNode c = choices.get(i);
            String label = (c != null && c.hasNonNull("label")) ? c.get("label").asText() : "(선택지 " + (i + 1) + ")";
            labels.add(label);
        }

        int pick = io.choose("[선택]", labels);
        JsonNode chosen = choices.get(pick - 1);
        if (chosen == null || !chosen.isObject()) {
            io.anythingToContinue();
            return StoryAction.none();
        }

        // (3) 효과 적용
        BattleRequest battle = null;
        JsonNode effects = chosen.get("effects");
        if (effects != null && effects.isArray()) {
            for (JsonNode eff : effects) {
                if (eff == null || !eff.isObject()) continue;
                String type = text(eff, "type");
                if (type == null) continue;
                switch (type) {
                    case "TEXT" -> {
                        String t = text(eff, "text");
                        if (t != null && !t.isBlank()) System.out.println("\n" + t);
                    }
                    case "GOLD" -> {
                        int amt = intVal(eff, "amount", 0);
                        if (amt != 0) {
                            session.addGold(amt);
                            System.out.println("\n골드 " + signed(amt) + " 획득");
                        }
                    }
                    case "STAT" -> {
                        boolean permanent = boolVal(eff, "permanent", true);
                        JsonNode stats = eff.get("stats");
                        if (stats != null && stats.isObject()) {
                            applyStats(session, stats, permanent);
                        }
                    }
                    case "CONSUMABLE" -> {
                        String name = text(eff, "name");
                        int cnt = intVal(eff, "count", 1);
                        if (name != null && cnt > 0) {
                            session.addItem(name, cnt);
                            System.out.println("\n아이템 획득: " + name + " x" + cnt);
                        }
                    }
                    case "EQUIPMENT" -> {
                        String name = text(eff, "name");
                        int cnt = intVal(eff, "count", 1);
                        if (name != null && cnt > 0) {
                            session.addItem(name, cnt);
                            System.out.println("\n장비 획득: " + name + " x" + cnt);
                        }
                    }
                    case "EQUIPMENT_DROP" -> {
                        int options = intVal(eff, "options", 3);
                        String forceSet = text(eff, "force_set"); // 예: "드래곤"
                        if (drops != null) {
                            drops.offerEquipmentChoice(session, options, forceSet, "스토리 보상");
                        }
                    }
                    case "BATTLE" -> {
                        if (battle == null) {
                            String enemyName = text(eff, "enemy");
                            boolean boss = boolVal(eff, "boss", false);

                            // enemy 미지정이면 챕터 풀에서 랜덤
                            if ((enemyName == null || enemyName.isBlank()) && cfg != null) {
                                List<String> pool = cfg.getEnemyPool();
                                if (pool != null && !pool.isEmpty()) {
                                    enemyName = pool.get(session.rng().nextInt(pool.size()));
                                }
                            }
                            if (enemyName != null && !enemyName.isBlank()) {
                                battle = new BattleRequest(enemyName, boss);
                            }
                        }
                    }
                }
            }
        }

        // 전투가 있으면 즉시 전투로 넘어가도록, 대기 입력은 CampaignEngine 전투 종료 뒤에 맡긴다.
        if (battle == null) io.anythingToContinue();
        return (battle == null) ? StoryAction.none() : StoryAction.battle(battle);
    }

    private static void applyStats(GameSession session, JsonNode stats, boolean permanent) {
        // 지원 키: max_hp, max_mp, attack, defense, spell_power, magic_resist, speed
        int maxHp = intVal(stats, "max_hp", 0);
        int maxMp = intVal(stats, "max_mp", 0);
        int atk = intVal(stats, "attack", 0);
        int def = intVal(stats, "defense", 0);
        int magic = intVal(stats, "spell_power", 0);
        int mres = intVal(stats, "magic_resist", 0);
        int spd = intVal(stats, "speed", 0);

        if (!permanent) {
            // 임시: 현재치만 조정(클램프됨)
            if (maxHp != 0) session.setHp(session.getHp() + maxHp);
            if (maxMp != 0) session.setMp(session.getMp() + maxMp);
            return;
        }

        // 영구 보너스는 GameSession 내부 bonus 필드에 누적한다.
        session.addPermanentStats(maxHp, maxMp, atk, magic, def, mres, spd);

        // 출력
        List<String> parts = new ArrayList<>();
        if (maxHp != 0) parts.add("최대HP " + signed(maxHp));
        if (maxMp != 0) parts.add("최대MP " + signed(maxMp));
        if (atk != 0) parts.add("공격 " + signed(atk));
        if (def != 0) parts.add("방어 " + signed(def));
        if (magic != 0) parts.add("마력 " + signed(magic));
        if (mres != 0) parts.add("마저 " + signed(mres));
        if (spd != 0) parts.add("속도 " + signed(spd));
        if (!parts.isEmpty()) {
            System.out.println("\n능력치 변화: " + String.join(", ", parts));
        }
    }

    private static String text(JsonNode obj, String k) {
        if (obj == null) return null;
        JsonNode v = obj.get(k);
        if (v == null || v.isNull()) return null;
        if (v.isTextual()) return v.asText();
        return String.valueOf(v);
    }
    private static int intVal(JsonNode obj, String k, int def) {
        if (obj == null) return def;
        JsonNode v = obj.get(k);
        if (v == null || v.isNull()) return def;
        if (v.isNumber()) return v.asInt();
        if (v.isTextual()) {
            try { return Integer.parseInt(v.asText().trim()); } catch (Exception ignored) {}
        }
        return def;
    }
    private static boolean boolVal(JsonNode obj, String k, boolean def) {
        if (obj == null) return def;
        JsonNode v = obj.get(k);
        if (v == null || v.isNull()) return def;
        if (v.isBoolean()) return v.asBoolean();
        if (v.isTextual()) return "true".equalsIgnoreCase(v.asText().trim());
        return def;
    }
    private static String signed(int v) {
        return (v > 0 ? "+" : "") + v;
    }

    public record BattleRequest(String enemyName, boolean boss) {}

    public static final class StoryAction {
        private final BattleRequest battle;
        private StoryAction(BattleRequest battle) { this.battle = battle; }
        public static StoryAction none() { return new StoryAction(null); }
        public static StoryAction battle(BattleRequest b) { return new StoryAction(b); }
        public BattleRequest battleRequest() { return battle; }
        public boolean hasBattle() { return battle != null; }
    }
}
