package desia.progress;

import desia.battle.BattleEngine;
import desia.equipment.EquipmentDropService;
import desia.equipment.EquipmentService;
import desia.inventory.InventoryService;
import desia.io.Io;
import desia.loader.SaveService;
import desia.shop.ShopService;
import desia.story.StoryService;
import desia.ui.UiText;
import java.util.List;

/**
 * "게임 루프" 전용 엔진.
 * - 챕터(1~7), 액트(1~12)
 * - act 12는 보스전 확정
 * - act 1~11은 BATTLE/SHOP/STORY 중 하나 랜덤
 * - 스토리 문구는 story.json에서 수정 가능
 */
public class CampaignEngine {
    private final Io io;
    private final StoryService story;
    private final BattleEngine battle;
    private final ShopService shop;
    private final InventoryService inv;
    private final EquipmentService equip;
    private final EquipmentDropService drops;
    private final SaveService save;

    public CampaignEngine(Io io, StoryService story) {
        this.io = io;
        this.story = story;
        /* battle, shop, inv는 CampaignEngine 클래스에 선언된 필드다(매개변수가 아님).
         * 생성자에서 new로 객체를 생성해 해당 필드에 대입함으로써,
         * CampaignEngine이 BattleEngine, ShopService, InventoryService 객체를 소유하게 된다.
         * 그 결과 CampaignEngine 내부에서 이 객체들의 메서드를 호출할 수 있다. */
        this.battle = new BattleEngine(io);
        this.shop = new ShopService(io);
        this.inv = new InventoryService(io);
        this.equip = new EquipmentService(io);
        this.drops = new EquipmentDropService(io);
        this.save = new SaveService(io);
    }

    // 게임의 메인 메뉴. 메인 루프이다. session 변수가 현재 상태에 대한 값(챕터 값, )들을 전달해준다.
    public void run(GameSession session) {
        while (!session.isFinalChapterCleared()) {
            ChapterConfig cfg = session.chapterConfig();
            if (cfg == null) {
                System.out.println("chapters.json 설정이 비정상입니다. chapter=" + session.getChapter());
                return;
            }
            printChapterActHeader(session, cfg);

            // 허브 메뉴(전투 밖)
            int cmd = io.choose("[메뉴]", List.of("진행", "스테이터스", "인벤토리", "장비", "저장", "메인메뉴"));
            if (cmd == 2) {
                printStatus(session);
                continue;
            }
            if (cmd == 3) {
                inv.open(session);
                continue;
            }
            if (cmd == 4) {
                equip.open(session);
                continue;
            }
            if (cmd == 5) {
                save.saveWithMenu(session);
                continue;
            }
            if (cmd == 6){
                return;
            }

            // 진행(= act 수행)
            boolean ok = resolveAct(session, cfg);
            if (!ok) return; // 게임 오버
            // 다음 act/chapter
            advance(session);
        }

        story.printStory("game.clear");
    }

    private void printChapterActHeader(GameSession session, ChapterConfig cfg) {
        UiText.clear();
        // GUI can swap chapter background when chapter changes.
        io.onChapterChanged(session.getChapter());
        // HUD (chapter/act + player HP bar)
        io.onHubHud(
                session.getChapter(),
                session.getAct(),
                session.getPlayerName(),
                session.getLevel(),
                session.getHp(),
                session.getMaxHp()
        );
        UiText.separatorX(30);
        System.out.println("CHAPTER " + cfg.getId() + " - " + cfg.getName());
        System.out.println("ACT " + session.getAct() + "/12");
        UiText.separatorX(30);

        System.out.println(session.getPlayerName()+"\tLv. "+session.getLevel());
        System.out.println("HP: " + Math.round(session.getHp()) + "/" + Math.round(session.getMaxHp()));
        System.out.println("MP: " + Math.round(session.getMp()) + "/" + Math.round(session.getMaxMp()));
        if (Math.round(session.getShield()) > 0 || Math.round(session.getEquipBaseShield()) > 0)
            System.out.println("SHD: " + Math.round(session.getShield()));
    }

    private void printStatus(GameSession session) {
        var p = session.getPlayerBase();
        UiText.clear();
        UiText.heading("[스테이터스]",1);
        System.out.println("이름: " + session.getPlayerName());
        System.out.println("직업: " + p.getClasses());
        System.out.println("레벨: " + session.getLevel());
        System.out.println("Exp(경험치): " + Math.round(session.getExp()) + "/" + Math.round(session.expToNextLevel()));
        System.out.println("HP: " + Math.round(session.getHp()) + "/" + Math.round(session.getMaxHp()));
        System.out.println("MP: " + Math.round(session.getMp()) + "/" + Math.round(session.getMaxMp()));
        if (Math.round(session.getShield()) > 0 || Math.round(session.getEquipBaseShield()) > 0)
            System.out.println("SHD: " + Math.round(session.getShield()));
        System.out.println("공격력: " + Math.round(session.getAtk()) + "  방어력: " + Math.round(session.getDef()));
        System.out.println("주문력: " + Math.round(session.getMagic()) + "  마법 저항력: " + Math.round(session.getMdef()));
        System.out.println("스피드: " + Math.round(session.getSpd()));
        System.out.println("소지금: " + Math.round(session.getGold())+"골드");
        io.anythingToContinue();
    }

