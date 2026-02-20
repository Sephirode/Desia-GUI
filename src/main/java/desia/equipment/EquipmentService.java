package desia.equipment;

import desia.io.Io;
import desia.item.EquipmentDef;
import desia.progress.GameSession;
import desia.ui.UiText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static desia.progress.GameSession.*;

/**
 * 장비 장착/해제 UI(전투 밖).
 * - 인벤토리(Map<이름,개수>)에서 장비를 찾아 장착한다.
 * - 장착 중인 장비는 인벤토리에서 빠진 상태로 취급한다(장착 1개 = 인벤토리 1개 사용).
 */
public class EquipmentService {

    private final Io io;

    public EquipmentService(Io io) {
        this.io = io;
    }

    public void open(GameSession session) {
        while (true) {
            UiText.clear();
            UiText.heading("[장비]", 1);

            printEquipped(session);

            int cmd = io.choose("[장비 메뉴]", List.of("장착/교체", "해제", "나가기"));

            if (cmd == 1) {
                equipMenu(session);
            } else if (cmd == 2) {
                unequipMenu(session);
            } else {
                return;
            }
        }
    }

    private void printEquipped(GameSession session) {
        Map<String, String> eq = session.equippedView();

        System.out.println("\n[방어구]");
        System.out.println("투구 : " + show(eq.get(SLOT_HELMET)));
        System.out.println("흉갑 : " + show(eq.get(SLOT_CHEST)));
        System.out.println("각반 : " + show(eq.get(SLOT_LEGS)));
        System.out.println("부츠 : " + show(eq.get(SLOT_BOOTS)));

        System.out.println("\n[장신구]");
        System.out.println("망토 : " + show(eq.get(SLOT_CLOAK)));
        System.out.println("반지1: " + show(eq.get(SLOT_RING1)));
        System.out.println("반지2: " + show(eq.get(SLOT_RING2)));

        System.out.println("\n[무기]");
        String w1 = eq.get(SLOT_WEAPON1);
        String w2 = eq.get(SLOT_WEAPON2);
        if (session.isTwoHandEquipped()) {
            System.out.println("양손 : " + show(w1));
        } else {
            System.out.println("무기1: " + show(w1));
            System.out.println("무기2: " + show(w2));
        }

        if (!session.activeSpecialTagsView().isEmpty()) {
            System.out.println("\n[세트 특수 태그]");
            for (String t : session.activeSpecialTagsView()) {
                System.out.println("- " + t);
            }
        }
    }

    private String show(String name) {
        return (name == null || name.isBlank()) ? "(없음)" : name;
    }

    private void equipMenu(GameSession session) {
        List<String> equipNames = listEquipmentsInInventory(session);
        if (equipNames.isEmpty()) {
            System.out.println("\n장착 가능한 장비가 인벤토리에 없습니다.");
            io.anythingToContinue();
            return;
        }

        while (true) {
            UiText.clear();
            UiText.heading("[장비] 장착/교체", 1);

            List<String> labels = new ArrayList<>();
            for (int i = 0; i < equipNames.size(); i++) {
                String name = equipNames.get(i);
                EquipmentDef def = session.equipmentDef(name);
                int count = session.inventoryView().getOrDefault(name, 0);
                labels.add(formatEquipLine(def, count));
            }
            int input = io.chooseAllowCancel("[장착/교체] 장비를 선택하세요", labels, "뒤로");
            if (input == 0) return;

            String chosenName = equipNames.get(input - 1);
            EquipmentDef chosen = session.equipmentDef(chosenName);
            if (chosen == null) {
                System.out.println("정의되지 않은 장비라 장착 불가: " + chosenName);
                io.anythingToContinue();
                continue;
            }

            boolean ok = equipOne(session, chosenName, chosen);
            if (ok) {
                System.out.println("\n장착 완료: " + chosenName);
            } else {
                System.out.println("\n장착 실패.");
            }
            io.anythingToContinue();
            return;
        }
    }

    private boolean equipOne(GameSession session, String equipName, EquipmentDef def) {
        String slot = safe(def.getSlot());

        // 인벤토리에 있는지 확인
        if (!session.removeItem(equipName, 1)) {
            return false;
        }

        // 실패 시 환불해야 하므로, 교체로 인해 빠지는 장비를 저장
        List<String> returnedToInv = new ArrayList<>();

        try {
            if (slot.equals("투구")) {
                replace(session, SLOT_HELMET, equipName, returnedToInv);
            } else if (slot.equals("흉갑")) {
                replace(session, SLOT_CHEST, equipName, returnedToInv);
            } else if (slot.equals("각반")) {
                replace(session, SLOT_LEGS, equipName, returnedToInv);
            } else if (slot.equals("부츠")) {
                replace(session, SLOT_BOOTS, equipName, returnedToInv);
            } else if (slot.equals("망토")) {
                replace(session, SLOT_CLOAK, equipName, returnedToInv);
            } else if (slot.equals("반지")) {
                String target = pickRingSlot(session);
                if (target == null) throw new IllegalStateException("반지 슬롯 선택 실패");
                replace(session, target, equipName, returnedToInv);
            } else if (slot.equals("방패")) {
                if (session.isTwoHandEquipped()) {
                    throw new IllegalStateException("양손 무기를 장착 중이면 방패를 들 수 없습니다.");
                }
                replace(session, SLOT_WEAPON2, equipName, returnedToInv);
            } else if (slot.contains("양손")) {
                // 양손 무기: WEAPON1에 장착하고 WEAPON2는 비운다(기존 장착품은 인벤으로 회수)
                // WEAPON1 교체
                replace(session, SLOT_WEAPON1, equipName, returnedToInv);
                // WEAPON2 비우기
                String w2 = session.equippedItem(SLOT_WEAPON2);
                if (w2 != null) {
                    session.setEquippedSlot(SLOT_WEAPON2, null);
                    returnedToInv.add(w2);
                }
            } else if (slot.contains("한손")) {
                if (session.isTwoHandEquipped()) {
                    throw new IllegalStateException("양손 무기를 장착 중이면 한손 무기를 추가로 들 수 없습니다. 먼저 해제하세요.");
                }
                String target = pickOneHandSlot(session);
                if (target == null) throw new IllegalStateException("무기 슬롯 선택 실패");
                replace(session, target, equipName, returnedToInv);
            } else {
                throw new IllegalStateException("지원하지 않는 장비 슬롯: " + slot);
            }

            // 회수된 장비들을 인벤에 되돌린다
            for (String back : returnedToInv) {
                if (back != null) session.addItem(back, 1);
            }
            return true;
        } catch (Exception ex) {
            // 실패: 인벤 환불 + 회수된 장비 롤백
            session.addItem(equipName, 1);
            // returnedToInv는 아직 인벤에 넣지 않았으므로, 롤백할 게 없음.
            System.out.println(ex.getMessage());
            return false;
        }
    }

