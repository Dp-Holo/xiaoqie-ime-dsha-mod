package org.fcitx.fcitx5.android.mod;

import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * 低功耗看门狗：空闲且 CPU 异常时做软重置；持续异常且键盘隐藏时才重启进程。
 * 采样只调用 Process.getElapsedCpuTime()，不读 /proc、不持锁、不唤醒。
 */
public final class ModWatchdog {

    private static final long IDLE_INTERVAL = 60_000L;   // 空闲采样间隔
    private static final long LONG_IDLE_INTERVAL = 300_000L;
    private static final double CPU_THRESHOLD = 30.0;    // %
    private static final int SOFT_AT = 2;
    private static final int HARD_AT = 4;

    private static InputMethodService service;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static long lastCpu = -1, lastWall = -1;
    private static int bad = 0;
    private static boolean running = false;

    private ModWatchdog() {
    }

    public static void start(InputMethodService s) {
        service = s;
        if (running) return;
        running = true;
        lastCpu = -1;
        lastWall = -1;
        bad = 0;
        handler.postDelayed(tick, IDLE_INTERVAL);
    }

    private static final Runnable tick = new Runnable() {
        @Override
        public void run() {
            try {
                sample();
            } catch (Throwable ignored) {
            }
            long interval = (bad > 0) ? IDLE_INTERVAL : LONG_IDLE_INTERVAL;
            // 异常时更频繁观察；正常时放慢到 5 分钟，省电
            handler.postDelayed(this, interval);
        }
    };

    private static void sample() {
        if (service == null) return;
        long cpu = android.os.Process.getElapsedCpuTime();
        long wall = SystemClock.uptimeMillis();
        if (lastCpu < 0) {
            lastCpu = cpu;
            lastWall = wall;
            return;
        }
        long dc = cpu - lastCpu;
        long dw = wall - lastWall;
        lastCpu = cpu;
        lastWall = wall;
        if (dw <= 0) return;
        double pct = dc * 100.0 / dw;

        boolean visible;
        try {
            visible = service.isInputViewShown();
        } catch (Throwable t) {
            visible = false;
        }
        if (visible || pct < CPU_THRESHOLD) {
            bad = 0;
            return;
        }
        bad++;
        if (bad == SOFT_AT) {
            log("idle cpu=" + (int) pct + "% -> soft reset");
            ModCalc.softReset();
        } else if (bad >= HARD_AT) {
            log("idle cpu=" + (int) pct + "% -> restart process");
            bad = 0;
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    private static void log(String msg) {
        try {
            if (service == null) return;
            java.io.File dir = service.getExternalFilesDir(null);
            if (dir == null) return;
            java.io.FileWriter w = new java.io.FileWriter(
                    new java.io.File(dir, "mod-watchdog.log"), true);
            w.write(new java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.US)
                    .format(new java.util.Date()) + " " + msg + "\n");
            w.close();
        } catch (Throwable ignored) {
        }
    }
}
