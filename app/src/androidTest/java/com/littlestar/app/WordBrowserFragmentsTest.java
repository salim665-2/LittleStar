package com.littlestar.app;

import android.graphics.Rect;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.littlestar.app.feature.words.ThemesActivity;
import com.littlestar.app.feature.words.ThemesFragment;
import com.littlestar.app.feature.words.WordListFragment;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;
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
    public void themeQuizCanBeReopenedAfterReturning() throws InterruptedException {
        try (ActivityScenario<ThemesActivity> scenario = ActivityScenario.launch(ThemesActivity.class)) {
            selectTheme("Animals");
            assertWordList("Animals");
            for (int attempt = 0; attempt < 2; attempt++) {
                onView(withId(R.id.btnQuiz)).check(matches(isEnabled())).perform(click());
                awaitContent();
                onView(withId(R.id.option0)).check(matches(isDisplayed()));
                pressBack();
                onView(withId(R.id.listTitle)).check(matches(withText("Animals")));
            }
        }
    }

    @Test
    public void rebuildingTheWordListDiscardsPendingQuizLaunch() throws InterruptedException {
        try (ActivityScenario<ThemesActivity> scenario = ActivityScenario.launch(ThemesActivity.class)) {
            selectTheme("Animals");
            assertWordList("Animals");
            scenario.onActivity(activity -> {
                FragmentManager fragments = activity.getSupportFragmentManager();
                Fragment wordList = fragments.findFragmentById(R.id.wordsPane);
                // Recreate the view before the quiz launch callback can run on the main thread.
                assertTrue(wordList.requireView().findViewById(R.id.btnQuiz).performClick());
                fragments.beginTransaction().detach(wordList).commitNow();
                fragments.beginTransaction().attach(wordList).commitNow();
            });
            assertWordList("Animals");
            onView(withId(R.id.btnQuiz)).check(matches(isEnabled()));
        }
    }

    @Test
    public void menuResumesTheLastViewedCardForTheSelectedTheme() throws Exception {
        LittleStarApp app = ApplicationProvider.getApplicationContext();
        app.container().store.update(state -> state.lastBrowsedWords.clear());
        awaitContent();
        try (ActivityScenario<ThemesActivity> scenario = ActivityScenario.launch(ThemesActivity.class)) {
            onView(withContentDescription(androidx.appcompat.R.string.abc_action_menu_overflow_description))
                    .check(doesNotExist());
            selectTheme("Animals");
            assertWordList("Animals");
            selectMenuItem(R.string.wl_start_browsing);
            assertCard("猫");
            onView(withId(R.id.btnNext)).perform(scrollTo(), click());
            assertCard("狗");
            pressBack();

            scenario.recreate();
            assertWordList("Animals");
            openWordMenu();
            onView(withText(R.string.wl_continue_browsing)).check(matches(isCompletelyDisplayed()));
            onView(withText(R.string.wl_play_theme)).check(matches(isCompletelyDisplayed()));
            UiEvidence.capture("word_browser_options");
            onView(withText(R.string.wl_continue_browsing)).perform(click());
            assertCard("狗");
            pressBack();
        }

        try (ActivityScenario<ThemesActivity> ignored = ActivityScenario.launch(ThemesActivity.class)) {
            selectTheme("Fruits");
            assertWordList("Fruits");
            openWordMenu();
            onView(withText(R.string.wl_start_browsing)).check(matches(isDisplayed()));
            pressBack();
        }
    }

    @Test
    public void menuPlaysThemeInOrderAndStopsWhenRequestedOrLeaving() throws Exception {
        LittleStarApp app = ApplicationProvider.getApplicationContext();
        CountDownLatch second = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        List<String> captions = new ArrayList<>();
        List<String> expected = List.of("猫", "狗", "鸟", "鱼", "马", "牛").stream()
                .map(word -> app.getString(R.string.wl_now_playing, word))
                .collect(Collectors.toList());
        try (ActivityScenario<ThemesActivity> scenario = ActivityScenario.launch(ThemesActivity.class)) {
            selectTheme("Animals");
            assertWordList("Animals");
            scenario.onActivity(activity -> {
                TextView status = activity.findViewById(R.id.wordListHint);
                status.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        String caption = s.toString();
                        if (caption.equals(app.getString(R.string.wl_hint))) {
                            if (captions.size() == expected.size()) {
                                finished.countDown();
                            }
                        } else {
                            captions.add(caption);
                            if (caption.equals(expected.get(1))) {
                                second.countDown();
                            }
                        }
                    }

                    @Override
                    public void afterTextChanged(Editable s) {}
                });
            });

            selectMenuItem(R.string.wl_play_theme);
            onView(withId(R.id.stopThemeAudio)).check(matches(isCompletelyDisplayed()));
            assertTrue("The second pronunciation should start automatically", second.await(8, TimeUnit.SECONDS));
            UiEvidence.capture("word_browser_theme_audio");
            onView(withId(R.id.stopThemeAudio)).perform(click());
            assertThemeAudioStopped();

            scenario.onActivity(activity -> captions.clear());
            selectMenuItem(R.string.wl_play_theme);
            assertTrue("The playlist should reach the end", finished.await(15, TimeUnit.SECONDS));
            assertEquals(expected, captions);
            assertThemeAudioStopped();

            selectMenuItem(R.string.wl_play_theme);
            onView(withId(R.id.stopThemeAudio)).check(matches(isDisplayed()));
            if (app.getResources().getConfiguration().screenWidthDp < 600) {
                pressBack();
                onView(withId(R.id.themesList)).check(matches(isDisplayed()));
            }
            selectTheme("Fruits");
            assertWordList("Fruits");
            assertThemeAudioStopped();
        }
    }

    private static void assertThemeAudioStopped() throws InterruptedException {
        awaitContent();
        onView(withId(R.id.stopThemeAudio)).check(matches(not(isDisplayed())));
        onView(withId(R.id.wordListHint)).check(matches(withText(R.string.wl_hint)));
        LittleStarApp app = ApplicationProvider.getApplicationContext();
        assertFalse(Boolean.TRUE.equals(app.container().speech.isPlaying().getValue()));
    }

    private static void assertCard(String hanzi) throws InterruptedException {
        awaitContent();
        onView(allOf(withId(R.id.pageHanzi), withText(hanzi), isDisplayed())).check(matches(isDisplayed()));
    }

    private static void openWordMenu() {
        openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext());
    }

    private static void selectMenuItem(int title) {
        openWordMenu();
        onView(withText(title)).perform(click());
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
