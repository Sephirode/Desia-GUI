package desia.progress;

import desia.character.Enemy;
import desia.character.EnemyInstance;
import desia.character.Player;
import desia.item.Consumables;
import desia.item.EquipmentDef;
import desia.item.EquipmentSetDef;
import desia.item.SetBonusDef;
import desia.combat.Combatant;
import desia.status.StatusContainer;
import desia.skill.SkillDef;
import desia.skill.SkillUnlockRepository;

import java.util.*;

public class GameSession implements Combatant {

    // 랜덤함수 사용을 위해 생성
    private final Random rng = new Random();

    // 장비 슬롯 키(세이브에도 그대로 저장됨)
    public static final String SLOT_HELMET = "HELMET";
    public static final String SLOT_CHEST = "CHEST";
    public static final String SLOT_LEGS = "LEGS";
    public static final String SLOT_BOOTS = "BOOTS";
    public static final String SLOT_CLOAK = "CLOAK";
    public static final String SLOT_RING1 = "RING1";
    public static final String SLOT_RING2 = "RING2";
    public static final String SLOT_WEAPON1 = "WEAPON1";
    public static final String SLOT_WEAPON2 = "WEAPON2";


    private final Player playerBase;
    private final String playerName;
    private int level;
    private double exp = 0;

    // 현재 자원(체력, 마나)
    private double hp;
    private double mp;
    private double shield = 0;

    // 영구 스탯 보너스(소모품/이벤트 등). 원본 Player 정의 데이터는 수정하지 않는다.
    private double bonusMaxHp = 0;
    private double bonusMaxMp = 0;
    private double bonusAtk = 0;
    private double bonusMagic = 0;
    private double bonusDef = 0;
    private double bonusMdef = 0;
    private double bonusSpd = 0;

    // 직업 레벨업 스킬 해금(세이브에 저장하지 않고, 레벨/설정으로 재계산)
    private final SkillUnlockRepository skillUnlockRepo = new SkillUnlockRepository();
    private final LinkedHashSet<String> knownSkillNames = new LinkedHashSet<>();


    // 상태이상(전투 중). 전투 시작 시 초기화
    private final StatusContainer statuses = new StatusContainer();

    // 게임 진행도. 챕터, 액트
    private int chapter = 1;
    private int act = 1; // 1~12

    // 챕터당 상점(상인) 등장: act 1~11 중 정확히 1번
    private int merchantActThisChapter = 1; // 1~11
    private boolean merchantDoneThisChapter = false;

    private double gold = 200; // 소지금

    // 인벤토리: 아이템 이름 -> 개수
    private final Map<String, Integer> inv = new LinkedHashMap<>();

    // 데이터(정의): 이름으로 조회
    private final Map<String, Enemy> enemyByName;
    private final Map<String, Consumables> consumableByName;
    private final Map<String, SkillDef> skillByName;

    private final Map<String, EquipmentDef> equipmentByName;
    private final Map<String, EquipmentSetDef> equipmentSetByName;

    // 장착 장비: 슬롯키 -> 장비 이름
    private final Map<String, String> equipped = new LinkedHashMap<>();

    // 장비/세트 보너스(장착 변경 시 재계산)
    private double equipMaxHp = 0;
    private double equipMaxMp = 0;
    private double equipAtk = 0;
    private double equipMagic = 0;
    private double equipDef = 0;
    private double equipMdef = 0;
    private double equipSpd = 0;
    private double equipMaxShield = 0;

    private final Set<String> activeSpecialTags = new LinkedHashSet<>();

    private final ChapterRepository chapterRepo;

