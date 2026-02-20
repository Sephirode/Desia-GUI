package desia.progress;

import lombok.*;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@ToString
public class ChapterConfig {
    private int id;
    private String name;

    // 챕터 적 레벨 스케일
    private int minLevel;
    private int maxLevel;

    // 잡몹풀(이 챕터에서 act 1~11에 랜덤 등장)
    private List<String> enemyPool;

    // act 12 보스
    private String boss;

    // 여기서는 객체 리스트 선언만 해 둠. 초기화는 되어있지 않다.
    // 스토리 키(StoryRepository에서 키 -> 문구로 매핑)
    private List<String> storyKeys;
}
