package desia.progress;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.*;

/*
 * 챕터/몬스터풀/보스/스토리 키를 외부 리소스(chapters.json)에서 로딩.
 * - 개발자가 chapters.json만 수정하면 챕터 구성 변경 가능
 */
public class ChapterRepository {

    private final List<ChapterConfig> chapters;
    private final Map<Integer, ChapterConfig> byId;

    public ChapterRepository() {
        this.chapters = loadChapters();
        Map<Integer, ChapterConfig> map = new HashMap<>();
        for (ChapterConfig c : chapters) map.put(c.getId(), c);
        this.byId = Collections.unmodifiableMap(map);
    }

    public ChapterConfig get(int chapterId) {
        ChapterConfig cfg = byId.get(chapterId);
        if (cfg == null) throw new IllegalArgumentException("알 수 없는 챕터 아이디: " + chapterId);
        return cfg;
    }

    public int maxChapterId() {
        return chapters.stream().mapToInt(ChapterConfig::getId).max().orElse(1);
    }

    private List<ChapterConfig> loadChapters() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("chapters.json")) {
            if (in == null)
                throw new RuntimeException("chapters.json 리소스가 발견되지 않음");

            ObjectMapper om = new ObjectMapper();
            return om.readValue(in, new TypeReference<List<ChapterConfig>>() {});
        } catch (Exception e) {
            throw new RuntimeException("chapters.json 로딩 실패: " + e.getMessage(), e);
        }
    }
}
