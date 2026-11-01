package org.fcitx.fcitx5.android.mod;

import android.content.Context;
import android.content.ContextWrapper;
import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;

/**
 * 魔改补充功能。
 *
 * 双击候选栏右侧的「展开更多候选」按钮 = 清空当前预输入（取消组字，不删已上屏文字）。
 * 由 KawaiiBarComponent 的 candidateUi 初始化处注入 setOnDoubleTapListener。
 */
public final class ModExtras {

    private ModExtras() {
    }

    /** 长按语言键：打开系统「选择输入法」窗口（可在小企鹅/讯飞/说点啥之间切换） */
    public static void showSystemImPicker(Context ctx) {
        try {
            InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        } catch (Throwable ignored) {
        }
    }

    /** 双击候选栏展开按钮：清空预输入（等价的 FcitxAPI.reset()） */
    public static Function1<View, Unit> clearPreeditOnDoubleTap() {
        return new Function1<View, Unit>() {
            @Override
            public Unit invoke(View v) {
                try {
                    final InputMethodService svc = findInputMethodService(v.getContext());
                    if (svc != null) {
                        // postFcitxJob(block: suspend FcitxAPI.() -> Unit)
                        svc.getClass()
                                .getMethod("postFcitxJob", Function2.class)
                                .invoke(svc, new ResetPreeditBlock());
                    }
                } catch (Throwable ignored) {
                }
                return Unit.INSTANCE;
            }
        };
    }

    private static InputMethodService findInputMethodService(Context c) {
        while (c instanceof ContextWrapper && !(c instanceof InputMethodService)) {
            Context base = ((ContextWrapper) c).getBaseContext();
            if (base == null || base == c) return null;
            c = base;
        }
        return c instanceof InputMethodService ? (InputMethodService) c : null;
    }

    /** suspend FcitxAPI.() -> Unit 的函数式实现：调用 FcitxAPI.reset() */
    private static final class ResetPreeditBlock implements Function2<Object, Object, Object> {
        @Override
        public Object invoke(Object api, Object continuation) {
            try {
                if (api == null) return Unit.INSTANCE;
                return api.getClass()
                        .getMethod("reset", Continuation.class)
                        .invoke(api, continuation);
            } catch (Throwable ignored) {
                return Unit.INSTANCE;
            }
        }
    }
}
