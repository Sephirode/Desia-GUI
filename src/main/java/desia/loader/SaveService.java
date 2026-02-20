package desia.loader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import desia.character.Player;
import desia.io.Io;
import desia.progress.ChapterRepository;
import desia.progress.GameSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;

/*
 * 저장/불러오기 서비스.
 * - 슬롯 1~3 지원
 * - 세이브 파일은 실행 폴더(user.dir)/saves/slot{n}.json 에 저장
 */
public class SaveService {

    private static final int MAX_SLOT = 3;

    private final Io io;
    private final ObjectMapper om;

    public SaveService(Io io) {
        this.io = io;
        this.om = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // 불러오기: 로드 성공 시 GameSession 반환, 실패/취소 시 null
    public GameSession load(GameData gameData, ChapterRepository chapterRepo) {
        ensureDir();

        // 슬롯 정보 미리 출력
        printLoadSlots();
        System.out.println("-----");
        int slot = io.chooseAllowCancel("[불러오기] 슬롯을 선택하세요", List.of("슬롯 1", "슬롯 2", "슬롯 3"), "취소");
        if (slot == 0) return null;
        Path path = slotPath(slot);
        if (!Files.exists(path)) {
            System.out.println("해당 슬롯에 세이브가 없습니다.");
            io.anythingToContinue();
            return null;
        }

        final SaveData saveData;
        try {
            saveData = om.readValue(Files.readString(path), SaveData.class);
        } catch (IOException e) {
            System.out.println("세이브 파일을 읽을 수 없습니다: " + e.getMessage());
            io.anythingToContinue();
            return null;
        }

        // (1) 플레이어 정의 선택
        Player chosen = findPlayerByClass(gameData.playables(), saveData.getPlayerClass());
        if (chosen == null) {
            System.out.println("세이브의 직업을 찾을 수 없습니다: " + safe(saveData.getPlayerClass()));
            io.anythingToContinue();
            return null;
        }

        // (2) 새 세션 생성(정의 데이터 주입)
        final GameSession session;
        try {
            session = GameSession.newSession(
                    chosen,
                    gameData.enemies(),
                    gameData.consumables(),
                    gameData.skills(),
                    gameData.equipments(),
                    gameData.equipmentSets(),
                    chapterRepo,
                    safe(saveData.getPlayerName())
            );
        } catch (Exception e) {
            System.out.println("게임 데이터 로딩 실패: " + e.getMessage());
            io.anythingToContinue();
            return null;
        }

        // (3) 세이브 상태 반영
        session.applySaveData(saveData);

        System.out.println("\n불러오기 완료!");
        io.anythingToContinue();
        return session;
    }

    private void printLoadSlots() {
        for (int slot = 1; slot <= MAX_SLOT; slot++) {
            Path path = slotPath(slot);
            if (!Files.exists(path)) {
                System.out.println("슬롯 " + slot + ") (비어 있음)");
                continue;
            }
            SaveData d = readSaveDataQuiet(path);
            if (d == null) {
                System.out.println("슬롯 " + slot + ") (손상된 세이브)");
                continue;
            }
            System.out.println(
                    "슬롯 " + slot + ") "
                            + safe(d.getPlayerClass()) + " Lv. " + d.getLevel()
                            + " / " + Math.round(d.getGold()) + "골드"
                            + " / 챕터 " + d.getChapter()
                            + " / ACT " + d.getAct()
            );
        }
    }

    private SaveData readSaveDataQuiet(Path path) {
        try {
            return om.readValue(Files.readString(path), SaveData.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String safe(String s) {
        return (s == null || s.isBlank()) ? "?" : s;
    }

    private Player findPlayerByClass(List<Player> playables, String playerClass) {
        if (playerClass == null) return null;
        for (Player p : playables) {
            if (playerClass.equals(p.getClasses())) return p;
        }
        return null;
    }

    private Path saveDir() {
        return Paths.get(System.getProperty("user.dir"), "saves");
    }

    private Path slotPath(int slot) {
        return saveDir().resolve("slot" + slot + ".json");
    }

    private void ensureDir() {
        try {
            Files.createDirectories(saveDir());
        } catch (IOException ignored) {
        }
    }

    // ====== Save UI helper (Io readInt 제한 때문에 별도 처리) ======

    public void saveWithMenu(GameSession session) {
        ensureDir();

        int slot = io.chooseAllowCancel("[저장] 슬롯을 선택하세요", List.of("슬롯 1", "슬롯 2", "슬롯 3"), "취소");
        if (slot == 0) return;
        Path path = slotPath(slot);

        if (Files.exists(path)) {
            if (!io.confirm("이 슬롯에는 이미 세이브가 있습니다. 덮어쓸까요?", "예", "아니오")) return;
        }

        SaveData data = SaveData.builder()
                .version(2)
                .playerClass(session.getPlayerBase().getClasses())
                .playerName(session.getPlayerName())
                .chapter(session.getChapter())
                .act(session.getAct())
                .merchantActThisChapter(session.getMerchantActThisChapter())
                .merchantDoneThisChapter(session.isMerchantDoneThisChapter())
                .level(session.getLevel())
                .exp(session.getExp())
                .hp(session.getHp())
                .mp(session.getMp())
                .shield(session.getShield())
                .bonusMaxHp(session.getBonusMaxHp())
                .bonusMaxMp(session.getBonusMaxMp())
                .bonusAtk(session.getBonusAtk())
                .bonusMagic(session.getBonusMagic())
                .bonusDef(session.getBonusDef())
                .bonusMdef(session.getBonusMdef())
                .bonusSpd(session.getBonusSpd())
                .gold(session.getGold())
                .equipped(new LinkedHashMap<>(session.equippedView()))
                .inventory(new LinkedHashMap<>(session.inventoryView()))
                .build();

        try {
            String json = om.writerWithDefaultPrettyPrinter().writeValueAsString(data);
            Files.writeString(path, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("저장 완료: 슬롯 " + slot);
        } catch (IOException e) {
            System.out.println("저장 실패: " + e.getMessage());
        }

        io.anythingToContinue();
    }
}