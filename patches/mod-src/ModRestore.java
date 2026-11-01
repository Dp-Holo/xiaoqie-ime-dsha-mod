package org.fcitx.fcitx5.android.mod;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 重装后首次启动时，从备份目录恢复词库、自定义短语和输入法配置。
 *
 * 判断依据：备份目录中最新文件的修改时间 晚于 本地 .mod_restored 标记时间 时执行恢复。
 * 这样既保证首次启动一定恢复，也保证上次"备份未拷全"时下次启动会自动重试。
 * 恢复完成后做一次完整性校验（词库文件数一致才落标记）。
 */
public final class ModRestore {

    private ModRestore() {
    }

    public static void restoreIfNeeded(Context ctx) {
        try {
            File ext = ctx.getExternalFilesDir(null);
            if (ext == null) return;
            File marker = new File(ext, ".mod_restored");
            File[] candidates = new File[]{
                    new File("/sdcard/Android/media/org.fcitx.fcitx5.android/backup"),
                    new File("/sdcard/Download/fcitx-backup")
            };
            File backup = null;
            long newest = 0;
            for (File c : candidates) {
                if (!c.isDirectory()) continue;
                long t = latestTime(c);
                if (t > newest) {
                    newest = t;
                    backup = c;
                }
            }
            if (backup == null || newest <= 0) return;
            if (marker.exists() && marker.lastModified() >= newest) return;

            File[] children = backup.listFiles();
            if (children == null || children.length == 0) return;
            for (File child : children) {
                copyRecursively(child, new File(ext, child.getName()));
            }
            // 完整性校验：备份里的词库文件数必须与恢复后一致
            int srcDicts = countFiles(new File(backup, "data/pinyin/dictionaries"));
            int dstDicts = countFiles(new File(ext, "data/pinyin/dictionaries"));
            if (srcDicts > 0 && dstDicts < srcDicts) {
                // 没拷全，不落标记，下次启动重试
                return;
            }
            marker.setLastModified(Math.max(newest, System.currentTimeMillis()));
        } catch (Throwable ignored) {
        }
    }

    private static long latestTime(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.lastModified();
        long max = f.lastModified();
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                long t = latestTime(k);
                if (t > max) max = t;
            }
        }
        return max;
    }

    private static int countFiles(File dir) {
        if (dir == null || !dir.isDirectory()) return 0;
        File[] kids = dir.listFiles();
        if (kids == null) return 0;
        int n = 0;
        for (File k : kids) if (k.isFile()) n++;
        return n;
    }

    private static void copyRecursively(File src, File dst) throws Exception {
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) return;
            File[] kids = src.listFiles();
            if (kids == null) return;
            for (File kid : kids) copyRecursively(kid, new File(dst, kid.getName()));
        } else {
            File parent = dst.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            InputStream in = new FileInputStream(src);
            OutputStream out = new FileOutputStream(dst);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                try { in.close(); } catch (Throwable ignored) {}
                try { out.close(); } catch (Throwable ignored) {}
            }
        }
    }
}