    // 현재 챕터와 챕터의 몬스터 풀에 대한 정보를 수신함
    private boolean resolveAct(GameSession session, ChapterConfig cfg) {
        int act = session.getAct();
        // 마지막(12번째) 액트 상태일 경우, 최종보스와 전투 시작
        if (act == 12) {
            story.printStory("chapter." + cfg.getId() + ".boss");
            return doBattle(session, cfg.getBoss(), true);
        }

        // 챕터당 상점은 정확히 1번만 등장
        if (!session.isMerchantDoneThisChapter()) {
            // 로드 등으로 act가 앞질러진 경우: 지금 act에서 강제 소환
            if (act > session.getMerchantActThisChapter() && act <= 11) {
                session.setMerchantSchedule(act, false);
            }
            if (act == session.getMerchantActThisChapter()) {
                story.printStory("chapter." + cfg.getId() + ".shop");
                shop.open(session);
                session.markMerchantDone();
                return true;
            }
        }

        //
        ActType type = rollActType(session, cfg);
        switch (type) {
            case STORY:
                String key = rollStoryKey(session, cfg);
                if (key == null) {
                    story.printStory("story.fallback");
                    return true;
                }

                // story.json이 문자열이면 기존처럼 출력만 하고 끝.
                // 객체(선택지/이벤트)면 StoryService가 선택지를 진행하고,
                // 전투가 트리거되면 BattleRequest로 되돌려준다.
                var action = story.play(session, cfg, key, drops);
                if (action != null && action.hasBattle()) {
                    var br = action.battleRequest();
                    return doBattle(session, br.enemyName(), br.boss());
                }
                return true;
            case BATTLE:
            default:
                story.printStory("chapter." + cfg.getId() + ".battle");
                String enemyName = rollEnemy(session, cfg);
                return doBattle(session, enemyName, false);
        }
    }

    // 전투 함수 fight에 정보를 넘겨주는 메소드
    private boolean doBattle(GameSession session, String enemyName, boolean isBoss) {
        try {
            var enemy = session.spawnEnemy(enemyName, isBoss);
            var outcome = battle.fight(session, enemy);

            // 1) 패배
            if (outcome == desia.battle.BattleOutcome.LOSE) {
                return false;
            }

            // 2) 도망
            if (outcome == desia.battle.BattleOutcome.ESCAPE) {
                return true; // 진행은 계속, 보상 없음
            }

            // 3) 승리 (WIN)
            int enemyLv = enemy.getLevel();

            double expGain = 20 + enemyLv * 10;
            double goldGain = 20 + enemyLv * 5;

            if (isBoss) {
                expGain *= 2.0;
                goldGain *= 2.0;
            }

            session.gainExp(expGain);
            session.addGold(goldGain);

            System.out.println("\n획득 EXP: " + Math.round(expGain)
                    + " / 획득 골드: " + Math.round(goldGain));
            System.out.println("현재 레벨: " + session.getLevel()
                    + " (다음 레벨까지 EXP: "
                    + Math.round(session.expToNextLevel() - session.getExp()) + ")");

            // 전투 승리 보상: 장비 랜덤 드랍(3개 중 1개 선택)
            drops.onBattleWin(session, enemy);
            io.anythingToContinue();

            return true;

        } catch (Exception e) {
            System.out.println("전투 시작 실패: " + e.getMessage());
            return true; // 루프를 막지 않게 진행
        }
    }


    // 액트에서 어떤 이벤트를 불러올지 랜덤으로 정하는 메소드. 보통 전투가 나온다.
    private ActType rollActType(GameSession session, ChapterConfig cfg) {
        // 상점은 resolveAct에서 "챕터당 1회"로 따로 처리한다.
        // 여기서는 전투/스토리만 굴린다.
        int r = session.rng().nextInt(100);
        if (r < 80) return ActType.BATTLE;
        // ChapterConfig에서 이미 선언해둔 리스트. 이 리스트(ChapterConfig타입)는 int id, String name 필드를 가짐.
        List<String> keys = cfg.getStoryKeys();
        // 스토리 키가 없으면 전투
        if (keys == null || keys.isEmpty()) return ActType.BATTLE;
        return ActType.STORY;
    }

    private String rollEnemy(GameSession session, ChapterConfig cfg) {
        List<String> pool = cfg.getEnemyPool();
        if (pool == null || pool.isEmpty()) {
            throw new IllegalStateException("enemyPool 비어있음. chapters.json 확인 필요");
        }
        return pool.get(session.rng().nextInt(pool.size()));
    }

    private String rollStoryKey(GameSession session, ChapterConfig cfg) {
        List<String> keys = cfg.getStoryKeys();
        if (keys == null || keys.isEmpty()) return null;
        return keys.get(session.rng().nextInt(keys.size()));
    }

    private void advance(GameSession session) {
        int act = session.getAct();
        if (act < 12) {
            session.setAct(act + 1);
            return;
        }

        // 챕터 클리어
        story.printStory("chapter.clear");
        session.setChapter(session.getChapter() + 1);
        session.setAct(1);
        session.resetMerchantForNewChapter();
    }
}
