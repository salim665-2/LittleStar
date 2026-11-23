package com.littlestar.app;

import android.graphics.Rect;
import android.view.View;

import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.feature.words.ThemesActivity;
import com.littlestar.app.feature.words.ThemesFragment;
import com.littlestar.app.feature.words.WordListFragment;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;
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
    public void menuFiltersWordsAndPracticesTheVisibleCards() throws Exception {
        setRecordedAnimalWords(1);
        try (ActivityScenario<ThemesActivity> scenario = ActivityScenario.launch(ThemesActivity.class)) {
            onView(withId(R.id.browserToolbar)).check(matches(isDisplayed()));
            selectTheme("Animals");
            assertWordList("Animals");
            assertWordCount(scenario, 6);
            openWordMenu();
            onView(withText(R.string.wl_practice)).check(matches(isDisplayed()));
            UiEvidence.capture("word_browser_options");
            onView(withText(R.string.wl_show_unrecorded)).perform(click());
            assertWordCount(scenario, 5);

            scenario.recreate();
            assertWordList("Animals");
            assertWordCount(scenario, 5);
            openWordMenu();
            onView(withText(R.string.wl_practice)).perform(click());
            awaitContent();
            onView(allOf(withId(R.id.pageHanzi), withText("狗"), isDisplayed()))
                    .check(matches(isDisplayed()));
            pressBack();
            assertWordList("Animals");

            setRecordedAnimalWords(6);
            assertWordCount(scenario, 0);
            onView(withId(R.id.wordGridEmpty)).check(matches(isDisplayed()));
            openWordMenu();
            onView(withText(R.string.wl_show_unrecorded)).perform(click());
            assertWordCount(scenario, 6);
        }
    }

    private static void setRecordedAnimalWords(int count) throws InterruptedException {
        LittleStarApp app = ApplicationProvider.getApplicationContext();
        app.container().store.update(state -> {
            String[] wordIds = {"mao", "gou", "niao", "yu", "ma", "niu"};
            for (int index = 0; index < wordIds.length; index++) {
                String wordId = wordIds[index];
                if (index < count) {
                    ProgressState.WordProgress wordProgress = new ProgressState.WordProgress();
                    wordProgress.learnedAt = 1L;
                    state.words.put(wordId, wordProgress);
                } else {
                    state.words.remove(wordId);
                }
            }
        });
        awaitContent();
    }

    private static void assertWordCount(ActivityScenario<ThemesActivity> scenario, int count) {
        scenario.onActivity(activity -> {
            RecyclerView grid = activity.findViewById(R.id.wordGrid);
            assertEquals(count, grid.getAdapter().getItemCount());
        });
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
