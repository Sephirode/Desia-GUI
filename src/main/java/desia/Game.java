package desia;

import desia.character.Player;
import desia.io.Io;
import desia.loader.DataLoader;
import desia.loader.GameData;
import desia.loader.GameLoad;
import desia.loader.Ranking;
import desia.progress.GameSession;
import desia.progress.CampaignEngine;
import desia.progress.ChapterRepository;
import desia.story.StoryRepository;
import desia.story.StoryService;
import java.util.List;

public class Game {

    // 공용 객체: 한 번만 만들어서 계속 사용
    private final Io io;
    private final DataLoader loader;
    private final GameData data;

    private final GameLoad gl;
    private final Ranking rk;

    // 진행/스토리
    private final ChapterRepository chapterRepo;
    private final StoryService storyService;

    // 게임을 종료하기 전까지, 게임 진행상황은 Game 클래스가 들고 있는다.
    private GameSession currentSession = null;
    public Game(Io io) {
        this.io = io;
        this.loader = new DataLoader();
        this.data = loader.loadAll();
        this.gl = new GameLoad();
        this.rk = new Ranking();
        this.chapterRepo = new ChapterRepository();
        this.storyService = new StoryService(io, new StoryRepository());
    }

    // 게임 시작. 메인함수의 첫 시작점.
    public void start(){
        while(true) {
            io.onMainMenu();
            int input = io.choose("[메인 메뉴]", List.of("새 게임", "계속하기", "불러오기", "랭킹", "게임 종료"));
            // arrow switch문은 내부적으로 break가 강제되어 있는 형태이다. break문이 필요 없다.
            switch (input) {
                case 1 -> newGame();
                case 2 -> continueGame();
                case 3 -> {
                    GameSession loaded = gl.gameLoad(io, data, chapterRepo);
                    if (loaded != null){
                        currentSession = loaded;
                        new CampaignEngine(io, storyService).run(currentSession);
                    }
                }
                case 4 -> rk.printRanking();
                case 5 -> { if (io.confirmExit()) return; }
            }
        }
    }

    // 새 게임 시작하는 메소드.
    // ★★★★★로드된 데이터 -> 세션으로의 이동 코드는 여기에 있다.★★★★★
    public void newGame() {
        // 게임이 진행 중인 상태에서 새로운 게임을 시작할 경우, 플레이어에게 체크 요구
        if (currentSession != null) {
            System.out.println("진행 중인 게임이 있습니다. 새 게임을 시작하시겠습니까?");
            if (!io.confirm(">>>", "예", "아니오")) return;
        }

        final List<Player> playables;
        try {
            // DataLoad 클래스의 loadPlayables함수가 리턴한 playables 객체 리스트를, 이곳에 있는 객체 리스트에 배당해줌.
            playables = data.playables();
        } catch (Exception e) {
            System.out.println("플레이어블 로딩 실패: " + e.getMessage());
            return;
        }

        int selectedIndex = selectPlayable(playables); // -1이면 뒤로가기
        if (selectedIndex == -1)
            return;

        Player chosen = playables.get(selectedIndex);

        // 닉네임 입력(직업classes와 분리)
        String nickname = askPlayerNickname();

        // ★★★★★세션 생성. 여기가 바로 DataLoader 클래스에서 로드한 json 데이터를 GameSesseion 클래스로 넘겨주는 구간이다.
        GameSession session;
        try {
            session = GameSession.newSession(
                    chosen,
                    data.enemies(),
                    data.consumables(),
                    data.skills(),
                    data.equipments(),
                    data.equipmentSets(),
                    chapterRepo,
                    nickname
            );
        } catch (Exception e) {
            System.out.println("게임 데이터 로딩 실패: " + e.getMessage());
            return;
        }

        // 시작 스토리(개발자가 story.json에서 수정)
        currentSession = session;
        storyService.printStory("game.start");
        // 캠페인 실행(챕터/액트/상점/스토리/전투)
        /* 이 코드는 원래 다음 두 줄의 코드를 압축한 것이다.
         * CampaignEngine engine = new CampaignEngine(io, storyService);
         * engine.run(session);
         * 즉, 생성자 호출과 객체 생성을 한꺼번에 하고(new CampaignEngine(io, storyService)),
         * 실행(.run())까지 동시에 한 것이다. */
        new CampaignEngine(io, storyService).run(session);
    }
    //
    private void continueGame(){
        if(currentSession == null){
            System.out.println("진행 중인 게임이 없습니다.");
            return;
        }
        new CampaignEngine(io, storyService).run(currentSession);
    }

    // 플레이어블 캐릭터 선택하는 메소드
    private int selectPlayable(List<Player> playables) {
        while (true) {
            System.out.println("플레이할 캐릭터를 선택하세요.");
            loader.printAllPlayables(playables);
            java.util.ArrayList<String> labels = new java.util.ArrayList<>();
            for (Player p : playables) labels.add(p.getClasses());
            int pick = io.chooseAllowCancel("[캐릭터 선택]", labels, "뒤로");
            if (pick == 0) return -1;
            int idx = pick - 1;
            if (idx >= 0 && idx < playables.size()) return idx;
        }
    }

    // 플레이어 이름 정하는 메소드
    private String askPlayerNickname() {
        System.out.print("플레이어 이름을 정하세요: ");
        return io.readNonEmptyString(">>>",20);
    }
}
