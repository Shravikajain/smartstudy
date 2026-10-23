package com.smartstudy.backend.engine;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.smartstudy.backend.model.Question;

public final class IrtEngine {

    private static final double MIN_ABILITY = -3.0;
    private static final double MAX_ABILITY = 3.0;

    private IrtEngine() {
    }

    public static double probabilityCorrect(double ability, double difficulty) {
        double difference = ability - difficulty;
        if (difference >= 0) {
            return 1.0 / (1.0 + Math.exp(-difference));
        }
        double exp = Math.exp(difference);
        return exp / (1.0 + exp);
    }

    public static double information(double ability, double difficulty) {
        double probability = probabilityCorrect(ability, difficulty);
        return probability * (1.0 - probability);
    }

    public static double updateAbility(double ability, double difficulty,
                                       boolean correct, int answeredCount) {
        double probability = probabilityCorrect(ability, difficulty);
        double observed = correct ? 1.0 : 0.0;
        double step = 1.0 / (Math.max(0, answeredCount) + 1.0);
        return clamp(ability + step * (observed - probability));
    }

    public static double updateDifficulty(double ability, double difficulty,
                                          boolean correct, int answeredCount) {
        double probability = probabilityCorrect(ability, difficulty);
        double observed = correct ? 1.0 : 0.0;
        double step = 1.0 / (Math.max(0, answeredCount) + 1.0);
        return clampDifficulty(difficulty - step * (observed - probability));
    }

    public static Optional<Question> selectNext(double ability, List<Question> candidates) {
        return candidates.stream()
                .min(Comparator.comparingDouble(q -> Math.abs(q.getDifficulty() - ability)));
    }

    private static double clamp(double value) {
        return Math.max(MIN_ABILITY, Math.min(MAX_ABILITY, value));
    }

    private static double clampDifficulty(double value) {
        return Math.max(MIN_ABILITY, Math.min(MAX_ABILITY, value));
    }
}
