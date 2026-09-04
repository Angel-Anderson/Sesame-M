package io.github.aw1y2z.sesame.hook;

import android.util.Log;

import io.github.aw1y2z.sesame.util.compat.XC_MethodHook;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * API 102 引擎：包装 {@link XposedModule} 实例，走 libxposed API 102 的 hook 链。
 * 逻辑从原 XHelpers.hookMember() 第 199-233 行原样搬入。
 */
public class LibXposedHookEngine implements HookEngine {

    private final XposedModule module;
    private static final AtomicInteger SEQ = new AtomicInteger(0);

    public LibXposedHookEngine(XposedModule module) {
        this.module = module;
    }

    @Override
    public XC_MethodHook.Unhook hook(Member member, XC_MethodHook callback) {
        XposedInterface.HookHandle handle = module.hook((Executable) member)
                .setId("xh_" + SEQ.incrementAndGet())
                .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                .intercept(chain -> {
                    XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
                    param.thisObject = chain.getThisObject();
                    param.args = chain.getArgs().toArray();
                    try {
                        callback.callBefore(param);
                    } catch (Throwable t) {
                        Log.e("XHelpers", "beforeHookedMethod error", t);
                        return chain.proceed(param.args);
                    }
                    if (param.hasResult) {
                        return param.result;
                    }
                    Object result;
                    try {
                        result = chain.proceed(param.args);
                    } catch (Throwable t) {
                        Log.e("XHelpers", "proceed error", t);
                        throw t;
                    }
                    param.result = result;
                    param.hasResult = true;
                    try {
                        callback.callAfter(param);
                    } catch (Throwable t) {
                        Log.e("XHelpers", "afterHookedMethod error", t);
                        return param.result;
                    }
                    return param.result;
                });
        return () -> handle.unhook();
    }

    @Override
    public int getApiVersion() {
        return module.getApiVersion();
    }

    @Override
    public String getFrameworkName() {
        return module.getFrameworkName();
    }

    @Override
    public String getFrameworkVersion() {
        return module.getFrameworkVersion();
    }

    @Override
    public void log(int priority, String tag, String msg) {
        module.log(priority, tag, msg);
    }
}
