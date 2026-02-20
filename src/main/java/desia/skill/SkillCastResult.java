package desia.skill;

import lombok.*;
import java.util.*;

@Getter
@AllArgsConstructor
public class SkillCastResult {
    private final boolean spentTurn;
    private final List<String> logs;

    public static SkillCastResult noTurn(String msg) {
        return new SkillCastResult(false, List.of(msg));
    }

    public static SkillCastResult turn(List<String> logs) {
        return new SkillCastResult(true, logs);
    }
}
