package desia.item;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * equipment.json 의 장비 1개 정의.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@ToString
public class EquipmentDef {

    // equipment.json의 key(이름)과 중복될 수 있지만, 편의상 name도 둔다(없으면 key로 채움).
    private String name;

    // 예: 투구/흉갑/각반/부츠/망토/반지/한손 무기/양손 무기/방패
    private String slot;

    private String rarity;

    // 상점 가격(없으면 0). equipment.json 에서 "price" 로 들어온다.
    // 일부 장비는 가격이 없을 수 있으므로 ShopService에서 rarity/스탯 기반으로 보정한다.
    @Builder.Default
    private int price = 0;

    // 무기일 때만: ONE_HAND / TWO_HAND
    @JsonProperty("weapon_hand")
    private String weaponHand;

    private String description;

    @Builder.Default
    private Map<String, Integer> stats = new LinkedHashMap<>();

    // 세트 이름(예: 미스릴/가죽/강철/드래곤). 없으면 null.
    @JsonProperty("set_name")
    private String setName;

    public int stat(String key) {
        if (key == null || stats == null) return 0;
        return stats.getOrDefault(key, 0);
    }
}
