package com.littlestar.app.data.store;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Atomic JSON state storage backed by NIO {@link Files#move}: writes to a staging {@code .new}
 * file, flushes the descriptor, closes the stream, and atomically replaces the target file
 * across both Android (POSIX) and Windows host JVM unit tests.
 * Reference consulted: Java SE NIO Files.move & StandardCopyOption
 * (https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
 */
public final class AtomicStateFile implements StateFile {

    private static final String TAG = "AtomicStateFile";

    private final File baseFile;
    private final File newFile;
    private final File legacyBackupFile;
    private final File backupFile;

    public AtomicStateFile(@NonNull File baseFile) {
        this.baseFile = baseFile;
        this.newFile = new File(baseFile.getPath() + ".new");
        this.legacyBackupFile = new File(baseFile.getPath() + ".bak");
        this.backupFile = new File(baseFile.getParentFile(), "progress.bak.json");
    }

    @Nullable
    @Override
    public String read() {
        try {
            if (legacyBackupFile.exists()) {
                moveReplacing(legacyBackupFile, baseFile);
            }
            if (newFile.exists()) {
                Files.deleteIfExists(newFile.toPath());
            }
            if (!baseFile.exists()) {
                return null;
            }
            return readUtf8(baseFile);
        } catch (IOException e) {
            Log.e(TAG, "read failed", e);
            return null;
        }
    }

    @Override
    public void write(@NonNull String content) {
        File parent = baseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            Log.e(TAG, "mkdirs failed for " + parent);
        }
        boolean written = false;
        try (FileOutputStream out = new FileOutputStream(newFile)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.flush();
            try {
                out.getFD().sync();
            } catch (IOException ignored) {
                // Best-effort fsync before closing handle.
            }
            written = true;
        } catch (IOException e) {
            Log.e(TAG, "write staging failed", e);
        }
        if (!written) {
            try {
                Files.deleteIfExists(newFile.toPath());
            } catch (IOException ignored) {
            }
            return;
        }
        try {
            moveReplacing(newFile, baseFile);
        } catch (IOException e) {
            Log.e(TAG, "commit move failed", e);
            try {
                Files.deleteIfExists(newFile.toPath());
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void rename(@NonNull String suffix) {
        if (!baseFile.exists()) {
            return;
        }
        File target = new File(baseFile.getParentFile(), baseFile.getName() + suffix);
        try {
            moveReplacing(baseFile, target);
            Files.deleteIfExists(newFile.toPath());
            Files.deleteIfExists(legacyBackupFile.toPath());
        } catch (IOException e) {
            Log.e(TAG, "rename to " + target + " failed", e);
        }
    }

    @Nullable
    @Override
    public String readBackup() {
        if (!backupFile.exists()) {
            return null;
        }
        try {
            return readUtf8(backupFile);
        } catch (IOException e) {
            Log.e(TAG, "readBackup failed", e);
            return null;
        }
    }

    @Override
    public void writeBackup(@NonNull String content) {
        try (FileOutputStream out = new FileOutputStream(backupFile)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.e(TAG, "writeBackup failed", e);
        }
    }

    private static void moveReplacing(@NonNull File source, @NonNull File target) throws IOException {
        try {
            Files.move(source.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @NonNull
    private static String readUtf8(@NonNull File file) throws IOException {
        try (InputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
