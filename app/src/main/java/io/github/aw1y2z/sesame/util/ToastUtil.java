package io.github.aw1y2z.sesame.util;

import android.content.Context;

/**
 * Toast 工具类，统一委托给 {@link io.github.aw1y2z.sesame.hook.Toast}，
 * 由其决定使用自定义气泡（前台，位置可控）还是系统 Toast（后台降级）。
 */
public class ToastUtil {

    public static void show(Context context, int resId) {
        show(context, context.getText(resId));
    }

    public static void show(Context context, CharSequence text) {
        io.github.aw1y2z.sesame.hook.Toast.show(text);
    }
}
