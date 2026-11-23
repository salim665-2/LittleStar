package com.littlestar.app.notify;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

/** Clears just the notification the user dismissed. */
public final class ReminderDismissReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        int id = intent.getIntExtra(Notifier.EXTRA_NOTIFICATION_ID, -1);
        if (id == Notifier.ID_REMINDER || id == Notifier.ID_TEST) {
            context.getSystemService(NotificationManager.class).cancel(id);
        }
    }
}
