package desia.status;

public interface StatusCarrier {

    String getNameForStatus();
    double getHp();
    void setHp(double hp);
    StatusContainer statuses();
}
