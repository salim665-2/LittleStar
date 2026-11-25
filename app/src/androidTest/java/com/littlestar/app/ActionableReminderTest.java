package com.littlestar.app;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.littlestar.app.data.store.ProgressState;
import com.littlestar.app.feature.flashcard.FlashcardActivity;
import com.littlestar.app.feature.home.HomeActivity;
import com.littlestar.app.notify.Notifier;
import com.littlestar.app.notify.ReminderReceiver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

@RunWith(AndroidJUnit4.class)
public class ActionableReminderTest {

    private final LittleStarApp app = ApplicationProvider.getApplicationContext();
    private final AppContainer container = app.container();
    private final NotificationManager notifications = app.getSystemService(NotificationManager.class);
    private Set<Integer> previousDays;
    private boolean previouslyOnboarded;

    @Before
    public void prepare() {
        // 通知权限是设备状态：由用例自己授予，避免依赖其它测试的执行顺序。
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .grantRuntimePermission(app.getPackageName(),
                        android.Manifest.permission.POST_NOTIFICATIONS);
        container.notifier.ensureChannels();
        assumeTrue("Enable notifications for this test", notifications.areNotificationsEnabled());
        assumeTrue("Enable the daily reminder channel",
                notifications.getNotificationChannel(Notifier.CHANNEL_REMINDER).getImportance()
                        != NotificationManager.IMPORTANCE_NONE);
        previousDays = container.settings.getReminderDays();
        previouslyOnboarded = container.settings.isOnboarded();
        container.settings.setReminderDays(Set.of(container.clock.today().getDayOfWeek().getValue()));
        container.settings.setOnboarded(true);
        notifications.cancel(Notifier.ID_REMINDER);
        notifications.cancel(Notifier.ID_TEST);
    }

    @After
    public void restoreSettings() {
        if (previousDays != null) {
            notifications.cancel(Notifier.ID_REMINDER);
            notifications.cancel(Notifier.ID_TEST);
            container.settings.setReminderDays(previousDays);
            container.settings.setOnboarded(previouslyOnboarded);
            container.reminders.reschedule();
        }
    }

    @Test
    public void dismissClearsOnlyTheDailyNotification() throws Exception {
        setLearned(false);
        container.notifier.test(new Intent(app, HomeActivity.class));
        fireReminder();
        Notification reminder = awaitReminder();
        assertTrue(reminder.getTimeoutAfter() > 0);
        assertEquals(2, reminder.actions.length);
        assertEquals(app.getString(R.string.notif_action_learn), reminder.actions[0].title.toString());
        assertEquals(app.getString(R.string.notif_action_dismiss), reminder.actions[1].title.toString());

        long nextReminder = container.reminders.nextTriggerMillis();
        reminder.actions[1].actionIntent.send();
        long deadline = SystemClock.elapsedRealtime() + 5_000;
        while (activeReminder() != null && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50);
        }
        assertNull(activeReminder());
        assertTrue(Arrays.stream(notifications.getActiveNotifications())
                .anyMatch(entry -> entry.getId() == Notifier.ID_TEST));
        assertEquals(nextReminder, container.reminders.nextTriggerMillis());
    }

    @Test
    public void learnNowOpensDailyFlashcardFromExistingHome() throws Exception {
        setLearned(false);
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            fireReminder();
            Notification reminder = awaitReminder();
            reminder.actions[0].actionIntent.send();
            AtomicReference<FlashcardActivity> opened = new AtomicReference<>();
            long deadline = SystemClock.elapsedRealtime() + 5_000;
            while (opened.get() == null && SystemClock.elapsedRealtime() < deadline) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                                .stream().filter(FlashcardActivity.class::isInstance).findFirst()
                                .ifPresent(activity -> opened.set((FlashcardActivity) activity)));
                if (opened.get() == null) {
                    SystemClock.sleep(50);
                }
            }
            assertNotNull("Learn now should open the flashcard activity", opened.get());
            assertEquals("SESSION", opened.get().getIntent().getStringExtra("mode"));
            assertNull(activeReminder());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(opened.get()::finish);
        }
    }

    @Test
    public void skipsNotificationWhenNothingIsLeftToLearn() throws Exception {
        setLearned(true);
        fireReminder();
        assertNull(activeReminder());
    }

    private void setLearned(boolean learned) {
        container.store.update(state -> {
            state.dailyPlans.clear();
            state.sessions.clear();
            state.words.clear();
            if (learned) {
                container.content.ensureLoaded();
                for (var word : container.content.wordsInOrder()) {
                    ProgressState.WordProgress progress = new ProgressState.WordProgress();
                    progress.learnedAt = container.clock.now().toEpochMilli();
                    state.words.put(word.id, progress);
                }
            }
        });
    }

    private void fireReminder() throws InterruptedException {
        CountDownLatch delivered = new CountDownLatch(1);
        app.sendOrderedBroadcast(new Intent(app, ReminderReceiver.class), null, new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                delivered.countDown();
            }
        }, null, Activity.RESULT_OK, null, null);
        assertTrue("Reminder receiver did not finish", delivered.await(10, TimeUnit.SECONDS));
    }

    private Notification activeReminder() {
        return Arrays.stream(notifications.getActiveNotifications())
                .filter(entry -> entry.getId() == Notifier.ID_REMINDER)
                .map(entry -> entry.getNotification())
                .findFirst().orElse(null);
    }

    private Notification awaitReminder() {
        long deadline = SystemClock.elapsedRealtime() + 5_000;
        Notification current = activeReminder();
        while (current == null && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50);
            current = activeReminder();
        }
        assertNotNull("The daily reminder was not posted", current);
        return current;
    }
}
