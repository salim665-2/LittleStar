package com.littlestar.app.notify;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.littlestar.app.R;
import com.littlestar.app.core.time.AppClock;
import com.littlestar.app.feature.home.HomeActivity;

import java.time.Duration;
import java.time.ZonedDateTime;

/**
 * 通知出口（§10.3）。每日学习提醒；媒体通知由 Media3 管理。
 * 文案进 strings.xml；小图标 ic_stat_panda。
 * Reference consulted: Android Developers — Create a notification & action buttons
 * (https://developer.android.com/develop/ui/views/notifications/build-notification)
 */
public final class Notifier {

    public static final String CHANNEL_REMINDER = "reminder";

    public static final int ID_REMINDER = 1001;

    /** 家长设置菜单「发送测试通知」专用；与每日提醒分开，避免覆盖。 */
    public static final int ID_TEST = 1099;

    private static final int REQUEST_START_LEARNING = 1003;
    static final String EXTRA_NOTIFICATION_ID = "notificationId";

    private final Context app;
    private final NotificationManager nm;
    private final AppClock clock;

    public Notifier(@NonNull Context context, @NonNull AppClock clock) {
        this.app = context.getApplicationContext();
        this.nm = app.getSystemService(NotificationManager.class);
        this.clock = clock;
    }

    /** App 启动时建渠道（幂等）。 */
    public void ensureChannels() {
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_REMINDER,
                app.getString(R.string.notif_channel_reminder), NotificationManager.IMPORTANCE_DEFAULT));
        // 升级时清理已删除的休息提醒渠道及遗留通知。
        nm.cancel(1002);
        nm.deleteNotificationChannel("rest");
    }

    /** 每日学习提醒：「该学汉字啦：今天还有 N 个字」。 */
    public void reminder(int remainingWords) {
        String text = app.getString(R.string.notif_reminder_text, remainingWords);
        NotificationCompat.Builder notification = reminderNotification(ID_REMINDER,
                app.getString(R.string.notif_reminder_title), text, new Intent(app, HomeActivity.class));
        PendingIntent startLearning = activityAction(REQUEST_START_LEARNING,
                HomeActivity.learnFromReminderIntent(app));
        ZonedDateTime now = clock.now().atZone(clock.zone());
        long remainingTodayMillis = Duration.between(now.toInstant(),
                now.toLocalDate().plusDays(1).atStartOfDay(now.getZone()).toInstant()).toMillis();
        notification.addAction(R.drawable.ic_play, app.getString(R.string.notif_action_learn), startLearning)
                .addAction(dismissAction(ID_REMINDER))
                .setTimeoutAfter(Math.max(1, remainingTodayMillis));
        nm.notify(ID_REMINDER, notification.build());
    }

    /** 家长设置菜单的测试通知：立即展示，用于确认通知权限和渠道正常。 */
    public void test(@NonNull Intent tapIntent) {
        Notification notification = reminderNotification(ID_TEST,
                app.getString(R.string.notif_test_title),
                app.getString(R.string.notif_test_text), tapIntent)
                .addAction(dismissAction(ID_TEST))
                .build();
        nm.notify(ID_TEST, notification);
    }

    public void cancelDailyReminder() {
        nm.cancel(ID_REMINDER);
    }

    private NotificationCompat.Action dismissAction(int id) {
        Intent dismiss = new Intent(app, ReminderDismissReceiver.class)
                .putExtra(EXTRA_NOTIFICATION_ID, id);
        PendingIntent action = PendingIntent.getBroadcast(app, id, dismiss,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Action(R.drawable.ic_close,
                app.getString(R.string.notif_action_dismiss), action);
    }

    private NotificationCompat.Builder reminderNotification(int id, String title,
                                                             String text, Intent tapIntent) {
        return new NotificationCompat.Builder(app, CHANNEL_REMINDER)
                .setSmallIcon(R.drawable.ic_stat_panda)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(activityAction(id, tapIntent))
                .setAutoCancel(true);
    }

    private PendingIntent activityAction(int requestCode, Intent intent) {
        // A notification must show Home itself, not merely bring an existing
        // task (which may have Settings on top) to the foreground.
        Intent launchIntent = new Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        // UPDATE_CURRENT refreshes extras but can retain an older token's launch
        // flags after an app update; recreate that token to apply this route.
        return PendingIntent.getActivity(app, requestCode, launchIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
