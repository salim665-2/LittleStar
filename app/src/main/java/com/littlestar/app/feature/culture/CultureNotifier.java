package com.littlestar.app.feature.culture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.littlestar.app.R;
import com.littlestar.app.data.content.model.CultureItem;
import com.littlestar.app.feature.player.PlayerActivity;

import java.util.List;

/**
 * Builds and posts actionable cultural storytime notifications so parents can jump directly
 * to audio playback ("Play now"), open the expanded transcript ("Read together"), or dismiss.
 * References consulted:
 * - Android Developers: Create a notification & action buttons
 *   (https://developer.android.com/develop/ui/views/notifications/build-notification)
 * - Android Developers: Notification runtime permission on Android 13+
 *   (https://developer.android.com/develop/ui/views/notifications/notification-permission)
 */
public final class CultureNotifier {

    public static final String CHANNEL_CULTURE = "culture_storytime";
    public static final int ID_STORYTIME = 1201;
    static final String EXTRA_NOTIFICATION_ID = "cultureNotificationId";

    private static final int REQ_CONTENT_OPEN = 1202;
    private static final int REQ_ACTION_PLAY = 1203;
    private static final int REQ_ACTION_READ = 1204;
    private static final int REQ_ACTION_DISMISS = 1205;

    private final Context app;
    private final NotificationManager nm;

    public CultureNotifier(@NonNull Context context) {
        this.app = context.getApplicationContext();
        this.nm = app.getSystemService(NotificationManager.class);
    }

    public void ensureChannel() {
        if (nm == null) {
            return;
        }
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_CULTURE,
                app.getString(R.string.notif_channel_culture),
                NotificationManager.IMPORTANCE_DEFAULT));
    }

    public boolean canPostNotifications() {
        if (nm == null || !nm.areNotificationsEnabled()) {
            return false;
        }
        ensureChannel();
        NotificationChannel channel = nm.getNotificationChannel(CHANNEL_CULTURE);
        return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    public void postStorytimeCard(@NonNull CultureItem item, @NonNull List<String> queue) {
        if (nm == null) {
            return;
        }
        ensureChannel();
        String title = app.getString(R.string.notif_culture_title, item.titleZh, item.titleEn);
        String expandedBody = item.summary + "\n"
                + app.getString(R.string.culture_detail_focus) + ": " + item.learningFocus;

        PendingIntent contentIntent = activityPendingIntent(
                REQ_CONTENT_OPEN,
                CultureDetailActivity.notificationIntent(app, item.id, false));
        PendingIntent playAction = activityPendingIntent(
                REQ_ACTION_PLAY,
                PlayerActivity.notificationIntent(app, item.id, queue));
        PendingIntent readAction = activityPendingIntent(
                REQ_ACTION_READ,
                CultureDetailActivity.notificationIntent(app, item.id, true));

        Intent dismissIntent = new Intent(app, CultureNotificationReceiver.class)
                .putExtra(EXTRA_NOTIFICATION_ID, ID_STORYTIME);
        PendingIntent dismissAction = PendingIntent.getBroadcast(
                app,
                REQ_ACTION_DISMISS,
                dismissIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(app, CHANNEL_CULTURE)
                .setSmallIcon(R.drawable.ic_stat_panda)
                .setContentTitle(title)
                .setContentText(item.learningFocus)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(expandedBody))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .addAction(R.drawable.ic_play,
                        app.getString(R.string.notif_culture_action_play), playAction)
                .addAction(R.drawable.ic_book,
                        app.getString(R.string.notif_culture_action_read), readAction)
                .addAction(R.drawable.ic_close,
                        app.getString(R.string.notif_action_dismiss), dismissAction)
                .build();

        nm.notify(ID_STORYTIME, notification);
    }

    public static void cancel(@NonNull Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.cancel(ID_STORYTIME);
        }
    }

    @NonNull
    private PendingIntent activityPendingIntent(int requestCode, @NonNull Intent intent) {
        Intent launch = new Intent(intent).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                app,
                requestCode,
                launch,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
