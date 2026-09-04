package io.github.aw1y2z.sesame.hook;

import io.github.aw1y2z.sesame.util.compat.XC_MethodHook;

import java.lang.reflect.Member;

/**
 * 旧版 Xposed 引擎：走 de.robv.android.xposed.XposedBridge.hookMethod()。
 * 供 LSPatch / NPatch 等免 root 框架使用。
 * <p>
 * 适配器将自定义 {@link XC_MethodHook} 的 before/after 回调转接到
 * 旧版 de.robv.android.xposed.XC_MethodHook 的 MethodHookParam。
 */
public class LegacyXposedBridgeHookEngine implements HookEngine {

    @Override
    public XC_MethodHook.Unhook hook(Member member, XC_MethodHook callback) {
        de.robv.android.xposed.XC_MethodHook adapter = new de.robv.android.xposed.XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam legacy) throws Throwable {
                XC_MethodHook.MethodHookParam ours = new XC_MethodHook.MethodHookParam();
                ours.thisObject = legacy.thisObject;
                ours.args = legacy.args;
                ours.hasResult = false;
                callback.callBefore(ours);
                if (ours.hasResult) {
                    legacy.setResult(ours.result);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam legacy) throws Throwable {
                XC_MethodHook.MethodHookParam ours = new XC_MethodHook.MethodHookParam();
                ours.thisObject = legacy.thisObject;
                ours.args = legacy.args;
                ours.result = legacy.getResult();
                ours.hasResult = true;
                ours.exception = legacy.getThrowable();
                callback.callAfter(ours);
                if (ours.hasResult) {
                    legacy.setResult(ours.result);
                }
            }
        };
        de.robv.android.xposed.XC_MethodHook.Unhook u =
                de.robv.android.xposed.XposedBridge.hookMethod(member, adapter);
        return u::unhook;
    }

    @Override
    public int getApiVersion() {
        return 82;
    }

    @Override
    public String getFrameworkName() {
        return "LSPatch";
    }

    @Override
    public String getFrameworkVersion() {
        try {
            return String.valueOf(de.robv.android.xposed.XposedBridge.getXposedVersion());
        } catch (Throwable t) {
            return "unknown";
        }
    }

    @Override
    public void log(int priority, String tag, String msg) {
        android.util.Log.println(priority, tag, msg);
    }
}
