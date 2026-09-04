package io.github.aw1y2z.sesame.hook;

import io.github.aw1y2z.sesame.util.compat.XC_MethodHook;

import java.lang.reflect.Member;

/**
 * Hook 引擎抽象层：屏蔽 LSPosed (API 102) 与 LSPatch (旧版 XposedBridge) 的差异。
 * <p>
 * 两个实现分别走 {@link LibXposedHookEngine}（API 102）和
 * {@link LegacyXposedBridgeHookEngine}（旧版 de.robv.android.xposed）。
 */
public interface HookEngine {

    /**
     * Hook 一个方法或构造函数。
     *
     * @param member   要 hook 的方法/构造函数
     * @param callback before/after 回调
     * @return Unhook 句柄
     */
    XC_MethodHook.Unhook hook(Member member, XC_MethodHook callback);

    int getApiVersion();

    String getFrameworkName();

    String getFrameworkVersion();

    void log(int priority, String tag, String msg);
}