    private void replace(GameSession session, String slotKey, String newEquipName, List<String> returnedToInv) {
        String prev = session.equippedItem(slotKey);
        if (prev != null) {
            returnedToInv.add(prev);
        }
        session.setEquippedSlot(slotKey, newEquipName);
    }

    private String pickRingSlot(GameSession session) {
        String r1 = session.equippedItem(SLOT_RING1);
        String r2 = session.equippedItem(SLOT_RING2);
        if (r1 == null) return SLOT_RING1;
        if (r2 == null) return SLOT_RING2;

        int cmd = io.chooseAllowCancel("[반지] 교체할 슬롯을 고르세요", List.of("반지1 (" + r1 + ")", "반지2 (" + r2 + ")"), "취소");
        if (cmd == 1) return SLOT_RING1;
        if (cmd == 2) return SLOT_RING2;
        return null;
    }

    private String pickOneHandSlot(GameSession session) {
        String w1 = session.equippedItem(SLOT_WEAPON1);
        String w2 = session.equippedItem(SLOT_WEAPON2);
        if (w1 == null) return SLOT_WEAPON1;
        if (w2 == null) return SLOT_WEAPON2;

        int cmd = io.chooseAllowCancel("[무기] 교체할 슬롯을 고르세요", List.of("무기1 (" + w1 + ")", "무기2 (" + w2 + ")"), "취소");
        if (cmd == 1) return SLOT_WEAPON1;
        if (cmd == 2) return SLOT_WEAPON2;
        return null;
    }

    private void unequipMenu(GameSession session) {
        Map<String, String> eq = session.equippedView();
        List<String> slots = new ArrayList<>();
        List<String> names = new ArrayList<>();

        for (Map.Entry<String, String> e : eq.entrySet()) {
            if (e.getValue() == null) continue;
            slots.add(e.getKey());
            names.add(e.getValue());
        }

        if (slots.isEmpty()) {
            System.out.println("\n해제할 장비가 없습니다.");
            io.anythingToContinue();
            return;
        }

        while (true) {
            UiText.clear();
            UiText.heading("[장비] 해제", 1);

            List<String> labels = new ArrayList<>();
            for (int i = 0; i < slots.size(); i++) {
                String slotKey = slots.get(i);
                String name = names.get(i);
                labels.add(slotLabel(slotKey) + " : " + name);
            }
            int input = io.chooseAllowCancel("[해제] 장비를 선택하세요", labels, "뒤로");
            if (input == 0) return;

            String slotKey = slots.get(input - 1);
            String itemName = session.equippedItem(slotKey);
            if (itemName == null) return;

            // 양손 무기 해제면 WEAPON1만 해제하면 된다(WEAPON2는 이미 비어있음)
            session.setEquippedSlot(slotKey, null);
            session.addItem(itemName, 1);

            System.out.println("\n해제 완료: " + itemName);
            io.anythingToContinue();
            return;
        }
    }

    private List<String> listEquipmentsInInventory(GameSession session) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : session.inventoryView().entrySet()) {
            if (e == null) continue;
            String name = e.getKey();
            int count = (e.getValue() == null) ? 0 : e.getValue();
            if (count <= 0) continue;
            if (session.equipmentDef(name) != null) {
                out.add(name);
            }
        }
        return out;
    }

    private String formatEquipLine(EquipmentDef def, int count) {
        if (def == null) return "(알 수 없는 장비)";
        StringBuilder sb = new StringBuilder();
        sb.append(def.getName());
        sb.append("  [").append(safe(def.getSlot())).append("]");
        sb.append(" x").append(count);

        if (def.getStats() != null && !def.getStats().isEmpty()) {
            sb.append("  {");
            boolean first = true;
            for (Map.Entry<String, Integer> e : def.getStats().entrySet()) {
                if (e == null) continue;
                if (!first) sb.append(", ");
                first = false;
                sb.append(e.getKey()).append("+").append(e.getValue());
            }
            sb.append("}");
        }
        return sb.toString();
    }

    private String slotLabel(String slotKey) {
        return switch (slotKey) {
            case SLOT_HELMET -> "투구";
            case SLOT_CHEST -> "흉갑";
            case SLOT_LEGS -> "각반";
            case SLOT_BOOTS -> "부츠";
            case SLOT_CLOAK -> "망토";
            case SLOT_RING1 -> "반지1";
            case SLOT_RING2 -> "반지2";
            case SLOT_WEAPON1 -> "무기1";
            case SLOT_WEAPON2 -> "무기2";
            default -> slotKey;
        };
    }

    private String safe(String s) {
        return (s == null) ? "" : s;
    }
}
