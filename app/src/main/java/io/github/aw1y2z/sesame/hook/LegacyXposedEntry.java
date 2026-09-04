package io.github.aw1y2z.sesame.hook;

import de.robv.android.xposed.IXposedHookLoadPackage;

import io.github.aw1y2z.sesame.util.XHelpers;

/**
 * 旧版 Xposed API 入口点（LSPatch / NPatch 等免 root 框架）。
 * <p>
 * 通过 assets/xposed_init 发现，不引用 io.github.libxposed.api.*，
 * 避免在 LSPatch 环境下因 XposedModule 类不存在而 ClassNotFoundException。
 * <p>
 * 委托给 {@link ApplicationHook#handleLoadPackage} 全部 hook 业务逻辑，
 * 与 LSPosed 路径（java_init.list → ApplicationHook）零重复代码。
 */
public class LegacyXposedEntry implements IXposedHookLoadPackage {

    private static volatile boolean inited = false;
    private static final String TARGET_PKG = "com.eg.android.AlipayGphone";

    @Override
    public void handleLoadPackage(de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PKG.equals(lpparam.packageName)) {
            return;
        }

        // 1) 注入引擎（幂等）
        XHelpers.init(new LegacyXposedBridgeHookEngine());

        // 2) 模拟 onModuleLoaded（仅一次）
        if (!inited) {
            ApplicationHook.onModuleLoadedInternal();
            inited = true;
        }

        // 3) 构造我们的 LoadPackageParam 并交给 hook 主逻辑
        io.github.aw1y2z.sesame.util.compat.XC_LoadPackage.LoadPackageParam ours =
                new io.github.aw1y2z.sesame.util.compat.XC_LoadPackage.LoadPackageParam();
        ours.packageName = lpparam.packageName;
        ours.processName = lpparam.processName;
        ours.classLoader = lpparam.classLoader;
        ApplicationHook.handleLoadPackage(ours);
    }
}
