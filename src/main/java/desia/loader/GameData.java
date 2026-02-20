package desia.loader;

import desia.character.Enemy;
import desia.character.Player;
import desia.item.Consumables;
import desia.item.EquipmentDef;
import desia.item.EquipmentSetDef;
import desia.skill.SkillDef;

import java.util.List;
import java.util.Map;

public final class GameData {
    private final List<Player> playables;
    private final Map<String, Enemy> enemies;
    private final Map<String, Consumables> consumables;
    private final Map<String, SkillDef> skills;

    private final Map<String, EquipmentDef> equipments;
    private final Map<String, EquipmentSetDef> equipmentSets;

    public GameData(
            List<Player> playables,
            Map<String, Enemy> enemies,
            Map<String, Consumables> consumables,
            Map<String, SkillDef> skills,
            Map<String, EquipmentDef> equipments,
            Map<String, EquipmentSetDef> equipmentSets
    ) {
        this.playables = List.copyOf(playables);
        this.enemies = Map.copyOf(enemies);
        this.consumables = Map.copyOf(consumables);
        this.skills = Map.copyOf(skills);
        this.equipments = Map.copyOf(equipments);
        this.equipmentSets = Map.copyOf(equipmentSets);
    }

    public List<Player> playables() { return playables; }
    public Map<String, Enemy> enemies() { return enemies; }
    public Map<String, Consumables> consumables() { return consumables; }
    public Map<String, SkillDef> skills() { return skills; }

    public Map<String, EquipmentDef> equipments() { return equipments; }
    public Map<String, EquipmentSetDef> equipmentSets() { return equipmentSets; }
}
