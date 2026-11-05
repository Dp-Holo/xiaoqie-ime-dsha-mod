package org.fcitx.fcitx5.android.mod;

import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.view.inputmethod.InputConnection;

import java.lang.reflect.Field;

/**
 * 简易计算器候选：输入 1+2 时，在候选栏末尾显示「=3」，点一下把结果上屏。
 * 由 InputView 的 InputPanelEvent、候选栏适配器注入调用。
 */
public final class ModCalc {

    private static volatile String preedit = "";
    private static InputMethodService service;
    private static boolean withEq = true;
    private static boolean prefLoaded = false;

    private static void loadPref() {
        if (prefLoaded || service == null) return;
        prefLoaded = true;
        try {
            withEq = service.getSharedPreferences("mod_calc", 0).getBoolean("with_eq", true);
        } catch (Throwable ignored) {
            withEq = true;
        }
    }

    private static void savePref() {
        try {
            if (service != null) {
                service.getSharedPreferences("mod_calc", 0).edit().putBoolean("with_eq", withEq).apply();
            }
        } catch (Throwable ignored) {
        }
    }
    private static View.OnClickListener listener;

    private ModCalc() {
    }

    public static void setService(InputMethodService s) {
        service = s;
        try {
            ModWatchdog.start(s);
        } catch (Throwable ignored) {
        }
    }

    /** 看门狗用：清掉本模块的视图/定时器/引用，不影响输入法本身 */
    public static void softReset() {
        ticking = false;
        try {
            if (chip != null && chip.getVisibility() == View.VISIBLE) {
                chip.setVisibility(View.GONE);
            }
        } catch (Throwable ignored) {
        }
        try {
            showCandidatePage(false);
        } catch (Throwable ignored) {
        }
        lastRefreshAt = 0;
        lastLayoutAt = 0;
    }

    /** data = FcitxEvent.InputPanelEvent.Data */
    public static void onInputPanel(Object data) {
        String text = "";
        try {
            Field pf = data.getClass().getField("preedit");
            Object ft = pf.get(data);
            if (ft != null) {
                Field sf = ft.getClass().getField("strings");
                Object arr = sf.get(ft);
                if (arr instanceof String[]) {
                    StringBuilder sb = new StringBuilder();
                    for (String s : (String[]) arr) {
                        if (s != null) sb.append(s);
                    }
                    text = sb.toString();
                }
            }
        } catch (Throwable ignored) {
        }
        preedit = text;
    }

    public static boolean hasCandidate() {
        return candidate() != null;
    }

    /** 候选显示文本；不合法返回 null */
    public static String candidate() {
        Double v = eval(preedit);
        if (v == null) return null;
        String num = fmt(v);
        loadPref();
        return withEq ? "=" + num : num;
    }

    private static volatile int extraIndex = -1;
    private static volatile int realCandCount = 0;

    // ---- 轻量统计（每 5 秒最多一行，用于定位“越用越卡”） ----
    static long nCommit = 0, nRefresh = 0, nRefreshReal = 0, nDecorate = 0;
    static long nPageSwitch = 0, nChipAdd = 0, nTick = 0;
    private static long statAt = 0;
    private static volatile long realCandAt = 0;

    /** 构造追加的候选对象（CandidateWord），失败返回 null */
    private static java.lang.reflect.Constructor<?> cwCtor;
    private static Class<?> cwClass;

