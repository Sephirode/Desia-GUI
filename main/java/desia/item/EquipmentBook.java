package desia.item;

import lombok.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * equipment.json 루트 래퍼.
 * {
 *   "equipment": { "가죽 투구": {...}, ... },
 *   "sets": { "미스릴": {...}, ... }
 * }
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@ToString
public class EquipmentBook {

    @Builder.Default
    private Map<String, EquipmentDef> equipment = new LinkedHashMap<>();

    @Builder.Default
    private Map<String, EquipmentSetDef> sets = new LinkedHashMap<>();
}