    // 게임 중 실제로 사용할 플레이어 객체 생성자. 오리지널 데이터를 만질 필요를 줄임. 게임 중 변동하는 값(스탯 등)을 세션 필드로 분리.
    // !!!!!주의!!!!! 이 생성자의 매개변수인 playerBase는 변수타입이 Player(객체)인 객체 변수이다.
    private GameSession(Player playerBase,
                        String playerName,
                        Map<String, Enemy> enemyByName,
                        Map<String, Consumables> consumableByName,
                        Map<String, SkillDef> skillByName,
                        Map<String, EquipmentDef> equipmentByName,
                        Map<String, EquipmentSetDef> equipmentSetByName,
                        ChapterRepository chapterRepo) {
        this.playerBase = playerBase;
        this.playerName = playerName;
        /* 게임세션에서 쓸 필드를 가져온다. 체력과 마나? 그건 playerBase가 받은 플레이어 객체의 최대 체력과 최대 마나를
         * 위에서 정의한 필드들(현재 체력과 현재 마나. 줄 14~)로 가져오는 것이다. 매개변수에 없더라도 말이 되는 것.
         * 이로써 원본 정의 데이터(playerBase. Player타입 변수)와 게임 중에 실제로 사용하면서 변하는 데이터(GameSession 객체)가 구분되는 것이다.
         * 이게 없으면 툭하면 Player객체를 직접 수정하야 하는데, 이러면 게임 세이브, 로드, 전투, 리셋에서 지옥도가 열린다. */

        // 레벨 데이터가 잘못돼서 0 이하로 들어와버릴 경우, 강제로 1로 보정해준다. json의 데이터 또는 기본값이 0으로 잘못 들어와도 OK.
        this.level = Math.max(1, playerBase.getLevel());
        // 세션 시작 시, 풀피 풀마나
        this.hp = playerBase.getMaxHp();
        this.mp = playerBase.getMaxMp();
        this.enemyByName = enemyByName;
        this.consumableByName = consumableByName;
        this.skillByName = skillByName;
        this.equipmentByName = equipmentByName;
        this.equipmentSetByName = equipmentSetByName;
        initEquipSlots();
        recalcEquipmentBonuses();

        // 직업 스킬 해금(레벨 기반)
        refreshKnownSkills();

        // 챕터 1 시작 시 상점 스케줄 설정
        rollMerchantActForChapter();
        this.chapterRepo = chapterRepo;
    }

    private void rollMerchantActForChapter() {
        this.merchantActThisChapter = 1 + rng.nextInt(11);
        this.merchantDoneThisChapter = false;
    }

    /** 챕터가 바뀔 때 호출: 상점 스케줄 재설정 */
    public void resetMerchantForNewChapter() {
        rollMerchantActForChapter();
    }

    public int getMerchantActThisChapter() { return merchantActThisChapter; }
    public boolean isMerchantDoneThisChapter() { return merchantDoneThisChapter; }

    public void markMerchantDone() { this.merchantDoneThisChapter = true; }

    // 세이브/로드용
    public void setMerchantSchedule(int merchantAct, boolean done) {
        int a = merchantAct;
        if (a < 1 || a > 11) a = 1 + rng.nextInt(11);
        this.merchantActThisChapter = a;
        this.merchantDoneThisChapter = done;
    }

    // ★★★★★ 접근하면 안 되는 불변 데이터(원본) 대신, 게임에서 사용할 가변 데이터 세트를(새로운 세션) 준비하는 생성자.
    // Game 클래스에서 이 생성자를 호출, 로드한 데이터를 여기에 집어넣는다.
    public static GameSession newSession(Player chosen,
                                         Map<String, Enemy> enemyByName,
                                         Map<String, Consumables> consumableByName,
                                         Map<String, SkillDef> skillByName,
                                         Map<String, EquipmentDef> equipmentByName,
                                         Map<String, EquipmentSetDef> equipmentSetByName,
                                         ChapterRepository chapterRepo,
                                         String playerName) {
        // 정의 데이터(Map) 생성은 DataLoader가 담당한다.
        GameSession s = new GameSession(chosen, playerName, enemyByName, consumableByName, skillByName, equipmentByName, equipmentSetByName, chapterRepo);

        // 시작 아이템(테스트용)
        s.addItem("'보호막' 스크롤", 2);
        s.addItem("체력 포션", 3);
        return s;
    }

    public EnemyInstance spawnEnemy(String name) {
        return spawnEnemy(name, false);
    }

    public SkillDef skillDef(String name) {
        if (name == null) return null;
        return skillByName.get(name);
    }

    public Map<String, SkillDef> skillsView() {
        return Collections.unmodifiableMap(skillByName);
    }

    /**
     * 현재 레벨 기준으로 플레이어가 알고 있는 스킬 이름 목록.
     * (skill_unlocks.json 또는 기본 규칙으로 계산)
     */
    public Set<String> knownSkillsView() {
        return Collections.unmodifiableSet(knownSkillNames);
    }

    public List<String> knownSkillsList() {
        return new ArrayList<>(knownSkillNames);
    }

    /** 레벨/직업/룰을 기반으로 knownSkillNames를 재계산한다. */
    public void refreshKnownSkills() {
        knownSkillNames.clear();
        knownSkillNames.addAll(skillUnlockRepo.knownSkillsUpTo(playerBase.getClasses(), level));
    }