    private static boolean initCw() {
        if (cwCtor != null) return true;
        try {
            cwClass = Class.forName("org.fcitx.fcitx5.android.core.CandidateWord");
            cwCtor = cwClass.getConstructor(String.class, String.class, String.class, boolean.class);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static Object extraCandidate() {
        String cand = candidate();
        if (cand == null) return null;
        try {
            if (!initCw()) return null;
            return cwCtor.newInstance("", cand, "", false);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 追加项在候选数组中的下标；无追加项为 -1 */
    public static int extraIndex() {
        return extraIndex;
    }

    /** 由 HorizontalCandidateComponent 调用：在候选数组末尾追加计算器候选 */
    public static Object[] decorate(Object[] in) {
        extraIndex = -1;
        nDecorate++;
        realCandCount = in == null ? 0 : in.length;
        realCandAt = android.os.SystemClock.uptimeMillis();
        Object extra = extraCandidate();
        if (extra == null || in == null) return in;
        try {
            if (!initCw()) return in;
            Object arr = java.lang.reflect.Array.newInstance(cwClass, in.length + 1);
            for (int i = 0; i < in.length; i++) {
                java.lang.reflect.Array.set(arr, i, in[i]);
            }
            java.lang.reflect.Array.set(arr, in.length, extra);
            extraIndex = in.length;
            return (Object[]) arr;
        } catch (Throwable t) {
            extraIndex = -1;
            return in;
        }
    }

    public static View.OnClickListener clickListener() {
        if (listener == null) {
            listener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    commit();
                }
            };
        }
        return listener;
    }

    public static void commit() {
        String cand = candidate();
        if (cand == null) return;
        final String out = cand;
        final InputMethodService svc = service;
        try {
            if (svc != null) ModExtras.clearPreedit(svc);
        } catch (Throwable ignored) {
        }
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    InputConnection ic = svc == null ? null : svc.getCurrentInputConnection();
                    if (ic != null) ic.commitText(out, 1);
                } catch (Throwable ignored) {
                }
            }
        }, 150);
        preedit = "";
    }

    // ---------- 悬浮“=结果”按钮（候选栏无候选时也能显示） ----------

    private static View chip;
    private static int keyboardTop = -1;

    private static int dp(int v) {
        if (service == null) return v;
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                service.getResources().getDisplayMetrics());
    }

    private static ViewGroup barRoot;
    private static ViewGroup animator;
    private static int candidatePage = -1;
    private static boolean forced;

    private static void ensureChip() {
        // 视图可能随输入法窗口重建而失效：不在窗口上就重新挂载
        if (chip != null) {
            boolean ok = false;
            try {
                ok = chip.isAttachedToWindow() && chip.getParent() != null;
            } catch (Throwable ignored) {
            }
            if (ok) return;
            try {
                if (chip.getParent() instanceof ViewGroup) {
                    ((ViewGroup) chip.getParent()).removeView(chip);
                }
            } catch (Throwable ignored) {
            }
            chip = null;
            animator = null;
            candidatePage = -1;
            forced = false;
        }
        if (service == null) return;
        try {
            View decor = service.getWindow().getWindow().getDecorView();
            int id = service.getResources().getIdentifier("expand_candidate_btn", "id",
                    service.getPackageName());
            log("ensureChip id=" + id);
            if (id == 0) return;
            View expand = decor.findViewById(id);
            log("expand=" + (expand == null ? "null" : expand.getClass().getName()));
            if (expand == null) return;
            ViewParent parent = expand.getParent();
            log("parent=" + (parent == null ? "null" : parent.getClass().getName()));
            if (!(parent instanceof ViewGroup)) return;
            ViewGroup bar = (ViewGroup) parent;
            // 记录外层 ViewAnimator（空闲页 / 候选页），后面可主动切到候选页
            try {
                ViewParent vp = bar.getParent();
                if (vp instanceof ViewGroup
                        && vp.getClass().getName().endsWith("ViewAnimator")) {
                    ViewGroup va = (ViewGroup) vp;
                    animator = va;
                    int idx = 0;
                    for (int i = 0; i < va.getChildCount(); i++) {
                        if (va.getChildAt(i) == bar) {
                            idx = i;
                            break;
                        }
                    }
                    candidatePage = idx;
                    log("animator children=" + va.getChildCount() + " candidatePage=" + idx);
                }
            } catch (Throwable ignored) {
            }

            TextView tv = new TextView(service);
            tv.setPadding(dp(10), 0, dp(10), 0);
            tv.setTextSize(20);
            tv.setTextColor(themeColor("getCandidateTextColor", 0xFF37474F));
            tv.setBackgroundColor(0x00000000);
            tv.setGravity(Gravity.CENTER);
            tv.setVisibility(View.GONE);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    commitChip();
                }
            });
            tv.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    loadPref();
                    withEq = !withEq;
                    savePref();
                    try {
                        android.widget.Toast.makeText(service,
                                withEq ? "计算结果上屏：带 =（如 =3）" : "计算结果上屏：纯数字（如 3）",
                                android.widget.Toast.LENGTH_SHORT).show();
                    } catch (Throwable ignored) {
                    }
                    refreshChip();
                    return true;
                }
            });
            Class<?> lpCls = Class.forName("androidx.constraintlayout.widget.ConstraintLayout$LayoutParams");
            Object lp = lpCls.getConstructor(int.class, int.class)
                    .newInstance(ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            setInt(lp, "startToStart", 0);
            setInt(lp, "topToTop", 0);
            setInt(lp, "bottomToBottom", 0);
            bar.addView(tv, (ViewGroup.LayoutParams) lp);
            nChipAdd++;
            chip = tv;
            barRoot = bar;
            log("chip added to bar, children=" + bar.getChildCount());
        } catch (Throwable ignored) {
        }
    }

    private static void stat(String what) {
        try {
            long now = android.os.SystemClock.uptimeMillis();
            if (now - statAt < 5000) return;
            statAt = now;
            if (service == null) return;
            java.io.File dir = service.getExternalFilesDir(null);
            if (dir == null) return;
            java.io.FileWriter w = new java.io.FileWriter(new java.io.File(dir, "calc-stat.log"), true);
            w.write("commit=" + nCommit + " refresh=" + nRefresh + " real=" + nRefreshReal
                    + " decorate=" + nDecorate + " page=" + nPageSwitch + " add=" + nChipAdd
                    + " tick=" + nTick + " heap=" + (Runtime.getRuntime().totalMemory() / 1048576)
                    + "M/" + (Runtime.getRuntime().maxMemory() / 1048576) + "M " + what + "\n");
            w.close();
        } catch (Throwable ignored) {
        }
    }

    private static void log(String msg) {
        // 调试期已结束：不再落盘，避免每次按键写文件拖慢输入
    }

    private static void setInt(Object target, String name, int value) {
        try {
            java.lang.reflect.Field f = target.getClass().getField(name);
            f.setInt(target, value);
        } catch (Throwable ignored) {
        }
    }

    private static int themeColor(String getter, int def) {
        try {
            Class<?> tm = Class.forName("org.fcitx.fcitx5.android.data.theme.ThemeManager");
            Object inst = tm.getField("INSTANCE").get(null);
            Object theme = tm.getMethod("getActiveTheme").invoke(inst);
            Object v = theme.getClass().getMethod(getter).invoke(theme);
            if (v instanceof Integer) return (Integer) v;
        } catch (Throwable ignored) {
        }
        return def;
    }

    private static void continue_ok() {
    }

    /** 由 onComputeInsets 调用，键盘顶端位置 */
    public static void onKeyboardTop(int top) {
        keyboardTop = top;
        layoutChip();
    }

    private static long lastLayoutAt = 0;

    private static void layoutChip() {
        try {
            if (chip == null || barRoot == null) return;
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastLayoutAt < 200) return;   // 限频，避免自旋
            lastLayoutAt = now;
            int parentW = barRoot.getWidth();
            int w = chip.getWidth();
            if (parentW <= 0 || w <= 0) return;     // 取不到尺寸就等下次刷新，不再 post 自旋
            ViewGroup.LayoutParams lp = chip.getLayoutParams();
            try {
                java.lang.reflect.Field f = lp.getClass().getField("leftMargin");
                int target = Math.max(0, (parentW - w) / 2);
                if (f.getInt(lp) != target) {
                    f.setInt(lp, target);
                    chip.setLayoutParams(lp);
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    /** 由 FcitxInputMethodService.commitText 调用（每次上屏文本后刷新） */
    public static void onTextCommitted(Object ignored) {
        nCommit++;
        stat("commit");
        try {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    refreshChip();
                }
            });
        } catch (Throwable t) {
        }
    }

    private static boolean ticking = false;

    private static final Runnable tick = new Runnable() {
        @Override
        public void run() {
            nTick++;
            refreshChip();
            if (ticking) {
                new Handler(Looper.getMainLooper()).postDelayed(this, 800);
            }
        }
    };

    private static void startTicking() {
        if (ticking) return;
        ticking = true;
        new Handler(Looper.getMainLooper()).postDelayed(tick, 800);
    }

    private static void stopTicking() {
        ticking = false;
    }

    private static void showCandidatePage(boolean show) {
        try {
            if (animator == null || candidatePage < 0) return;
            if (show) {
                int cur = (Integer) animator.getClass().getMethod("getDisplayedChild")
                        .invoke(animator);
                if (cur != candidatePage) {
                    nPageSwitch++;
                    animator.getClass().getMethod("setDisplayedChild", int.class)
                            .invoke(animator, candidatePage);
                }
                forced = true;
            } else if (forced) {
                int idle = candidatePage == 0 ? 1 : 0;
                animator.getClass().getMethod("setDisplayedChild", int.class)
                        .invoke(animator, idle);
                forced = false;
            }
        } catch (Throwable ignored) {
        }
    }

    private static long lastRefreshAt = 0;

    private static void refreshChip() {
        nRefresh++;
        stat("refresh");
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastRefreshAt < 120) return;   // 节流：快速连打时不要每次都读光标
        lastRefreshAt = now;
        try {
            ensureChip();
            if (chip == null) return;
            long since = android.os.SystemClock.uptimeMillis() - realCandAt;
            boolean hasRealCand = realCandCount > 0 && since < 1200;
            log("refresh state preedit=[" + preedit + "] realCand=" + realCandCount + "/" + since);
            if ((preedit != null && preedit.length() > 0) || hasRealCand) {
                // 引擎正在出候选：只收走我们的结果，不要动页面（否则会把真实候选一起切没）
                chip.setVisibility(View.GONE);
                stopTicking();
                return;
            }
            String before = textBefore();
            String expr = extractExpression(before);
            Double v = eval(expr);
            log("refresh before=[" + (before.length() > 24 ? before.substring(before.length() - 24) : before)
                    + "] expr=" + expr + " v=" + v);
            if (v == null) {
                if (chip.getVisibility() != View.GONE) chip.setVisibility(View.GONE);
                showCandidatePage(false);
                stopTicking();
                return;
            }
            String num = fmt(v);
            loadPref();
            String label = withEq ? "=" + num : num;
            if (!label.contentEquals(((TextView) chip).getText())) {
                ((TextView) chip).setText(label);
            }
            layoutChip();
            if (chip.getVisibility() != View.VISIBLE) chip.setVisibility(View.VISIBLE);
            showCandidatePage(true);
            startTicking();
        } catch (Throwable ignored) {
        }
    }

    private static void commitChip() {
        try {
            if (chip != null) chip.setVisibility(View.GONE);
            String expr = extractExpression(textBefore());
            Double v = eval(expr);
            if (v == null || service == null) return;
            InputConnection ic = service.getCurrentInputConnection();
            if (ic == null) return;
            String num = fmt(v);
            loadPref();
            ic.deleteSurroundingText(expr.length(), 0);
            ic.commitText(withEq ? "=" + num : num, 1);
        } catch (Throwable ignored) {
        }
    }

    private static String textBefore() {
        try {
            if (service == null) return "";
            InputConnection ic = service.getCurrentInputConnection();
            if (ic == null) return "";
            CharSequence cs = ic.getTextBeforeCursor(64, 0);
            return cs == null ? "" : cs.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /** 取结尾的算式片段（含数字与运算符，去尾部空格） */
    private static String extractExpression(String text) {
        if (text == null) return null;
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == ' ') end--;
        int start = end;
        while (start > 0) {
            char ch = text.charAt(start - 1);
            if ((ch >= '0' && ch <= '9') || ch == '.' || ch == '+' || ch == '-' || ch == '*'
                    || ch == '/' || ch == '(' || ch == ')' || ch == ' ') {
                start--;
            } else {
                break;
            }
        }
        String s = text.substring(start, end).trim();
        if (s.length() == 0) return null;
        if (!s.matches(".*[0-9].*")) return null;
        if (!s.matches(".*[+\\-*/%^].*")) return null;
        return s;
    }

    // ---------- 表达式求值 ----------

    private static Double eval(String s) {
        if (s == null) return null;
        String t = s.replace('×', '*').replace('÷', '/')
                .replace('＋', '+').replace('－', '-')
                .replace('（', '(').replace('）', ')')
                .replace('％', '%').replace('＾', '^')
                .replace('．', '.').trim();
        if (t.length() == 0 || t.length() > 64) return null;
        if (!t.matches("[0-9+\\-*/%^(). ]+")) return null;
        if (!t.matches(".*[0-9].*")) return null;
        try {
            Parser p = new Parser(t);
            double v = p.expr();
            p.skip();
            if (p.pos != t.length()) return null;
            if (Double.isNaN(v) || Double.isInfinite(v)) return null;
            if (Math.abs(v) > 1e15) return null;
            return v;
        } catch (Throwable e) {
            return null;
        }
    }

    private static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.valueOf((long) Math.rint(v));
        String s = String.format(java.util.Locale.US, "%.6f", v);
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '0') end--;
        if (end > 0 && s.charAt(end - 1) == '.') end--;
        return s.substring(0, end);
    }

    private static final class Parser {
        final String s;
        int pos;

        Parser(String s) {
            this.s = s;
        }

        void skip() {
            while (pos < s.length() && s.charAt(pos) == ' ') pos++;
        }

        boolean eat(char c) {
            skip();
            if (pos < s.length() && s.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        double expr() {
            double v = term();
            while (true) {
                skip();
                if (eat('+')) v += term();
                else if (eat('-')) v -= term();
                else return v;
            }
        }

        double term() {
            double v = factor();
            while (true) {
                skip();
                if (eat('*')) v *= factor();
                else if (eat('/')) v /= factor();
                else if (eat('%')) v %= factor();
                else return v;
            }
        }

        double factor() {
            double base = unary();
            skip();
            if (eat('^')) return Math.pow(base, factor());
            return base;
        }

        double unary() {
            skip();
            if (eat('-')) return -unary();
            if (eat('+')) return unary();
            if (eat('(')) {
                double v = expr();
                if (!eat(')')) throw new IllegalArgumentException("paren");
                return v;
            }
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
            if (start == pos) throw new IllegalArgumentException("num");
            return Double.parseDouble(s.substring(start, pos));
        }
    }
}
