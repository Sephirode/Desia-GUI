package desia.ui;

/**
 * UI 텍스트 출력 유틸.
 *
 * - 콘솔 게임 시절의 ConsoleUi(줄바꿈 도배) 의존을 제거하고,
 * - JavaFX에서는 TextArea를 실제로 clear() 하도록 훅(clearHandler)을 제공한다.
 *
 * 기본 동작(핸들러 미설정)은 호환을 위해 "여러 줄 줄바꿈"으로 fallback 한다.
 */
public final class UiText {
    private UiText() {}

    /** JavaFX 등 UI 쪽에서 등록하는 화면 정리 훅. */
    private static volatile Runnable clearHandler;

    public static void setClearHandler(Runnable handler) {
        clearHandler = handler;
    }

    public static void clear() {
        Runnable h = clearHandler;
        if (h != null) {
            h.run();
            return;
        }
        // fallback: 콘솔에서도 지나치게 긴 줄바꿈 도배는 피한다.
        System.out.print("\n".repeat(20));
    }

    public static void separator(int n) {
        if (n <= 0) { System.out.println(); return; }
        System.out.println("-".repeat(n));
    }

    public static void separatorX(int n) {
        if (n <= 0) { System.out.println(); return; }
        System.out.println("=".repeat(n));
    }

    /**
     * style==1: "-" 구분선
     * style==2: "=" 구분선(굵은 버전)
     */
    public static void heading(String title, int style) {
        if (style == 2) {
            separatorX(30);
            System.out.println(title);
            separatorX(30);
            return;
        }
        separator(30);
        System.out.println(title);
        separator(30);
    }
}