    // 챕터, 액트 스케일을 적용한 적 스폰
    public EnemyInstance spawnEnemy(String name, boolean isBoss) {
        ChapterConfig cfg = chapterConfig();

        int minLv = (cfg == null) ? 1 : cfg.getMinLevel();
        int maxLv = (cfg == null) ? 10 : cfg.getMaxLevel();

        //Chapter.json에 레벨 정보가 없을 시 임시 처리(1~10)
        if (minLv <= 0 || maxLv <= 0)
            return EnemyInstance.spawn(enemyDef(name), rng);

        //보스는 해당 챕터의 상한 레벨로 고정 스폰됨
        if (isBoss)
            return EnemyInstance.spawn(enemyDef(name), rng, maxLv, maxLv);

        // act 1~11 진행에 따라 min~max 범위 내에서 점진 상승
        int actIndex = Math.max(1, Math.min(11, getAct()));
        int range = Math.max(0, maxLv - minLv);

        // 11등분: 액트가 올라갈수록 스폰 구간이 뒤로 밀림
        int scaledMin = minLv + (actIndex - 1) * range / 11;
        int scaledMax = minLv + (actIndex) * range / 11;
        // 최댓값 초과된 수치 보정
        if (scaledMax < scaledMin)
            scaledMax = scaledMin;
        if (scaledMax > maxLv)
            scaledMax = maxLv;

        return EnemyInstance.spawn(enemyDef(name), rng, scaledMin, scaledMax);
    }

    // 인레이 힌트에서 '0개의 사용 위치'라고 뜬다고 해서 정말로 안 쓰이는 게 아니다.
    // 물론, private임에도 0개면 필요없는 게 맞다.
    public Random rng() { return rng; }

    public Player getPlayerBase() { return playerBase; }
    public String getPlayerName() { return playerName;}

    @Override
    public String getNameForStatus() { return playerName; }

    @Override
    public StatusContainer statuses() { return statuses; }

    @Override
    public double getShield() { return shield; }
    @Override
    public void setShield(double shield) {
        // 스탯/자원은 정수로 처리한다.
        long v = Math.round(shield);
        if (v < 0) v = 0;
        this.shield = v;
    }

    /** 장비(방패 등)로부터 "전투 밖/전투 종료 후에도 남는" 기본 실드. */
    public double getEquipBaseShield() {
        return Math.max(0, Math.round(equipMaxShield));
    }


    public void resetBattleStatuses() {
        statuses.clearAll();
        // 전투 시작 시: 장비 기본 실드는 유지
        setShield(getEquipBaseShield());
    }

    /** 전투 종료 정리: 전투 중 획득한 임시 실드는 사라지고, 장비 기본 실드만 남긴다. */
    public void endBattleCleanup() {
        statuses.clearAll();
        setShield(getEquipBaseShield());
    }


    // 플레이어의 실 능력치(레벨, 성장 반영)
    public int getLevel(){ return level; }

    public void setLevel(int level){
        this.level = Math.max(1,level);
        setHp(hp);
        setMp(mp);
    }

    public double getExp(){ return exp; }

    // 레벨업 요구 경험치 함수
    public int expToNextLevel(){
        return 100 + (level - 1) * 50;
    }

    // 레벨업, 경험치 획득 메소드
    public void gainExp(double amount){
        if(amount<=0) return;
        exp += amount;

        while(true) {
            int need = expToNextLevel();
            // 만약에, 버그로 인해 다음 레벨의 요구 경험치량이 양수가 아닌 경우, 예외 던지기
            if (need <= 0) throw new IllegalStateException("expToNextLevel must be > 0");

            if (exp < need)
                break;
            exp -= need;
            level += 1;

            // 레벨업 시 보상: "최대치의 절반"만큼 회복(정수)
            long healHp = Math.max(1, Math.round(getMaxHp() * 0.5));
            long healMp = Math.max(0, Math.round(getMaxMp() * 0.5));
            setHp(getHp() + healHp);
            if (healMp > 0) setMp(getMp() + healMp);

            // 레벨업 스킬 해금
            List<String> newly = skillUnlockRepo.skillsUnlockedAt(playerBase.getClasses(), level);
            if (newly != null && !newly.isEmpty()) {
                List<String> added = new ArrayList<>();
                for (String s : newly) {
                    if (s == null || s.isBlank()) continue;
                    if (knownSkillNames.add(s)) added.add(s);
                }
                if (!added.isEmpty()) {
                    System.out.println("\n[스킬 습득] " + String.join(", ", added));
                }
            }

        }
    }

