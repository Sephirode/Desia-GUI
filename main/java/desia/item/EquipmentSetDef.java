package desia.item;

import lombok.*;

import java.util.List;

/**
 * equipment.json 의 sets[세트명] 정의.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@ToString
public class EquipmentSetDef {

    @Builder.Default
    private List<String> pieces = List.of();

    @Builder.Default
    private List<SetBonusDef> bonuses = List.of();
}
