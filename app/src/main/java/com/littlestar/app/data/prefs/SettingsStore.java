package com.littlestar.app.data.prefs;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * SharedPreferences 封装：学习任务与应用配置。每个键一个 getter / setter，写入用 apply()。
 */
public final class SettingsStore {

    private static final String TAG = "SettingsStore";
    /** 家长密码哈希记录前缀："v1:<迭代次数>:<盐 hex>:<哈希 hex>"。 */
    private static final String PIN_VERSION_PREFIX = "v1:";
    private static final int PIN_ITERATIONS = 120_000;
    private static final int PIN_SALT_BYTES = 16;
    private static final int PIN_KEY_BITS = 256;

    /** 键名集中在这里。 */
    public static final class Keys {
        public static final String DAILY_GOAL = "daily_goal";
        public static final String REMINDER_TIME = "reminder_time";     // HH:mm
        public static final String REMINDER_DAYS = "reminder_days";     // ISO 星期，逗号分隔
        public static final String ONBOARDED = "onboarded";
        public static final String NOTIF_RATIONALE_SHOWN = "notif_rationale_shown";
        public static final String SOUND_ENABLED = "sound_enabled";
        public static final String ENGLISH_HINT = "english_hint";
        public static final String PARENT_PIN = "parent_pin";

        private Keys() {
        }
    }

    private final SharedPreferences prefs;

    public SettingsStore(@NonNull SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public int getDailyGoal() {
        return prefs.getInt(Keys.DAILY_GOAL, 3);
    }

    public void setDailyGoal(int goal) {
        prefs.edit().putInt(Keys.DAILY_GOAL, goal).apply();
    }

    @NonNull
    public String getReminderTime() {
        String v = prefs.getString(Keys.REMINDER_TIME, "19:30");
        return v == null ? "19:30" : v;
    }

    public void setReminderTime(@NonNull String hhmm) {
        prefs.edit().putString(Keys.REMINDER_TIME, hhmm).apply();
    }

    /** ISO 星期集合（1=周一 … 7=周日）。默认周一到周五。 */
    @NonNull
    public Set<Integer> getReminderDays() {
        String raw = prefs.getString(Keys.REMINDER_DAYS, "1,2,3,4,5");
        Set<Integer> days = new HashSet<>();
        if (raw != null) {
            for (String part : raw.split(",")) {
                try {
                    days.add(Integer.parseInt(part.trim()));
                } catch (NumberFormatException ignored) {
                    // 忽略坏数据
                }
            }
        }
        return Collections.unmodifiableSet(days);
    }

    public void setReminderDays(@NonNull Set<Integer> days) {
        StringBuilder sb = new StringBuilder();
        for (int d = 1; d <= 7; d++) {
            if (days.contains(d)) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(d);
            }
        }
        prefs.edit().putString(Keys.REMINDER_DAYS, sb.toString()).apply();
    }

    public boolean isOnboarded() {
        return prefs.getBoolean(Keys.ONBOARDED, false);
    }

    public void setOnboarded(boolean onboarded) {
        prefs.edit().putBoolean(Keys.ONBOARDED, onboarded).apply();
    }

    public boolean isNotifRationaleShown() {
        return prefs.getBoolean(Keys.NOTIF_RATIONALE_SHOWN, false);
    }

    public void setNotifRationaleShown(boolean shown) {
        prefs.edit().putBoolean(Keys.NOTIF_RATIONALE_SHOWN, shown).apply();
    }

    public boolean isSoundEnabled() {
        return prefs.getBoolean(Keys.SOUND_ENABLED, true);
    }

    public void setSoundEnabled(boolean enabled) {
        prefs.edit().putBoolean(Keys.SOUND_ENABLED, enabled).apply();
    }

    /** debug 开关：测验题目卡显示英文提示（默认关）。 */
    public boolean isEnglishHint() {
        return prefs.getBoolean(Keys.ENGLISH_HINT, false);
    }

    public void setEnglishHint(boolean show) {
        prefs.edit().putBoolean(Keys.ENGLISH_HINT, show).apply();
    }

    /**
     * 家长密码（D31：PIN 登录取代算术验证，引导时设置）。
     * 只保存加盐哈希（PBKDF2-HMAC-SHA256）；旧版本的 4 位明文会在验证成功后自动升级。
     */
    public boolean isPinSet() {
        return !storedPin().isEmpty();
    }

    /** 校验输入是否为当前家长密码。 */
    public boolean verifyParentPin(@NonNull String pin) {
        String stored = storedPin();
        if (stored.isEmpty()) {
            return false;
        }
        if (!stored.startsWith(PIN_VERSION_PREFIX)) {
            byte[] expected = stored.getBytes(StandardCharsets.UTF_8);
            byte[] actual = pin.getBytes(StandardCharsets.UTF_8);
            boolean ok = MessageDigest.isEqual(expected, actual);
            if (ok) {
                setParentPin(pin);
            }
            return ok;
        }
        String[] parts = stored.split(":", 4);
        if (parts.length != 4) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = decodeHex(parts[2]);
            byte[] expected = decodeHex(parts[3]);
            if (iterations <= 0 || salt.length == 0 || expected.length == 0) {
                return false;
            }
            byte[] actual = pbkdf2(pin, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            Log.w(TAG, "parent pin record is corrupt", e);
            return false;
        }
    }

    /** 设置新密码；空字符串表示清除。 */
    public void setParentPin(@NonNull String pin) {
        if (pin.isEmpty()) {
            prefs.edit().remove(Keys.PARENT_PIN).apply();
            return;
        }
        byte[] salt = new byte[PIN_SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] hash = pbkdf2(pin, salt, PIN_ITERATIONS);
        String stored = PIN_VERSION_PREFIX + PIN_ITERATIONS + ":" + encodeHex(salt) + ":"
                + encodeHex(hash);
        prefs.edit().putString(Keys.PARENT_PIN, stored).apply();
    }

    @NonNull
    private String storedPin() {
        String pin = prefs.getString(Keys.PARENT_PIN, "");
        return pin == null ? "" : pin;
    }

    private static byte[] pbkdf2(@NonNull String pin, @NonNull byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, iterations, PIN_KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 unavailable", e);
        } finally {
            spec.clearPassword();
        }
    }

    @NonNull
    private static String encodeHex(@NonNull byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    @NonNull
    private static byte[] decodeHex(@NonNull String hex) {
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("odd hex length");
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("bad hex");
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    /**
     * 观察某个键的变化。监听器由返回的 LiveData 强引用持有（active 期间注册），不会被 GC。
     */
    @NonNull
    public LiveData<Integer> observeInt(@NonNull String key, int defaultValue) {
        return new PrefsLiveData<>(key, () -> prefs.getInt(key, defaultValue));
    }

    private final class PrefsLiveData<T> extends MutableLiveData<T> {
        private final String key;
        private final java.util.function.Supplier<T> reader;
        private final SharedPreferences.OnSharedPreferenceChangeListener listener;

        PrefsLiveData(String key, java.util.function.Supplier<T> reader) {
            this.key = key;
            this.reader = reader;
            this.listener = (p, changedKey) -> {
                if (key.equals(changedKey)) {
                    setValue(reader.get());
                }
            };
        }

        @Override
        protected void onActive() {
            setValue(reader.get());
            prefs.registerOnSharedPreferenceChangeListener(listener);
        }

        @Override
        protected void onInactive() {
            prefs.unregisterOnSharedPreferenceChangeListener(listener);
        }
    }
}
