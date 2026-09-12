package io.github.aw1y2z.sesame.hook;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import io.github.aw1y2z.sesame.model.normal.base.BaseModel;
import io.github.aw1y2z.sesame.util.Log;

public class Toast {
    private static final String TAG = Toast.class.getSimpleName();

    private static final int DURATION_SHORT = 2000;

    /** 当前正在显示的自定义气泡 View，显示新气泡前需移除旧的 */
    private static View currentToastView;
    private static WindowManager currentWindowManager;
    private static final Handler hideHandler = new Handler();
    private static Runnable hideRunnable;

    public static void show(CharSequence cs) {
        show(cs, false);
    }

    public static void show(CharSequence cs, boolean force) {
        Context context = ApplicationHook.getContext();
        if (context != null && (force || BaseModel.getShowToast().getValue())) {
            show(context, ApplicationHook.getMainHandler(), cs);
        }
    }

    public static void show(Context context, Handler handler, CharSequence cs) {
        try {
            handler.post(() -> {
                try {
                    Activity activity = ApplicationHook.getCurrentActivity();
                    if (activity != null) {
                        // 前台：使用 WindowManager 自定义气泡，位置完全可控
                        showCustomToast(activity, cs);
                    } else {
                        // 后台：降级系统 Toast，位置不可控但能弹出提示
                        android.widget.Toast toast = android.widget.Toast.makeText(context, cs, android.widget.Toast.LENGTH_SHORT);
                        toast.show();
                    }
                } catch (Throwable t) {
                    Log.i(TAG, "show.run err:");
                    Log.printStackTrace(TAG, t);
                }
            });
        } catch (Throwable t) {
            Log.i(TAG, "show err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 使用 WindowManager 在当前 Activity 窗口上显示自定义气泡，支持纵向偏移。
     * 从 Android 11(API 30) 起系统文本 Toast 的 setGravity/yOffset 被系统忽略，
     * 因此在前台时改用应用内窗口叠加的方式实现位置可控的气泡提示。
     */
    private static void showCustomToast(Activity activity, CharSequence text) {
        // 移除上一个气泡
        removeCurrentToast();

        try {
            WindowManager windowManager = activity.getWindowManager();

            TextView textView = new TextView(activity);
            textView.setText(text);
            textView.setTextColor(Color.WHITE);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);

            // 半透明黑色圆角背景，模拟系统 Toast 样式
            GradientDrawable background = new GradientDrawable();
            background.setColor(0xCC000000);
            background.setCornerRadius(dp2px(activity, 8));
            int paddingH = dp2px(activity, 16);
            int paddingV = dp2px(activity, 10);
            textView.setPadding(paddingH, paddingV, paddingH, paddingV);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                textView.setBackground(background);
            } else {
                textView.setBackgroundDrawable(background);
            }

            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION;
            params.format = PixelFormat.TRANSLUCENT;
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
            params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            params.y = BaseModel.getToastOffsetY().getValue();
            params.width = WindowManager.LayoutParams.WRAP_CONTENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;

            windowManager.addView(textView, params);
            currentToastView = textView;
            currentWindowManager = windowManager;

            // 定时移除
            hideRunnable = Toast::removeCurrentToast;
            hideHandler.postDelayed(hideRunnable, DURATION_SHORT);
        } catch (Throwable t) {
            Log.i(TAG, "showCustomToast err:");
            Log.printStackTrace(TAG, t);
            // 自定义气泡失败时降级系统 Toast
            try {
                android.widget.Toast.makeText(activity, text, android.widget.Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * 移除当前正在显示的自定义气泡
     */
    private static void removeCurrentToast() {
        if (hideRunnable != null) {
            hideHandler.removeCallbacks(hideRunnable);
            hideRunnable = null;
        }
        if (currentToastView != null && currentWindowManager != null) {
            try {
                currentWindowManager.removeView(currentToastView);
            } catch (Throwable ignored) {
            }
        }
        currentToastView = null;
        currentWindowManager = null;
    }

    private static int dp2px(Context context, float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }
}
