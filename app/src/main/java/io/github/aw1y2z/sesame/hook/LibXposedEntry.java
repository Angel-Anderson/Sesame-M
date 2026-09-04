package io.github.aw1y2z.sesame.hook;

import androidx.annotation.NonNull;

import io.github.aw1y2z.sesame.util.XHelpers;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * libxposed API 102（LSPosed）专用入口点。
 * <p>
 * 通过 META-INF/xposed/java_init.list 发现，由 LSPosed 框架实例化。
 * 本类持有对 {@link XposedModule} 的继承，是 libxposed API 的唯一承载体；
 * 业务 hook 逻辑全部委托给静态的 {@link ApplicationHook}，
 * 以便 LSPatch / NPatch 路径（{@link LegacyXposedEntry}）复用同一套逻辑，
 * 且在不存在 libxposed 类的环境中不会触发 NoClassDefFoundError。
 */
public class LibXposedEntry extends XposedModule {

    // 本类仅提供无参构造函数：LSPosed 框架先通过无参构造实例化，
    // 再调用父类 XposedInterfaceWrapper.attachFramework(...) 注入 XposedInterface，
    // 之后 hook()/getApiVersion() 等能力才可用。生命周期回调在 attach 之后触发。

    @Override
    public void onModuleLoaded(@NonNull XposedModuleInterface.ModuleLoadedParam param) {
        XHelpers.init(new LibXposedHookEngine(this));
        ApplicationHook.onModuleLoadedInternal();
    }

    @Override
    public void onPackageReady(@NonNull XposedModuleInterface.PackageReadyParam param) {
        ApplicationHook.onPackageReadyInternal(param.getPackageName(), param.getClassLoader());
    }
}
