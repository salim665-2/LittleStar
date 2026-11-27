package com.littlestar.app.playback;

import android.app.PendingIntent;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSession.ControllerInfo;
import androidx.media3.session.MediaSessionService;

import com.littlestar.app.R;
import com.littlestar.app.feature.home.HomeActivity;

/**
 * Long-form cultural audio playback service: manages background ExoPlayer playback and
 * configures the actionable system MediaSession notification with branded LittleStar icon.
 * References consulted:
 * - Android Developers: Background playback with MediaSessionService
 *   (https://developer.android.com/media/media3/session/background-playback)
 * - Android Developers: Customize media notifications with DefaultMediaNotificationProvider
 *   (https://developer.android.com/media/media3/session/control-playback#notification)
 */
public final class PlaybackService extends MediaSessionService {

    public static final String CHANNEL_MEDIA = "media_playback";
    private static volatile boolean running;

    public static boolean isRunning() {
        return running;
    }

    @Nullable
    private MediaSession session;
    @Nullable
    private ExoPlayer player;

    @Override
    @UnstableApi
    public void onCreate() {
        super.onCreate();
        DefaultMediaNotificationProvider notificationProvider =
                new DefaultMediaNotificationProvider.Builder(this)
                        .setChannelId(CHANNEL_MEDIA)
                        .setChannelName(R.string.notif_channel_media)
                        .build();
        notificationProvider.setSmallIcon(R.drawable.ic_stat_panda);
        setMediaNotificationProvider(notificationProvider);

        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(), true)
                .setHandleAudioBecomingNoisy(true)
                .build();

        Intent openHome = new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent sessionActivity = PendingIntent.getActivity(
                this, 0, openHome,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        session = new MediaSession.Builder(this, player)
                .setSessionActivity(sessionActivity)
                .build();
        running = true;
    }

    @Override
    public void onTaskRemoved(@Nullable Intent rootIntent) {
        if (player != null && !player.isPlaying()) {
            stopSelf();
        }
    }

    @Nullable
    @Override
    public MediaSession onGetSession(@NonNull ControllerInfo controllerInfo) {
        return session;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (session != null) {
            session.release();
            session = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
