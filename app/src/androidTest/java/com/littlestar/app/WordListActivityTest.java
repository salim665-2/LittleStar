package com.littlestar.app;

import android.content.Context;
import android.content.Intent;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.littlestar.app.feature.words.WordListActivity;
import com.littlestar.app.feature.words.WordListFragment;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class WordListActivityTest {

    @Test
    public void hostsTheWordListForAThemeOpenedDirectly() throws InterruptedException {
        awaitContent();
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, WordListActivity.class).putExtra("themeId", "fruits");
        try (ActivityScenario<WordListActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.wordsPane);
                assertTrue(fragment instanceof WordListFragment);
            });
            onView(withId(R.id.wordGrid)).check(matches(isDisplayed()));
            onView(withId(R.id.listTitle)).check(matches(withText("Fruits")));
        }
    }

    private static void awaitContent() throws InterruptedException {
        LittleStarApp app = ApplicationProvider.getApplicationContext();
        CountDownLatch ready = new CountDownLatch(1);
        app.container().executors.diskIO()
                .execute(() -> app.container().executors.mainThread().execute(ready::countDown));
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }
}
