package com.littlestar.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;

import androidx.fragment.app.FragmentManager;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.util.concurrent.ListenableFuture;
import com.littlestar.app.data.content.model.CultureItem;
import com.littlestar.app.feature.culture.CultureCategoriesActivity;
import com.littlestar.app.feature.culture.CultureCategoriesFragment;
import com.littlestar.app.feature.culture.CultureDetailActivity;
import com.littlestar.app.feature.culture.CultureListFragment;
import com.littlestar.app.feature.culture.CultureNotifier;
import com.littlestar.app.feature.player.PlayerActivity;
import com.littlestar.app.playback.PlaybackService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.action.ViewActions.swipeLeft;
import static androidx.test.espresso.action.ViewActions.swipeRight;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

@RunWith(AndroidJUnit4.class)
public final class CultureBrowserFragmentsTest {

    private Context context;
    private AppContainer container;
    private NotificationManager notifications;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        container = ((LittleStarApp) context).container();
        container.content.ensureLoaded();
        notifications = context.getSystemService(NotificationManager.class);
        CultureNotifier.cancel(context);
    }

    @After
    public void tearDown() {
        CultureNotifier.cancel(context);
        context.stopService(new Intent(context, PlaybackService.class));
    }

    @Test
    public void switchesCultureFragmentsAndAdaptsToScreenWidth() throws Exception {
        try (ActivityScenario<CultureCategoriesActivity> scenario =
                     ActivityScenario.launch(CultureCategoriesActivity.class)) {
            awaitContent();
            scenario.onActivity(activity -> {
                assertTrue(activity.getSupportFragmentManager().findFragmentById(R.id.categoriesPane)
                        instanceof CultureCategoriesFragment);
                assertNull(activity.getSupportFragmentManager().findFragmentById(R.id.cultureListPane));
            });

            onView(withId(R.id.catSongs)).perform(click());
            awaitContent();
            onView(withId(R.id.categoryName)).check(matches(withText("Songs")));

            scenario.recreate();
            awaitContent();
            onView(withId(R.id.categoryName)).check(matches(withText("Songs")));

            boolean[] wide = new boolean[1];
            scenario.onActivity(activity -> {
                FragmentManager fragments = activity.getSupportFragmentManager();
                assertTrue(fragments.findFragmentById(R.id.cultureListPane) instanceof CultureListFragment);
                View categories = activity.findViewById(R.id.categoriesPane);
                View list = activity.findViewById(R.id.cultureListPane);
                wide[0] = activity.isTwoPane();
                assertTrue(list.isShown());
                if (wide[0]) {
                    Rect left = new Rect();
                    Rect right = new Rect();
                    assertTrue(categories.getGlobalVisibleRect(left));
                    assertTrue(list.getGlobalVisibleRect(right));
                    assertFalse("Two-pane culture layout must keep columns separate",
                            Rect.intersects(left, right));
                } else {
                    assertFalse(categories.isShown());
                }
            });

            if (wide[0]) {
                onView(withId(R.id.catPoems)).perform(click());
                awaitContent();
                onView(withId(R.id.categoryName)).check(matches(withText("Poems")));
            } else {
                pressBack();
                onView(withId(R.id.catPoems)).check(matches(isDisplayed())).perform(click());
                awaitContent();
                onView(withId(R.id.categoryName)).check(matches(withText("Poems")));
                onView(withContentDescription(R.string.nav_back)).perform(click());
                onView(withId(R.id.catSongs)).check(matches(isDisplayed()));
            }
        }
    }

    @Test
    public void toolbarMenuOpensListeningTipsDialogAndLongPressPreviewsItem() throws Exception {
        CultureItem song = container.content.itemsOfCategory("songs").get(0);
        try (ActivityScenario<CultureCategoriesActivity> scenario =
                     ActivityScenario.launch(CultureCategoriesActivity.class)) {
            awaitContent();
            openActionBarOverflowOrOptionsMenu(context);
            onView(withText(R.string.culture_menu_tips)).perform(click());
            onView(withText(R.string.culture_tips_title)).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(R.string.culture_tips_ok)).inRoot(isDialog()).perform(click());

            onView(withId(R.id.catSongs)).perform(click());
            awaitContent();
            onView(withText(song.titleEn)).perform(longClick());
            onView(withText(containsString(song.learningFocus)))
                    .inRoot(isDialog())
                    .check(matches(isDisplayed()));
            onView(withText(R.string.culture_preview_close)).inRoot(isDialog()).perform(click());
        }
    }

    @Test
    public void coverSwipeSwitchesBetweenNeighbouringTracks() throws Exception {
        ArrayList<String> queue = new ArrayList<>(List.of("xiaoxingxing", "liangzhilaohu"));
        AtomicReference<ListenableFuture<MediaController>> future = new AtomicReference<>();
        try (ActivityScenario<PlayerActivity> scenario = ActivityScenario.launch(
                new Intent(context, PlayerActivity.class)
                        .putExtra("itemId", queue.get(0))
                        .putStringArrayListExtra("queue", queue))) {
            onMain(() -> future.set(new MediaController.Builder(context,
                    new SessionToken(context, new ComponentName(context, PlaybackService.class)))
                    .buildAsync()));
            MediaController controller = future.get().get(10, TimeUnit.SECONDS);
            waitFor(() -> controller.getMediaItemCount() == 2 && controller.isPlaying());

            onView(withId(R.id.coverFrame)).perform(swipeLeft());
            waitFor(() -> controller.getCurrentMediaItem() != null
                    && "liangzhilaohu".equals(controller.getCurrentMediaItem().mediaId));
            onView(withId(R.id.songTitle)).check(matches(withText("两只老虎")));

            onView(withId(R.id.coverFrame)).perform(swipeRight());
            waitFor(() -> controller.getCurrentMediaItem() != null
                    && "xiaoxingxing".equals(controller.getCurrentMediaItem().mediaId));
            onView(withId(R.id.songTitle)).check(matches(withText("小星星")));
        } finally {
            if (future.get() != null) {
                MediaController controller = future.get().get(10, TimeUnit.SECONDS);
                onMain(() -> {
                    controller.stop();
                    controller.clearMediaItems();
                    controller.release();
                });
            }
        }
    }

    @Test
    public void storytimeNotificationProvidesPlayReadAndDismissActions() throws Exception {
        CultureNotifier notifier = new CultureNotifier(context);
        assumeTrue("Enable notifications to verify storytime actions", notifier.canPostNotifications());
        CultureItem poem = container.content.itemById("jingyesi");
        notifier.postStorytimeCard(poem, List.of("jingyesi", "yonge", "chunxiao"));

        Notification posted = awaitStorytimeNotification();
        assertEquals(3, posted.actions.length);
        assertEquals(context.getString(R.string.notif_culture_action_play),
                posted.actions[0].title.toString());
        assertEquals(context.getString(R.string.notif_culture_action_read),
                posted.actions[1].title.toString());
        assertEquals(context.getString(R.string.notif_action_dismiss),
                posted.actions[2].title.toString());

        posted.actions[2].actionIntent.send();
        long deadline = SystemClock.elapsedRealtime() + 5_000;
        while (activeStorytimeNotification() != null && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50);
        }
        assertNull(activeStorytimeNotification());

        try (ActivityScenario<CultureDetailActivity> scenario = ActivityScenario.launch(
                CultureDetailActivity.notificationIntent(context, poem.id, true))) {
            awaitContent();
            onView(withId(R.id.detailTranscript)).check(matches(isDisplayed()));
        }
    }

    private Notification activeStorytimeNotification() {
        return Arrays.stream(notifications.getActiveNotifications())
                .filter(entry -> entry.getId() == CultureNotifier.ID_STORYTIME)
                .map(entry -> entry.getNotification())
                .findFirst()
                .orElse(null);
    }

    private Notification awaitStorytimeNotification() {
        long deadline = SystemClock.elapsedRealtime() + 5_000;
        Notification current = activeStorytimeNotification();
        while (current == null && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50);
            current = activeStorytimeNotification();
        }
        assertNotNull("Storytime notification was not posted", current);
        return current;
    }

    private void awaitContent() throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);
        container.executors.diskIO().execute(() -> container.executors.mainThread().execute(ready::countDown));
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    private static void onMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }

    private static void waitFor(BooleanSupplier condition) {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        AtomicBoolean ready = new AtomicBoolean();
        do {
            onMain(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) {
                return;
            }
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        assertTrue("Condition timed out", ready.get());
    }
}