    // 성장한 만큼의 스탯을 계산해서 반환하는 함수
    private double scale(double base, double growth) { return base + growth*(level - 1);}

    // scale 함수를 이용한 getter 함수들
    public double getMaxHp() {
        double v = scale(playerBase.getMaxHp(), playerBase.getGrowthMaxHp()) + bonusMaxHp + equipMaxHp;
        return Math.max(1, Math.round(v));
    }
    public double getMaxMp() {
        double v = scale(playerBase.getMaxMp(), playerBase.getGrowthMaxMp()) + bonusMaxMp + equipMaxMp;
        return Math.max(0, Math.round(v));
    }
    public double getAtk() {
        double v = scale(playerBase.getAtk(), playerBase.getGrowthAtk()) + bonusAtk + equipAtk;
        return Math.max(0, Math.round(v));
    }
    public double getMagic() {
        double v = scale(playerBase.getMagic(), playerBase.getGrowthMagic()) + bonusMagic + equipMagic;
        return Math.max(0, Math.round(v));
    }
    public double getDef() {
        double v = scale(playerBase.getDef(), playerBase.getGrowthDef()) + bonusDef + equipDef;
        return Math.max(0, Math.round(v));
    }
    public double getMdef() {
        double v = scale(playerBase.getMdef(), playerBase.getGrowthMdef()) + bonusMdef + equipMdef;
        return Math.max(0, Math.round(v));
    }
    public double getSpd() {
        double v = scale(playerBase.getSpd(), playerBase.getGrowthSpd()) + bonusSpd + equipSpd;
        return Math.max(0, Math.round(v));
    }

    // 플레이어의 현재 체력, 마나를 리턴하는 getter 함수
    public double getHp() { return hp; }
    public double getMp() { return mp; }

    /* 체력, 마나 범위를 안전하게 고정시키는 함수.
     * 값이 최대를 넘어가는 경우, 최댓값으로 잘라냄.
     * 그 후 값이 0을 넘어가는 경우, 현재 값으로 잘라냄.
     * 결과적으로 [0<=현재 값<=최댓값] 완성.
     */
    public void setHp(double hp) {
        long v = Math.round(hp);
        long max = Math.round(getMaxHp());
        if (v < 0) v = 0;
        if (v > max) v = max;
        this.hp = v;
    }
    public void setMp(double mp) {
        long v = Math.round(mp);
        long max = Math.round(getMaxMp());
        if (v < 0) v = 0;
        if (v > max) v = max;
        this.mp = v;
    }

    // 챕터, 액트의 getter, setter함수
    public int getChapter() { return chapter; }
    public int getAct() { return act; }
    public void setChapter(int chapter) {
        // 챕터는 최소 1이어야 한다.
        this.chapter = Math.max(1, chapter);
    }
    public void setAct(int act) {
        // 액트는 최대 12이어야 한다.
        this.act = Math.max(1, Math.min(12, act));
    }

    // 소지금 액수를 불러오는 getter함수 & 골드 수입을 적용하는 함수
    public double getGold() { return gold; }
    // 소지금에 delta 만큼 더한다.
    public void addGold(double delta) {
        long v = Math.round(this.gold + delta);
        if (v < 0) v = 0;
        this.gold = v;
    }

    // 세이브/로드用 세터(런타임 로직에서는 사용 지양할 것)
    public void setGold(double gold) {
        long v = Math.round(gold);
        if (v < 0) v = 0;
        this.gold = v;
    }
    public void setExp(double exp) {
        long v = Math.round(exp);
        if (v < 0) v = 0;
        this.exp = v;
    }

    // === 영구 스탯 보너스(소모품/이벤트 등) ===
    public double getBonusMaxHp() { return bonusMaxHp; }
    public double getBonusMaxMp() { return bonusMaxMp; }
    public double getBonusAtk() { return bonusAtk; }
    public double getBonusMagic() { return bonusMagic; }
    public double getBonusDef() { return bonusDef; }
    public double getBonusMdef() { return bonusMdef; }
    public double getBonusSpd() { return bonusSpd; }

