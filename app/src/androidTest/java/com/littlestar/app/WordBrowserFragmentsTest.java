package com.littlestar.app;

import android.graphics.Rect;
import android.view.View;

import androidx.fragment.app.FragmentManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.feature.words.ThemesActivity;
import com.littlestar.app.feature.words.ThemesFragment;
import com.littlestar.app.feature.words.WordListFragment;
import com.littlestar.app.feature.words.WordSearchActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class WordBrowserFragmentsTest {

    @Test
    public void switchesFragmentsInOneActivityAndRestoresSelection() throws InterruptedException {
        try (ActivityScenario<ThemesActivity> scenario = ActivityScenario.launch(ThemesActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.getSupportFragmentManager().findFragmentById(R.id.themesPane)
                        instanceof ThemesFragment);
                assertNull(activity.getSupportFragmentManager().findFragmentById(R.id.wordsPane));
            });
            selectTheme("Animals");
            assertWordList("Animals");
            scenario.recreate();
            assertWordList("Animals");

            boolean[] wide = new boolean[1];
            scenario.onActivity(activity -> {
                FragmentManager fragments = activity.getSupportFragmentManager();
                assertTrue(fragments.findFragmentById(R.id.wordsPane) instanceof WordListFragment);
                View themes = activity.findViewById(R.id.themesPane);
                View words = activity.findViewById(R.id.wordsPane);
                wide[0] = activity.getResources().getConfiguration().screenWidthDp >= 600;
                assertTrue(words.isShown());
                if (wide[0]) {
                    Rect themesBounds = new Rect();
                    Rect wordBounds = new Rect();
                    assertTrue(themes.getGlobalVisibleRect(themesBounds));
                    assertTrue(words.getGlobalVisibleRect(wordBounds));
                    assertFalse("Both panes must occupy separate columns",
                            Rect.intersects(themesBounds, wordBounds));
                } else {
                    assertFalse(themes.isShown());
                }
            });

            if (wide[0]) {
                selectTheme("Fruits");
                assertWordList("Fruits");
                onView(withId(R.id.themesList)).check(matches(isDisplayed()));
            } else {
                pressBack();
                onView(withId(R.id.themesList)).check(matches(isDisplayed()));
                scenario.onActivity(activity ->
                        assertNull(activity.getSupportFragmentManager().findFragmentById(R.id.wordsPane)));
                selectTheme("Fruits");
                assertWordList("Fruits");
                onView(withContentDescription(R.string.nav_back)).perform(click());
                onView(withId(R.id.themesList)).check(matches(isDisplayed()));
            }
        }
    }

    @Test
    public void menuFindsWordsAcrossThemesAndReviewsRecordedCards() throws Exception {
        setRecordedWords(false);
        try (ActivityScenario<ThemesActivity> ignored = ActivityScenario.launch(ThemesActivity.class)) {
            onView(withId(R.id.browserToolbar)).check(matches(isDisplayed()));
            openWordMenu();
            onView(withText(R.string.word_search_title)).check(matches(isDisplayed()));
            UiEvidence.capture("word_browser_options");
            onView(withText(R.string.word_review_recorded)).perform(click());
            awaitContent();
            onView(withText(R.string.word_review_empty)).check(matches(isDisplayed()));
            onView(withId(android.R.id.button1)).perform(click());

            selectTheme("Animals");
            assertWordList("Animals");
            openWordMenu();
            onView(withText(R.string.word_search_title)).perform(click());
            searchFor("zzzz");
            onView(withText(R.string.word_search_empty)).check(matches(isDisplayed()));
            String peach = "桃 · táo · peach";
            for (String query : new String[] {" PEACH ", "tao", "　桃　"}) {
                searchFor(query);
                onView(withText(peach)).check(matches(isDisplayed()));
            }
            onView(withId(R.id.wordQuery)).perform(click(), replaceText("tao"));
            onView(withText(peach)).check(matches(isCompletelyDisplayed()));
            UiEvidence.capture("word_browser_search_keyboard");
            searchFor("桃");

            UiEvidence.capture("word_browser_search");
            onView(withText(peach)).perform(click());
            assertCard("桃");
            pressBack();
            onView(withText(peach)).check(matches(isDisplayed()));
            onView(withId(R.id.wordQuery)).perform(closeSoftKeyboard());
            onView(withContentDescription(R.string.nav_back)).perform(click());
            assertWordList("Animals");

            setRecordedWords(true);
            openWordMenu();
            onView(withText(R.string.word_review_recorded)).perform(click());
            assertCard("狗");
            pressBack();
            assertWordList("Animals");
        }
    }

    @Test
    public void searchRestoresQueryAndResults() throws InterruptedException {
        try (ActivityScenario<WordSearchActivity> scenario = ActivityScenario.launch(WordSearchActivity.class)) {
            searchFor("桃");
            scenario.recreate();
            awaitContent();
            onView(withId(R.id.wordQuery)).check(matches(withText("桃")));
            onView(withText("桃 · táo · peach")).check(matches(isDisplayed()));
        }
    }

    private static void setRecordedWords(boolean recorded) throws InterruptedException {
        LittleStarApp app = ApplicationProvider.getApplicationContext();
        app.container().store.update(state -> {
            state.words.clear();
            if (recorded) {
                ProgressState.WordProgress peach = new ProgressState.WordProgress();
                peach.learnedAt = 1L;
                state.words.put("tao", peach);
                ProgressState.WordProgress dog = new ProgressState.WordProgress();
                dog.learnedAt = 2L;
                state.words.put("gou", dog);
            }
        });
        awaitContent();
    }

    private static void searchFor(String query) throws InterruptedException {
        awaitContent();
        onView(withId(R.id.wordQuery)).perform(replaceText(query), closeSoftKeyboard());
    }

    private static void assertCard(String hanzi) throws InterruptedException {
        awaitContent();
        onView(allOf(withId(R.id.pageHanzi), withText(hanzi), isDisplayed())).check(matches(isDisplayed()));
    }

    private static void openWordMenu() {
        openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext());
    }

    private static void selectTheme(String name) throws InterruptedException {
        awaitContent();
        onView(allOf(withId(R.id.themeNameEn), withText(name))).perform(click());
    }

    private static void assertWordList(String name) throws InterruptedException {
        awaitContent();
        onView(withId(R.id.wordGrid)).check(matches(isDisplayed()));
        onView(withId(R.id.listTitle)).check(matches(withText(name)));
    }

    private static void awaitContent() throws InterruptedException {
        LittleStarApp app = ApplicationProvider.getApplicationContext();
        AppExecutors executors = app.container().executors;
        CountDownLatch ready = new CountDownLatch(1);
        executors.diskIO().execute(() -> executors.mainThread().execute(ready::countDown));
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }
}
