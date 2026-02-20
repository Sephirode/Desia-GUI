package desia.status;

import java.util.EnumMap;
import java.util.Map;

public class StatusContainer {

    private final EnumMap<StatusType, Integer> stacks = new EnumMap<>(StatusType.class);

    public int getStacks(StatusType type) {
        if (type == null) return 0;
        Integer v = stacks.get(type);
        return (v == null) ? 0 : v;
    }

    public boolean has(StatusType type) {
        return getStacks(type) > 0;
    }

    public void addStacks(StatusType type, int n) {
        if (type == null || n <= 0) return;
        int cur = getStacks(type);
        stacks.put(type, cur + n);
    }

    public void reduceStacks(StatusType type, int n) {
        if (type == null || n <= 0) return;
        int cur = getStacks(type);
        int next = Math.max(0, cur - n);
        if (next == 0) stacks.remove(type);
        else stacks.put(type, next);
    }

    public void clear(StatusType type) {
        if (type == null) return;
        stacks.remove(type);
    }

    public void clearAll() {
        stacks.clear();
    }

    public Map<StatusType, Integer> snapshot() {
        return Map.copyOf(stacks);
    }
}