    public void addPermanentStats(double maxHp, double maxMp, double atk, double magic, double def, double mdef, double spd) {
        bonusMaxHp += Math.round(maxHp);
        bonusMaxMp += Math.round(maxMp);
        bonusAtk += Math.round(atk);
        bonusMagic += Math.round(magic);
        bonusDef += Math.round(def);
        bonusMdef += Math.round(mdef);
        bonusSpd += Math.round(spd);

        // 최대치가 바뀌었을 수 있으므로 현재 자원 안전 클램프
        setHp(hp);
        setMp(mp);
    }

    // 세이브/로드용(런타임 로직에서는 직접 호출 지양)
    public void setPermanentBonuses(double maxHp, double maxMp, double atk, double magic, double def, double mdef, double spd) {
        this.bonusMaxHp = Math.round(maxHp);
        this.bonusMaxMp = Math.round(maxMp);
        this.bonusAtk = Math.round(atk);
        this.bonusMagic = Math.round(magic);
        this.bonusDef = Math.round(def);
        this.bonusMdef = Math.round(mdef);
        this.bonusSpd = Math.round(spd);
    }


    public void setInventory(Map<String, Integer> inventory) {
        inv.clear();
        if (inventory == null) return;
        for (var e : inventory.entrySet()) {
            String name = e.getKey();
            // int는 스택이다. Integer는 힙에 있는 객체이다. cnt가 e.getValue()값 자체가 아니라, 그 값을 들고 있는 객체이다.
            Integer cnt = e.getValue();
            if (name == null || cnt == null || cnt <= 0)
                continue;
            inv.put(name, cnt);
        }
    }

    // 세이브 데이터 적용하기
    public void applySaveData(desia.loader.SaveData data) {
        if (data == null) return;

        // 진행도
        setChapter(data.getChapter());
        setAct(data.getAct());

        // 상점(상인) 스케줄
        setMerchantSchedule(data.getMerchantActThisChapter(), data.isMerchantDoneThisChapter());

        // 성장/재화
        setLevel(data.getLevel());
        refreshKnownSkills();
        setExp(data.getExp());
        setGold(data.getGold());

        // 영구 스탯 보너스(HP/MP 클램프 전에 적용해야 저장된 현재값이 잘리지 않는다)
        setPermanentBonuses(
                data.getBonusMaxHp(),
                data.getBonusMaxMp(),
                data.getBonusAtk(),
                data.getBonusMagic(),
                data.getBonusDef(),
                data.getBonusMdef(),
                data.getBonusSpd()
        );

        // 장비(HP/MP 클램프 전에 적용)
        setEquipped(data.getEquipped());

        // 자원(클램프 포함)
        setHp(data.getHp());
        setMp(data.getMp());
        setShield(data.getShield());

        // 인벤토리
        setInventory(data.getInventory());
    }


    //


    // ===== 장비 정의 조회 =====
    public EquipmentDef equipmentDef(String name) {
        if (name == null) return null;
        return equipmentByName.get(name);
    }

    public Map<String, EquipmentDef> equipmentsView() {
        return Collections.unmodifiableMap(equipmentByName);
    }

    public Map<String, EquipmentSetDef> equipmentSetsView() {
        return Collections.unmodifiableMap(equipmentSetByName);
    }

    // ===== 장착 상태 =====
    public Map<String, String> equippedView() {
        return Collections.unmodifiableMap(equipped);
    }

    public String equippedItem(String slotKey) {
        return equipped.get(slotKey);
    }

    public void setEquipped(Map<String, String> saved) {
        initEquipSlots();
        // 초기화
        for (String k : equipped.keySet()) equipped.put(k, null);
        if (saved != null) {
            for (Map.Entry<String, String> e : saved.entrySet()) {
                if (e == null) continue;
                String k = e.getKey();
                if (!equipped.containsKey(k)) continue;
                String v = e.getValue();
                if (v != null && equipmentDef(v) == null) continue; // 정의 없는 장비는 무시
                equipped.put(k, v);
            }
        }
        recalcEquipmentBonuses();
    }

    public void setEquippedSlot(String slotKey, String equipName) {
        initEquipSlots();
        if (!equipped.containsKey(slotKey)) return;
        if (equipName != null && equipmentDef(equipName) == null) return;
        equipped.put(slotKey, equipName);
        recalcEquipmentBonuses();
    }

    public Set<String> activeSpecialTagsView() {
        return Collections.unmodifiableSet(activeSpecialTags);
    }

