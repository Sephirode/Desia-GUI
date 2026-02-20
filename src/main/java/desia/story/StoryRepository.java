package desia.story;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * story.json 로더.
 *
 * story.json은 두 가지 형태를 지원한다.
 * 1) 기존 문자열(호환):
 *    { "c1.story.2": "문구...", ... }
 * 2) 선택지/이벤트 노드:
 *    {
 *      "c1.story.2": {
 *        "text": "문구...",
 *        "choices": [
 *          {"label":"다가가본다", "effects":[{"type":"BATTLE"}]},
 *          {"label":"가던 길 간다", "effects":[{"type":"STAT", "permanent":true, "stats":{"max_hp":5}}]}
 *        ]
 *      }
 *    }
 */
public class StoryRepository {

    private final Map<String, JsonNode> story;

    public StoryRepository() {
        this.story = load();
    }

    public JsonNode getNode(String key) {
        if (key == null) return null;
        return story.get(key);
    }

    /**
     * key가 문자열이면 그대로 반환.
     * key가 객체 노드이면 text를 반환.
     */
    public String getText(String key) {
        JsonNode n = getNode(key);
        if (n == null || n.isNull()) return null;
        if (n.isTextual()) return n.asText();
        if (n.isObject()) {
            JsonNode t = n.get("text");
            if (t != null && t.isTextual()) return t.asText();
        }
        return null;
    }

    public Map<String, JsonNode> raw() {
        return Collections.unmodifiableMap(story);
    }

    private Map<String, JsonNode> load() {
        try (InputStream in = getClass().getResourceAsStream("/story.json")) {
            if (in == null) {
                System.out.println("story.json 로드 실패: 리소스를 찾을 수 없음");
                return Collections.emptyMap();
            }
            ObjectMapper om = new ObjectMapper();
            JsonNode root = om.readTree(in);
            if (root == null || !root.isObject()) return Collections.emptyMap();

            Map<String, JsonNode> out = new HashMap<>();
            root.fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue()));
            return out;
        } catch (Exception e) {
            System.out.println("story.json 로드 실패: " + e.getMessage());
            return Collections.emptyMap();
        }
    }
}
