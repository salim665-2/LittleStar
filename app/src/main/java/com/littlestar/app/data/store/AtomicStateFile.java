package com.littlestar.app.data.store;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.AtomicFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 原子写入：先写 {@code progress.json.new}，fsync 后再移动到 {@code progress.json}。
 *
 * <p>不使用 {@link AtomicFile#finishWrite}，因为它内部用 {@link File#renameTo} 覆盖目标文件；
 * 在 Windows（JVM 单元测试）上目标已存在时 renameTo 会失败，导致除第一次以外的保存全部静默丢失。
 * 这里改用 {@link Files#move} + {@link StandardCopyOption#REPLACE_EXISTING}，
 * 并保留 {@code ATOMIC_MOVE} 以保证 Android/Linux 上的原子替换。</p>
 *
 * <p>读取时先处理旧版 AtomicFile 留下的 {@code .bak}：那是上次提交成功的完整内容，
 * 应当覆盖可能写了一半的主文件。</p>
 */
public final class AtomicStateFile implements StateFile {

    private static final String TAG = "AtomicStateFile";

    private final File baseFile;
    private final File newFile;
    private final File legacyBackupFile;
    private final AtomicFile atomicFile;
    private final File backupFile;

    public AtomicStateFile(@NonNull File baseFile) {
        this.baseFile = baseFile;
        this.newFile = new File(baseFile.getPath() + ".new");
        this.legacyBackupFile = new File(baseFile.getPath() + ".bak");
        this.atomicFile = new AtomicFile(baseFile);
        this.backupFile = new File(baseFile.getParentFile(), "progress.bak.json");
    }

    @Nullable
    @Override
    public String read() {
        promoteLegacyBackup();
        try (InputStream in = atomicFile.openRead();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (FileNotFoundException e) {
            if (baseFile.exists()) {
                Log.e(TAG, "read failed", e);
            }
            return null;
        } catch (IOException e) {
            Log.e(TAG, "read failed", e);
            return null;
        }
    }

    @Override
    public void write(@NonNull String content) {
        File parent = newFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            Log.e(TAG, "cannot create " + parent);
            return;
        }
        try (FileOutputStream out = new FileOutputStream(newFile)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (IOException e) {
            Log.e(TAG, "write failed", e);
            deleteQuietly(newFile);
            return;
        }
        try {
            replace(newFile, baseFile);
        } catch (IOException e) {
            Log.e(TAG, "commit failed, previous content kept", e);
            deleteQuietly(newFile);
        }
    }

    @Override
    public void rename(@NonNull String suffix) {
        if (!baseFile.exists()) {
            return;
        }
        File target = new File(baseFile.getParentFile(), baseFile.getName() + suffix);
        try {
            replace(baseFile, target);
        } catch (IOException e) {
            Log.e(TAG, "rename to " + target + " failed", e);
            return;
        }
        // 此时 base 已移走，delete 只清临时文件
        atomicFile.delete();
    }

    @Nullable
    @Override
    public String readBackup() {
        if (!backupFile.exists()) {
            return null;
        }
        try (InputStream in = new FileInputStream(backupFile);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
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

    /**
     * 旧版 AtomicFile 先写主文件、再把主文件改名为 {@code .bak}；
     * 若进程在写主文件时被杀，主文件是半截内容，而 {@code .bak} 才是上次完整提交的内容。
     */
    private void promoteLegacyBackup() {
        if (!legacyBackupFile.exists()) {
            return;
        }
        try {
            replace(legacyBackupFile, baseFile);
        } catch (IOException e) {
            Log.e(TAG, "promote legacy backup failed", e);
        }
    }

    private static void replace(@NonNull File source, @NonNull File target) throws IOException {
        Path from = source.toPath();
        Path to = target.toPath();
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(@NonNull File file) {
        if (file.exists() && !file.delete()) {
            Log.e(TAG, "Failed to delete " + file);
        }
    }
}