    public boolean hasSpecialTag(String tag) {
        if (tag == null) return false;
        return activeSpecialTags.contains(tag);
    }

    private void initEquipSlots() {
        if (!equipped.isEmpty()) return;
        equipped.put(SLOT_HELMET, null);
        equipped.put(SLOT_CHEST, null);
        equipped.put(SLOT_LEGS, null);
        equipped.put(SLOT_BOOTS, null);
        equipped.put(SLOT_CLOAK, null);
        equipped.put(SLOT_RING1, null);
        equipped.put(SLOT_RING2, null);
        equipped.put(SLOT_WEAPON1, null);
        equipped.put(SLOT_WEAPON2, null);
    }

    private boolean isTwoHand(EquipmentDef def) {
        if (def == null) return false;
        String s = def.getSlot();
        if (s != null && s.contains("양손")) return true;
        String h = def.getWeaponHand();
        return "TWO_HAND".equalsIgnoreCase(h);
    }

    public boolean isTwoHandEquipped() {
        EquipmentDef w1 = equipmentDef(equipped.get(SLOT_WEAPON1));
        return isTwoHand(w1);
    }

    private void recalcEquipmentBonuses() {
        equipMaxHp = equipMaxMp = equipAtk = equipMagic = equipDef = equipMdef = equipSpd = equipMaxShield = 0;
        activeSpecialTags.clear();

        // (1) 장비 자체 스탯
        for (String name : equipped.values()) {
            if (name == null) continue;
            EquipmentDef def = equipmentDef(name);
            if (def == null) continue;
            applyStatMap(def.getStats());
        }

        // (2) 세트 보너스
        for (Map.Entry<String, EquipmentSetDef> e : equipmentSetByName.entrySet()) {
            EquipmentSetDef set = e.getValue();
            if (set == null || set.getPieces() == null) continue;

            int count = 0;
            for (String piece : set.getPieces()) {
                if (piece == null) continue;
                if (equipped.containsValue(piece)) count++;
            }

            if (set.getBonuses() == null) continue;
            for (SetBonusDef b : set.getBonuses()) {
                if (b == null) continue;
                if (count >= b.getPieces()) {
                    applyStatMap(b.getStats());
                    if (b.getSpecialTags() != null) activeSpecialTags.addAll(b.getSpecialTags());
                }
            }
        }

        // (3) 장착 변경으로 최대치가 변했으니 현재치를 클램프
        setHp(hp);
        setMp(mp);
        // 장비 기본 실드는 전투 밖에서도 유지된다.
        setShield(getEquipBaseShield());
    }

    private void applyStatMap(Map<String, Integer> stats) {
        if (stats == null) return;
        // equipment.json stat 키 -> GameSession 스탯으로 매핑
        equipAtk += stats.getOrDefault("attack", 0);
        equipMagic += stats.getOrDefault("spell_power", 0);
        equipDef += stats.getOrDefault("defense", 0);
        equipMdef += stats.getOrDefault("magic_resist", 0);
        equipSpd += stats.getOrDefault("speed", 0);
        equipMaxHp += stats.getOrDefault("max_hp", 0);
        equipMaxMp += stats.getOrDefault("max_mp", 0);
        equipMaxShield += stats.getOrDefault("max_shield", 0);
    }

    public Map<String, Integer> inventoryView() {
        return Collections.unmodifiableMap(inv); }
    public void addItem(String name, int count) {
        if (count <= 0) return;
        inv.put(name, inv.getOrDefault(name, 0) + count);
    }
    public boolean removeItem(String name, int count) {
        int cur = inv.getOrDefault(name, 0);
        if (count <= 0 || cur < count) return false;
        int next = cur - count;
        if (next == 0) inv.remove(name);
        else inv.put(name, next);
        return true;
    }

    // 적 '개체(객체 아님)'의 이름을 받으면, 그 적에 해당하는 적 객체를 리턴하는 함수
    public Enemy enemyDef(String name) {
        Enemy e = enemyByName.get(name);
        if (e == null)
            throw new IllegalArgumentException("unknown enemy: " + name);
        return e;
    }

    public Consumables consumableDef(String name) {
        return consumableByName.get(name);
    }

    public Collection<Consumables> allConsumables() {
        return consumableByName.values();
    }

    public ChapterConfig chapterConfig() {
        return chapterRepo.get(chapter);
    }

    public boolean isFinalChapterCleared() {
        return chapter > chapterRepo.maxChapterId();
    }
}
