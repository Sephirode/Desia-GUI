package desia.combat;

public enum DamageType {
    PHYSICAL, MAGIC, TRUE;

    public static DamageType from(String s) {
        if (s == null) return PHYSICAL;
        return switch (s.trim().toLowerCase()) {
            case "magic" -> MAGIC;
            case "true" -> TRUE;
            default -> PHYSICAL;
        };
    }
}
