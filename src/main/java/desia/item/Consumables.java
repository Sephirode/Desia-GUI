package desia.item;

import lombok.*;
//import lombok.ToString;

@AllArgsConstructor //자동 생성자
@NoArgsConstructor  //default 생성자
@Builder            //객체를 생성하기 위한 다른 방법. 변수 순서를 섞어도 가능하게 함.
@Getter
//@Setter
@ToString

public class Consumables {

    private String name;
    private String category;
    private String description;
    private String rarity;
    private String effectType;
    private boolean useInBattle;
    private boolean useOutOfBattle;

    private int level;

    private double crt;
    private double xp;
    private double atk;
    private double magic;
    private double def;
    private double mdef;
    private double maxHp;
    private double maxMp;
    private double hp;
    private double mp;
    private double spd;
    private double price;

}
