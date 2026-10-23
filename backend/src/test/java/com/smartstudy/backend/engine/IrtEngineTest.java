package com.smartstudy.backend.engine;

import com.smartstudy.backend.model.Question;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class IrtEngineTest {

    private static Question questionWithDifficulty(double b) {
        return new Question(null, "prompt", List.of("A", "B", "C", "D"), 0, "why", b);
    }

    @Test
    void probabilityIsHalfWhenAbilityEqualsDifficulty() {
        assertThat(IrtEngine.probabilityCorrect(1.2, 1.2)).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void probabilityRisesAsAbilityGrows() {
        assertThat(IrtEngine.probabilityCorrect(2.0, 0.0))
                .isGreaterThan(IrtEngine.probabilityCorrect(0.0, 0.0));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-2.0, -0.5, 0.0, 1.5})
    void probabilitiesAreSymmetric(double gap) {
        // sigmoid(x) + sigmoid(-x) must equal 1
        double p = IrtEngine.probabilityCorrect(gap, 0.0);
        double mirror = IrtEngine.probabilityCorrect(0.0, gap);
        assertThat(p + mirror).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void informationPeaksWhenAbilityMatchesDifficulty() {
        double atMatch = IrtEngine.information(0.0, 0.0);
        assertThat(atMatch).isCloseTo(0.25, within(1e-9));
        assertThat(atMatch).isGreaterThan(IrtEngine.information(0.0, 2.0));
        assertThat(atMatch).isGreaterThan(IrtEngine.information(0.0, -2.0));
    }

    @Test
    void firstUpdateMatchesHandCalculation() {
        // p = 0.5, surprise = 0.5, step = 1.0  ->  theta moves by exactly 0.5
        assertThat(IrtEngine.updateAbility(0.0, 0.0, true, 0)).isCloseTo(0.5, within(1e-9));
        assertThat(IrtEngine.updateAbility(0.0, 0.0, false, 0)).isCloseTo(-0.5, within(1e-9));
    }

    @Test
    void beatingAHardQuestionMovesAbilityMoreThanBeatingAnEasyOne() {
        double afterHard = IrtEngine.updateAbility(0.0, 2.0, true, 0);
        double afterEasy = IrtEngine.updateAbility(0.0, -2.0, true, 0);
        assertThat(afterHard).isGreaterThan(afterEasy);
    }

    @Test
    void stepsShrinkAsMoreQuestionsAreAnswered() {
        double early = IrtEngine.updateAbility(0.0, 0.0, true, 0);
        double late = IrtEngine.updateAbility(0.0, 0.0, true, 10);
        assertThat(late).isLessThan(early);
    }

    @Test
    void abilityStaysWithinBounds() {
        assertThat(IrtEngine.updateAbility(3.0, 3.0, true, 0)).isEqualTo(3.0);
        assertThat(IrtEngine.updateAbility(-3.0, -3.0, false, 0)).isEqualTo(-3.0);
    }

    @Test
    void difficultyMovesOppositeToTheStudentsResult() {
        assertThat(IrtEngine.updateDifficulty(0.0, 0.0, true, 0)).isLessThan(0.0);
        assertThat(IrtEngine.updateDifficulty(0.0, 0.0, false, 0)).isGreaterThan(0.0);
    }

    @Test
    void selectNextPicksTheQuestionClosestToCurrentAbility() {
        List<Question> pool = List.of(
                questionWithDifficulty(-2.0),
                questionWithDifficulty(0.4),
                questionWithDifficulty(2.0));

        Optional<Question> next = IrtEngine.selectNext(0.5, pool);

        assertThat(next).isPresent();
        assertThat(next.get().getDifficulty()).isEqualTo(0.4);
    }

    @Test
    void selectNextReturnsEmptyWhenThereAreNoCandidates() {
        assertThat(IrtEngine.selectNext(0.0, List.of())).isEmpty();
    }
}