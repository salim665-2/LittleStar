package com.littlestar.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.feature.review.RoundReviewActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * 本轮回顾的 Fragment 动态界面（手机端）：
 * 列表在 reviewPane 内 → 点行切换到详情 Fragment → 返回键回到列表。
 */
@RunWith(AndroidJUnit4.class)
public final class ReviewFragmentsTest {

    @Test
    public void listRow_opensDetailFragment_backReturnsToList() {
        AppContainer container = ((LittleStarApp) ApplicationProvider.getApplicationContext())
                .container();
        container.settings.setSoundEnabled(false);
        container.store.update(state -> {
            container.content.ensureLoaded();
            state.sessions.clear();
            ProgressState.Session session = new ProgressState.Session();
            session.id = 7;
            session.date = container.clock.today().toString();
            session.kind = "DAILY";
            session.newWordIds = List.of("mao", "gou", "niao");
            session.correct = 2;
            session.total = 3;
            session.answers.add(answer("mao", "mao", true));
            session.answers.add(answer("gou", "niao", false));
            session.answers.add(answer("niao", "niao", true));
            state.sessions.add(session);
            state.nextSessionId = 8;
        });
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                RoundReviewActivity.class).putExtra("sessionId", 7L);
        try (ActivityScenario<RoundReviewActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.answerList)).check(matches(isDisplayed()));
            onView(withId(R.id.reviewSummary)).check(matches(withText(
                    ApplicationProvider.getApplicationContext()
                            .getString(R.string.review_summary, 2, 3))));

            // 第二行（gou）点击 → 详情 Fragment 替换列表。
            scenario.onActivity(a -> {
                RecyclerView list = a.findViewById(R.id.answerList);
                ViewGroup row = (ViewGroup) list.getChildAt(1);
                row.performClick();
            });
            onView(withId(R.id.detailHanzi)).check(matches(isDisplayed()));
            onView(withId(R.id.detailPosition)).check(matches(withText(
                    ApplicationProvider.getApplicationContext()
                            .getString(R.string.review_position, 2, 3))));

            onView(withId(R.id.btnPrev)).perform(click());
            onView(withId(R.id.detailPosition)).check(matches(withText(
                    ApplicationProvider.getApplicationContext()
                            .getString(R.string.review_position, 1, 3))));
            onView(withId(R.id.btnNext)).perform(click());
            onView(withId(R.id.detailPosition)).check(matches(withText(
                    ApplicationProvider.getApplicationContext()
                            .getString(R.string.review_position, 2, 3))));

            pressBack();
            onView(withId(R.id.answerList)).check(matches(isDisplayed()));
            onView(withId(R.id.reviewSummary)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void emptyRound_showsEmptyState() {
        AppContainer container = ((LittleStarApp) ApplicationProvider.getApplicationContext())
                .container();
        container.store.update(state -> {
            container.content.ensureLoaded();
            state.sessions.clear();
            ProgressState.Session session = new ProgressState.Session();
            session.id = 9;
            session.date = container.clock.today().toString();
            session.kind = "DAILY";
            session.correct = 0;
            session.total = 0;
            state.sessions.add(session);
            state.nextSessionId = 10;
        });
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                RoundReviewActivity.class).putExtra("sessionId", 9L);
        try (ActivityScenario<RoundReviewActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.emptyState)).check(matches(isDisplayed()));
            onView(withId(R.id.answerList)).check(matches(
                    androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility(
                            androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE)));
        }
    }

    private static ProgressState.Answer answer(String wordId, String chosen, boolean correct) {
        ProgressState.Answer a = new ProgressState.Answer();
        a.wordId = wordId;
        a.chosen = chosen;
        a.correct = correct;
        a.firstTry = true;
        a.at = System.currentTimeMillis();
        return a;
    }
}
