package desia.item;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 세트 보너스 1개 구간(예: 2피스, 4피스).
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@ToString
public class SetBonusDef {
    private int pieces;

    @Builder.Default
    private Map<String, Integer> stats = new LinkedHashMap<>();

    @JsonProperty("special_tags")
    @Builder.Default
    private List<String> specialTags = List.of();

    public int stat(String key) {
        if (key == null || stats == null) return 0;
        return stats.getOrDefault(key, 0);
    }
}
