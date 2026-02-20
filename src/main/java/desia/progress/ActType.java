package desia.progress;

/*
 * enum 타입 변수는 '미리' 정해진 값만 받을 수 있는 변수 타입이다.
 * 여기서는 BATTLE, SHOP, STORY가 지정되어 있으므로 이 세 변수들만 사용 가능하다.
 * 예를 들어 ActType.REST 같은 건 불가능하다. 등록해두지 않은 변수명이기 때문이다.
 * 그리니까 일종의 직원 카드키, 회원증같은 거라고 보면 된다.
 */
public enum ActType {
    BATTLE,
    SHOP,
    STORY
}
