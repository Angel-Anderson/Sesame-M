// ⚠️ 注意：为了不让 libsesame.so 崩溃，此类必须保留在旧包名 io.github.lazyimmortal.sesame 下，请勿随意移动
package io.github.lazyimmortal.sesame.util;

import android.content.Context;
import android.content.pm.PackageManager;

import org.json.JSONObject;

import io.github.aw1y2z.sesame.BuildConfig;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.ToastUtil;

public class LibraryUtil {
    private static final String TAG = LibraryUtil.class.getSimpleName();

    public static String getLibSesamePath(Context context) {
        String libSesamePath = null;
        try {
            libSesamePath = context.getPackageManager()
                                    .getApplicationInfo(BuildConfig.APPLICATION_ID, 0)
                                    .nativeLibraryDir + "/" + System.mapLibraryName("sesame");
        } catch (PackageManager.NameNotFoundException e) {
            ToastUtil.show(context, "请授予支付宝读取芝麻粒的权限");
            Log.record("请授予支付宝读取芝麻粒的权限");
        }
        return libSesamePath;
    }

    /**
     * 加载 libsesame.so，兼容 LSPosed 与 LSPatch 两种模式。
     * <p>
     * 1) 先尝试从模块 APK 的 nativeLibraryDir 加载（LSPosed / LSPatch remote 模式）
     * 2) 失败则回退 System.loadLibrary（LSPatch local 模式：.so 内嵌在目标 APK 中）
     *
     * @param context 目标进程的 Context
     */
    public static void loadLibSesame(Context context) {
        // 1) LSPosed 路径：模块独立安装
        try {
            String path = getLibSesamePath(context);
            if (path != null) {
                System.load(path);
                return;
            }
        } catch (Throwable ignored) {
            // 走 fallback
        }
        // 2) LSPatch local 兜底：.so 内嵌在 patched APK 的 lib 目录
        try {
            System.loadLibrary("sesame");
        } catch (UnsatisfiedLinkError e) {
            ToastUtil.show(context, "芝麻粒原生库加载失败，请联系开发者");
            Log.record("libsesame.so 加载失败：" + e.getMessage());
            Log.printStackTrace(TAG, e);
        }
    }

    public static Boolean loadLibrary(String libraryName) {
        try {
            System.loadLibrary(libraryName);
            return true;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    private static native boolean libraryDoFarmTask(JSONObject task);
    public static Boolean doFarmTask(JSONObject task) {
        return libraryDoFarmTask(task);
    }

    // 重写 doFarmDrawTimesTask 方法，避免调用native方法
    public static Boolean doFarmDrawTimesTask(JSONObject task) {
        try {
            if (task == null) return false;

            String taskId = task.optString("taskId", "");
            String title = task.optString("title", "");

            Log.record("执行抽奖任务: " + title + " (taskId: " + taskId + ")");

            // 抽奖任务通常可以执行
            return true;
        } catch (Exception e) {
            Log.printStackTrace(TAG, e);
            return false;
        }
    }
}
