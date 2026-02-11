package desia.combat;

public interface ShieldCarrier {
    double getShield();
    void setShield(double shield);

    default void addShield(double amount) {
        // 이 프로젝트는 "스탯/자원은 소수점 없이"를 원칙으로 한다.
        long amt = Math.round(amount);
        if (amt <= 0) return;
        long cur = Math.round(getShield());
        setShield(Math.max(0, cur + amt));
    }

    // @return 실드로 흡수 후 HP로 들어갈 남은 피해
    default double absorbDamage(double incomingDamage) {
        long incoming = Math.round(incomingDamage);
        if (incoming <= 0) return 0;

        long s = Math.max(0, Math.round(getShield()));
        if (s <= 0) return incoming;

        if (incoming >= s) {
            setShield(0);
            return incoming - s;
        }
        setShield(s - incoming);
        return 0;
    }
}
