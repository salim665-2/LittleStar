package com.littlestar.app.core.audio;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import com.littlestar.app.BuildConfig;
import com.littlestar.app.data.content.model.Word;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 字音播放：单独的 ExoPlayer 实例播放 asset:///audio/words/xx.ogg。
 * 新请求打断旧的；暴露 isPlaying LiveData。
 * 字音缺失降级：debug 用 TTS 读汉字并 Log.w；release 静默跳过。
 * Reference consulted: Android Developers — Jetpack Media3 ExoPlayer
 * (https://developer.android.com/media/media3/exoplayer)
 */
public final class SpeechPlayer {

    public interface FailureListener {
        /** 播放失败（音频缺失 / 损坏）。Flashcard 用来弹「发音暂时没有」。 */
        void onPlayFailed(@NonNull String wordId);
    }

    public interface SequenceListener {
        void onWordChanged(@NonNull String wordId);
        void onFinished(boolean failed);
    }

    private static final String TAG = "SpeechPlayer";

    private final ExoPlayer player;
    private final MutableLiveData<Boolean> playing = new MutableLiveData<>(false);
    @Nullable
    private final TextToSpeech tts;   // 仅 debug 兜底
    @Nullable
    private FailureListener failureListener;
    @Nullable
    private SequenceListener sequenceListener;
    @Nullable
    private String pendingWordId;

    public SpeechPlayer(@NonNull Context context) {
        player = new ExoPlayer.Builder(context)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .build(), true)
                .build();
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                playing.postValue(isPlaying);
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED || state == Player.STATE_IDLE) {
                    playing.postValue(false);
                }
                if (state == Player.STATE_ENDED) {
                    finishSequence(false);
                }
            }

            @Override
            public void onMediaItemTransition(@Nullable MediaItem item, int reason) {
                if (sequenceListener != null && item != null) {
                    sequenceListener.onWordChanged(item.mediaId);
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                playing.postValue(false);
                if (sequenceListener != null) {
                    Log.w(TAG, "word sequence failed", error);
                    finishSequence(true);
                    return;
                }
                Log.w(TAG, "play failed: " + pendingWordId, error);
                if (BuildConfig.DEBUG && tts != null && pendingWordText != null) {
                    // debug 兜底：TTS 读汉字
                    tts.speak(pendingWordText, TextToSpeech.QUEUE_FLUSH, null, "ls-tts");
                }
                FailureListener l = failureListener;
                String id = pendingWordId;
                if (l != null && id != null) {
                    l.onPlayFailed(id);
                }
            }
        });
        tts = BuildConfig.DEBUG ? new TextToSpeech(context.getApplicationContext(), status -> {
        }) : null;
        if (tts != null) {
            tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
        }
    }

    @Nullable
    private String pendingWordText;

    public void setFailureListener(@Nullable FailureListener listener) {
        failureListener = listener;
    }

    /** Remove only the listener owned by the Activity being destroyed. */
    public void clearFailureListener(@NonNull FailureListener listener) {
        if (failureListener == listener) {
            failureListener = null;
        }
    }

    /** 播放一个字的读音。新请求打断旧的。 */
    public void play(@NonNull Word word) {
        finishSequence(false);
        pendingWordId = word.id;
        pendingWordText = word.hanzi;
        player.setMediaItem(mediaItem(word));
        player.prepare();
        player.play();
    }

    /** Play the supplied pronunciations once, in order, with updates on the main thread. */
    public void playSequence(@NonNull List<Word> words, @NonNull SequenceListener listener) {
        if (words.isEmpty()) {
            return;
        }
        finishSequence(false);
        pendingWordId = null;
        pendingWordText = null;
        if (tts != null) {
            tts.stop();
        }
        List<MediaItem> items = new ArrayList<>(words.size());
        for (Word word : words) {
            items.add(mediaItem(word));
        }
        player.setMediaItems(items);
        sequenceListener = listener;
        listener.onWordChanged(words.get(0).id);
        player.prepare();
        player.play();
    }

    /** Stop only the sequence started by this listener. */
    public void stopSequence(@NonNull SequenceListener listener) {
        if (sequenceListener == listener) {
            stop();
        }
    }

    private static MediaItem mediaItem(Word word) {
        return new MediaItem.Builder()
                .setUri("asset:///" + word.audio)
                .setMediaId(word.id)
                .build();
    }

    private void finishSequence(boolean failed) {
        SequenceListener listener = sequenceListener;
        sequenceListener = null;
        if (listener != null) {
            listener.onFinished(failed);
        }
    }

    @NonNull
    public LiveData<Boolean> isPlaying() {
        return playing;
    }

    public void stop() {
        player.stop();
        finishSequence(false);
        playing.postValue(false);
    }

    public void release() {
        player.release();
        if (tts != null) {
            tts.shutdown();
        }
    }
}
