package desia.loader;

import lombok.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 세이브 파일 1개에 해당하는 데이터 모델.
 * - JSON 직렬화/역직렬화용 DTO
 * - "정의 데이터"(Player/Enemy/Consumables)는 저장하지 않고, 로드 시 다시 JSON에서 읽는다.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@ToString
public class SaveData {
    // 파일 포맷 버전(호환성용)
    @Builder.Default
    private int version = 2;

    // 플레이어 식별
    private String playerClass;   // 예: "전사" (Player.classes)
    private String playerName;    // 닉네임

    // 진행도
    private int chapter;
    private int act;

    // 챕터당 상점(상인) 등장 스케줄
    private int merchantActThisChapter;
    private boolean merchantDoneThisChapter;

    // 성장
    private int level;
    private double exp;

    // 현재 자원
    private double hp;
    private double mp;

    private double shield;

    // 영구 스탯 보너스(소모품 등)
    private double bonusMaxHp;
    private double bonusMaxMp;
    private double bonusAtk;
    private double bonusMagic;
    private double bonusDef;
    private double bonusMdef;
    private double bonusSpd;

    // 재화
    private double gold;

    // 장착 장비(슬롯키 -> 장비 이름)
    @Builder.Default
    private Map<String, String> equipped = new LinkedHashMap<>();

    // 인벤토리(아이템 이름 -> 개수)
    @Builder.Default
    private Map<String, Integer> inventory = new LinkedHashMap<>();
}
