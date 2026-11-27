package com.littlestar.app.feature.culture;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Dismisses a pinned cultural storytime notification when the user taps the Dismiss action button.
 * Reference consulted: Android Developers — Create an expandable and actionable notification
 * (https://developer.android.com/develop/ui/views/notifications/build-notification#Actions)
 */
public final class CultureNotificationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int id = intent.getIntExtra(CultureNotifier.EXTRA_NOTIFICATION_ID, CultureNotifier.ID_STORYTIME);
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.cancel(id);
        }
    }
}
