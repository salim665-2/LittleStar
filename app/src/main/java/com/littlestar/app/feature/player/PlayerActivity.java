package com.littlestar.app.feature.player;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.ListenableFuture;
import com.littlestar.app.R;
import com.littlestar.app.core.lifecycle.ViewModelFactory;
import com.littlestar.app.core.ui.ChildActivity;
import com.littlestar.app.core.ui.ContentImage;
import com.littlestar.app.core.ui.Insets;
import com.littlestar.app.core.ui.Motion;
import com.littlestar.app.data.content.model.CultureItem;
import com.littlestar.app.data.content.model.LyricLine;
import com.littlestar.app.databinding.ActivityPlayerBinding;
import com.littlestar.app.feature.culture.CultureNotifier;
import com.littlestar.app.playback.PlaybackControls;
import com.littlestar.app.playback.PlaybackService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cultural audio player: connects to {@link PlaybackService} via {@link MediaController},
 * synchronizes lyrics at 250ms intervals, supports horizontal swipe gestures on the album cover
 * to switch tracks, and presents a recovery dialog on playback errors.
 * References consulted:
 * - Android Developers: Detect common gestures with GestureDetector
 *   (https://developer.android.com/develop/ui/views/touch-and-input/gestures/detector)
 * - Android Developers: Connect a MediaController to a MediaSessionService
 *   (https://developer.android.com/media/media3/session/control-playback)
 */
public final class PlayerActivity extends ChildActivity {

    private static final String EXTRA_ITEM_ID = "itemId";
    private static final String EXTRA_QUEUE = "queue";
    private static final String EXTRA_PLAY = "play";
    private static final String EXTRA_FROM_NOTIFICATION = "fromCultureNotif";
    private static final String STATE_PLAYBACK_STARTED = "playback_started";
    private static final long PROGRESS_TICK_MS = 250L;
    private static final int MIN_SWIPE_DP = 48;

    @NonNull
    public static Intent intent(@NonNull Activity from, @NonNull String itemId,
                                @NonNull List<String> queue) {
        Intent i = new Intent(from, PlayerActivity.class);
        i.putExtra(EXTRA_ITEM_ID, itemId);
        i.putStringArrayListExtra(EXTRA_QUEUE, new ArrayList<>(queue));
        return i;
    }

    @NonNull
    public static Intent resumeIntent(@NonNull Activity from, @NonNull String itemId,
                                      @NonNull List<String> queue) {
        return intent(from, itemId, queue).putExtra(EXTRA_PLAY, false);
    }

    @NonNull
    public static Intent notificationIntent(@NonNull Context from, @NonNull String itemId,
                                            @NonNull List<String> queue) {
        Intent i = new Intent(from, PlayerActivity.class);
        i.putExtra(EXTRA_ITEM_ID, itemId);
        i.putStringArrayListExtra(EXTRA_QUEUE, new ArrayList<>(queue));
        i.putExtra(EXTRA_FROM_NOTIFICATION, true);
        return i;
    }

    private ActivityPlayerBinding binding;
    private ContentImage cover;
    private PlayerViewModel vm;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Nullable
    private MediaController controller;
    @Nullable
    private ListenableFuture<MediaController> controllerFuture;
    @Nullable
    private AlertDialog errorDialog;

    /** Tracks whether the queue has been handed to the controller during this visit. */
    private boolean playbackStarted;
    private boolean controllerInitialized;

    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            syncControllerState();
            handler.postDelayed(this, PROGRESS_TICK_MS);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        if (getResources().getConfiguration().fontScale >= 1.5f) {
            int size = getResources().getDimensionPixelSize(R.dimen.ls_player_cover_large_text);
            var coverParams = binding.coverFrame.getLayoutParams();
            coverParams.width = size;
            coverParams.height = size;
            binding.coverFrame.setLayoutParams(coverParams);
        }
        setContentView(binding.getRoot());
        cover = new ContentImage(binding.playerCover, container().executors, binding.coverGlyph);
        Insets.applySystemBarPadding(binding.getRoot());

        if (savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)) {
            CultureNotifier.cancel(this);
        }

        vm = new ViewModelProvider(this,
                ViewModelFactory.of(() -> new PlayerViewModel(container())))
                .get(PlayerViewModel.class);

        binding.btnBack.setOnClickListener(v -> {
            finish();
            Motion.childExit(this);
        });
        binding.btnPlay.setOnClickListener(v -> {
            MediaController c = controller;
            if (c != null && c.getMediaItemCount() > 0) {
                PlaybackControls.toggle(c);
                syncControllerState();
            }
        });
        binding.btnPrev.setOnClickListener(v -> {
            MediaController c = controller;
            if (c != null) {
                c.seekToPreviousMediaItem();
            }
        });
        binding.btnNext.setOnClickListener(v -> {
            MediaController c = controller;
            if (c != null) {
                c.seekToNextMediaItem();
            }
        });
        binding.progress.setOnSeek(pos -> {
            MediaController c = controller;
            if (c != null) {
                c.seekTo(pos);
            }
        });
        setupCoverTouchGestures();

        playbackStarted = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_PLAYBACK_STARTED);
        vm.state().observe(this, this::render);
        updateControls();

        List<String> queue = getIntent().getStringArrayListExtra(EXTRA_QUEUE);
        String itemId = getIntent().getStringExtra(EXTRA_ITEM_ID);
        container().content.load(container().executors, () -> {
            if (!isDestroyed()) {
                if (itemId == null || container().content.itemById(itemId) == null) {
                    finish();
                } else {
                    vm.init(itemId, queue != null ? queue : new ArrayList<>());
                }
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility") // Accessible prev/play/next buttons sit directly below coverFrame.
    private void setupCoverTouchGestures() {
        float minSwipePx = MIN_SWIPE_DP * getResources().getDisplayMetrics().density;
        int minFlingVelocity = ViewConfiguration.get(this).getScaledMinimumFlingVelocity();
        GestureDetector detector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(@NonNull MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                        MediaController c = controller;
                        if (c != null && controllerInitialized && c.getMediaItemCount() > 0) {
                            PlaybackControls.toggle(c);
                            syncControllerState();
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2,
                                           float velocityX, float velocityY) {
                        if (e1 == null) {
                            return false;
                        }
                        float dx = e2.getX() - e1.getX();
                        float dy = e2.getY() - e1.getY();
                        if (Math.abs(dx) > Math.abs(dy)
                                && Math.abs(dx) >= minSwipePx
                                && Math.abs(velocityX) >= minFlingVelocity) {
                            return handleCoverSwipe(dx < 0);
                        }
                        return false;
                    }
                });
        binding.coverFrame.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && v.getParent() != null) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            } else if ((event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) && v.getParent() != null) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return detector.onTouchEvent(event);
        });
    }

    public boolean handleCoverSwipe(boolean toNext) {
        MediaController c = controller;
        if (c == null || !c.isConnected() || !controllerInitialized) {
            return false;
        }
        if (toNext && c.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                && c.hasNextMediaItem()) {
            binding.coverFrame.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            c.seekToNextMediaItem();
            return true;
        }
        if (!toNext && c.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                && c.hasPreviousMediaItem()) {
            binding.coverFrame.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            c.seekToPreviousMediaItem();
            return true;
        }
        return false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        controllerInitialized = false;
        SessionToken token = new SessionToken(this,
                new ComponentName(this, PlaybackService.class));
        ListenableFuture<MediaController> future =
                new MediaController.Builder(this, token).buildAsync();
        controllerFuture = future;
        future.addListener(() -> {
            if (controllerFuture != future || isFinishing() || isDestroyed()) {
                return;
            }
            try {
                controller = future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                updateControls();
                return;
            } catch (java.util.concurrent.ExecutionException
                    | java.util.concurrent.CancellationException e) {
                updateControls();
                return;
            }
            controller.addListener(new Player.Listener() {
                @Override
                public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
                    if (controllerInitialized) {
                        syncControllerState();
                    }
                }

                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    syncControllerState();
                    showPlaybackErrorDialog();
                }
            });
            startPlaybackIfNeeded();
            updateControls();
            handler.removeCallbacks(progressTick);
            handler.post(progressTick);
        }, ContextCompat.getMainExecutor(this));
    }

    private void showPlaybackErrorDialog() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (errorDialog != null && errorDialog.isShowing()) {
            errorDialog.dismiss();
        }
        MediaController c = controller;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.player_error_title)
                .setMessage(R.string.player_error_body)
                .setNegativeButton(R.string.player_error_close, (d, w) -> {
                    finish();
                    Motion.childExit(this);
                });
        if (c != null && c.isConnected() && c.hasNextMediaItem()) {
            builder.setPositiveButton(R.string.player_error_next, (d, w) -> {
                if (controller != null && controller.isConnected() && controller.hasNextMediaItem()) {
                    controller.seekToNextMediaItem();
                    controller.prepare();
                }
            });
        }
        errorDialog = builder.show();
    }

    /** Rebuilds the service queue only when empty or different from the requested queue. */
    private void startPlaybackIfNeeded() {
        MediaController c = controller;
        PlayerViewModel.UiState s = vm.state().getValue();
        if (controllerInitialized || c == null || !c.isConnected() || s == null) {
            return;
        }
        controllerInitialized = true;
        MediaItem cur = c.getCurrentMediaItem();
        List<String> activeQueue = new ArrayList<>();
        for (int i = 0; i < c.getMediaItemCount(); i++) {
            activeQueue.add(c.getMediaItemAt(i).mediaId);
        }
        if (vm.canKeepPlayback(activeQueue, cur == null ? null : cur.mediaId, playbackStarted)) {
            if (!playbackStarted && getIntent().getBooleanExtra(EXTRA_PLAY, true)) {
                PlaybackControls.play(c);
            }
            playbackStarted = true;
            syncControllerState();
            return;
        }
        boolean resume = playbackStarted || !getIntent().getBooleanExtra(EXTRA_PLAY, true);
        playbackStarted = true;
        List<MediaItem> mediaItems = new ArrayList<>();
        for (String id : s.queue) {
            CultureItem it = container().content.itemById(id);
            if (it != null) {
                mediaItems.add(new MediaItem.Builder()
                        .setMediaId(it.id)
                        .setUri("asset:///" + it.audio)
                        .setMediaMetadata(new MediaMetadata.Builder()
                                .setTitle(it.titleEn)
                                .setDisplayTitle(it.titleEn)
                                .setSubtitle(it.titleZh)
                                .setArtist(it.subtitleEn)
                                .build())
                        .build());
            }
        }
        c.setMediaItems(mediaItems, s.queueIndex, resume ? s.positionMs : 0);
        c.prepare();
        c.setPlayWhenReady(!resume || s.playing);
        syncControllerState();
    }

    private void syncControllerState() {
        MediaController c = controller;
        if (c != null && c.isConnected() && controllerInitialized
                && vm.state().getValue() != null) {
            MediaItem current = c.getCurrentMediaItem();
            if (current != null) {
                vm.onTrackChanged(current.mediaId);
            }
            vm.onProgress(Math.max(0, c.getCurrentPosition()), Math.max(0, c.getDuration()),
                    c.getPlayWhenReady() && c.getPlaybackState() != Player.STATE_ENDED
                            && c.getPlayerError() == null);
        }
        updateControls();
    }

    private void updateControls() {
        MediaController c = controller;
        boolean ready = c != null && c.isConnected() && controllerInitialized
                && c.getMediaItemCount() > 0 && c.getPlayerError() == null;
        binding.btnPlay.setEnabled(ready);
        updateTrackControls();
        binding.progress.setEnabled(ready
                && c.isCurrentMediaItemSeekable() && c.getDuration() > 0);
    }

    /** Show whether a neighbouring item actually exists in the current category queue. */
    private void updateTrackControls() {
        MediaController c = controller;
        boolean ready = c != null && c.isConnected() && controllerInitialized
                && c.getMediaItemCount() > 0;
        boolean previous = ready
                && c.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                && c.hasPreviousMediaItem();
        boolean next = ready
                && c.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                && c.hasNextMediaItem();
        binding.btnPrev.setEnabled(previous);
        binding.btnNext.setEnabled(next);
        binding.btnPrev.setContentDescription(getString(previous
                ? R.string.player_desc_prev : R.string.player_desc_prev_unavailable));
        binding.btnNext.setContentDescription(getString(next
                ? R.string.player_desc_next : R.string.player_desc_next_unavailable));
    }

    private void render(PlayerViewModel.UiState s) {
        if (s == null) {
            return;
        }
        if (!controllerInitialized) {
            startPlaybackIfNeeded();
            s = vm.state().getValue();
        }
        binding.playerTitle.setText(s.item.titleEn);
        binding.songTitle.setText(s.item.titleZh);
        binding.coverGlyph.setText(s.item.glyph);
        cover.bind(s.item.cover);
        binding.songSubtitle.setText(s.item.subtitleEn);

        binding.playerSummary.setText(s.item.summary);
        binding.playLabel.setText(s.playing ? R.string.player_desc_pause : R.string.player_desc_play);
        binding.btnPlay.setIconResource(s.playing ? R.drawable.ic_pause : R.drawable.ic_play);
        binding.btnPlay.setContentDescription(
                getString(s.playing ? R.string.player_desc_pause : R.string.player_desc_play));

        binding.progress.setProgress(s.positionMs, s.durationMs);
        binding.timeCurrent.setText(formatMs(s.positionMs));
        binding.timeTotal.setText(formatMs(s.durationMs));

        renderLyrics(s);
        updateControls();
    }

    /** Highlights the active lyric line and previews up to two upcoming lines. */
    private void renderLyrics(PlayerViewModel.UiState s) {
        if (s.item.lyrics == null || s.item.lyrics.isEmpty() || s.lyricIndex < 0) {
            binding.lyricsCard.setVisibility(View.GONE);
            return;
        }
        binding.lyricsCard.setVisibility(View.VISIBLE);
        List<LyricLine> lyrics = s.item.lyrics;
        int i = s.lyricIndex;
        bindLyric(lyrics, i, binding.lyricCurrent, binding.lyricCurrentPinyin,
                binding.lyricCurrentEnglish);
        bindLyric(lyrics, i + 1, binding.lyricNext1, binding.lyricNext1Pinyin, null);
        bindLyric(lyrics, i + 2, binding.lyricNext2, binding.lyricNext2Pinyin, null);
    }

    private void bindLyric(List<LyricLine> lyrics, int index,
                           android.widget.TextView hanzi, android.widget.TextView pinyin,
                           @Nullable android.widget.TextView english) {
        if (index < lyrics.size()) {
            LyricLine line = lyrics.get(index);
            hanzi.setText(line.hanzi);
            pinyin.setText(line.pinyin);
            pinyin.setVisibility(View.VISIBLE);
            if (english != null) {
                english.setText(line.english);
                english.setVisibility(View.VISIBLE);
            }
        } else {
            hanzi.setText("");
            pinyin.setVisibility(View.GONE);
            if (english != null) {
                english.setVisibility(View.GONE);
            }
        }
    }

    @NonNull
    private static String formatMs(long ms) {
        if (ms <= 0) {
            return "0:00";
        }
        long totalSec = ms / 1000;
        return String.format(Locale.ROOT, "%d:%02d", totalSec / 60, totalSec % 60);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean(STATE_PLAYBACK_STARTED, playbackStarted);
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(progressTick);
        if (errorDialog != null) {
            errorDialog.dismiss();
            errorDialog = null;
        }
        ListenableFuture<MediaController> future = controllerFuture;
        controllerFuture = null;
        controller = null;
        controllerInitialized = false;
        updateControls();
        if (future != null) {
            MediaController.releaseFuture(future);
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
