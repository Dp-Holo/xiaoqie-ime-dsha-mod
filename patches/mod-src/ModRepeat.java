package org.fcitx.fcitx5.android.mod;

/** 按键连按加速：随按住时间递减间隔（长按退格越删越快）。 */
public final class ModRepeat {

    private static final long[] DELAYS = {50, 45, 40, 36, 32, 28, 24, 20, 17, 15};
    private static long lastAt = 0;
    private static int step = 0;

    private ModRepeat() {
    }

    public static long nextDelay() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastAt > 600) {
            step = 0;
        }
        long d = DELAYS[Math.min(step, DELAYS.length - 1)];
        step++;
        lastAt = now;
        return d;
    }
}
