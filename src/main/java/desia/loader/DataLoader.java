package desia.loader;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import desia.character.Enemy;
import desia.character.Player;
import desia.item.Consumables;
import desia.item.EquipmentBook;
import desia.item.EquipmentDef;
import desia.item.EquipmentSetDef;
import desia.skill.SkillBook;
import desia.skill.SkillDef;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DataLoader {

    private final ObjectMapper om = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public GameData loadAll() {
        try {
            return new GameData(
                    loadPlayables(),
                    loadEnemyMap(),
                    loadConsumableMap(),
                    loadSkillMap(),
                    loadEquipmentMap(),
                    loadEquipmentSetMap()
            );
        } catch (Exception e) {
            throw new RuntimeException("게임 데이터 로딩 실패: " + e.getMessage(), e);
        }
    }


    public List<Enemy> loadEnemies() throws Exception {
        // json파일을 resources파일에서 찾은 뒤, 인풋스트림을 꽂는다
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("enemies.json")) {
            if (in == null)
                throw new RuntimeException("enemies 리소스가 발견되지 않음");
            // json-java간 변환기 소환!
            return om.readValue(in, new TypeReference<List<Enemy>>() {});
        }
    }

    public List<Player> loadPlayables() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("playables.json")) {
            if (in == null)
                throw new RuntimeException("playables 리소스가 발견되지 않음");

            return om.readValue(in, new TypeReference<List<Player>>() {});

        }

    }

    public List<Consumables> loadConsumables() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("consumables.json")) {
            if (in == null)
                throw new RuntimeException("consumables 리소스가 발견되지 않음");

            return om.readValue(in, new TypeReference<List<Consumables>>() {});
        }
    }

    // ====== Map 빌더 (원본 보호용 정의 데이터) ======

    public Map<String, Enemy> loadEnemyMap() throws Exception {
        List<Enemy> enemies = loadEnemies();
        Map<String, Enemy> out = new LinkedHashMap<>();
        for (Enemy e : enemies) {
            if (e == null || e.getName() == null) continue;
            out.put(e.getName(), e);
        }
        return out;
    }

    public Map<String, Consumables> loadConsumableMap() throws Exception {
        List<Consumables> consumables = loadConsumables();
        Map<String, Consumables> out = new LinkedHashMap<>();
        for (Consumables c : consumables) {
            if (c == null || c.getName() == null) continue;
            out.put(c.getName(), c);
        }
        return out;
    }

    /**
     * skills.json 로딩 (SkillBook 포맷)
     */
    public Map<String, SkillDef> loadSkillMap() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("skills.json")) {
            if (in == null) throw new RuntimeException("skills 리소스가 발견되지 않음");
            SkillBook book = om.readValue(in, SkillBook.class);
            if (book == null || book.getSkills() == null) return new LinkedHashMap<>();
            return new LinkedHashMap<>(book.getSkills());
        }
    }


    /**
     * equipment.json 로딩 (EquipmentBook 포맷)
     */
    public EquipmentBook loadEquipmentBook() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("equipment.json")) {
            if (in == null) throw new RuntimeException("equipment 리소스가 발견되지 않음");
            EquipmentBook book = om.readValue(in, EquipmentBook.class);
            if (book == null) return new EquipmentBook();
            // 장비 이름(key)을 def.name에 채워준다(편의)
            if (book.getEquipment() != null) {
                for (Map.Entry<String, EquipmentDef> e : book.getEquipment().entrySet()) {
                    if (e.getValue() != null && (e.getValue().getName() == null || e.getValue().getName().isBlank())) {
                        e.getValue().setName(e.getKey());
                    }
                }
            }
            return book;
        }
    }

    public Map<String, EquipmentDef> loadEquipmentMap() throws Exception {
        EquipmentBook book = loadEquipmentBook();
        if (book.getEquipment() == null) return new LinkedHashMap<>();
        return new LinkedHashMap<>(book.getEquipment());
    }

    public Map<String, EquipmentSetDef> loadEquipmentSetMap() throws Exception {
        EquipmentBook book = loadEquipmentBook();
        if (book.getSets() == null) return new LinkedHashMap<>();
        return new LinkedHashMap<>(book.getSets());
    }

    // (디버그용 출력 메소드들)
    // 향상된 for문을 이용해서, 생성한 리스트의 객체들을 전부 출력하는 메소드들.
    // 향상된 for문은 배열이나 리스트를 처음부터 끝까지 전부 출력한다.
    public void printAllEnemies(List<Enemy> enemies) {
        for (Enemy e : enemies)
            System.out.println(e);
    }
    public void printAllPlayables(List<Player> playables) {
        int i = 1;
        for (Player e : playables){
            System.out.println("\n"+i+") "+e.getClasses()+"\n"+e.getDescription());
            i+=1;
        }
    }
    public void printAllConsumables(List<Consumables> consumables) {
        for (Consumables e : consumables)
            System.out.println(e);
    }
}