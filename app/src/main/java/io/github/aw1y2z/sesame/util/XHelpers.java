package io.github.aw1y2z.sesame.util;

import io.github.aw1y2z.sesame.hook.HookEngine;
import io.github.aw1y2z.sesame.util.compat.XC_MethodHook;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * API 102 临时迁移兼容层（桥接旧 XposedHelpers）。
 *
 * <p>hook 调用通过 {@link HookEngine} 派发，屏蔽 LSPosed（libxposed API 102）
 * 与 LSPatch / NPatch（旧版 de.robv.android.xposed）的框架差异。
 * 业务代码仅需把 {@code XHelpers.} 替换为 {@code XHelpers.} 即可，hook 逻辑保持不变。
 */
public class XHelpers {

    private static final String TAG = "XHelpers";
    private static HookEngine sEngine;

    /**
     * 注入 hook 引擎。LSPosed 路径传入 LibXposedHookEngine，
     * LSPatch / NPatch 路径传入 LegacyXposedBridgeHookEngine。
     * 本类不引用任何具体框架类，确保在缺少 libxposed 的环境中可被安全加载。
     */
    public static void init(HookEngine engine) {
        sEngine = engine;
    }

    public static int getApiVersion() {
        return sEngine.getApiVersion();
    }

    public static String getFrameworkName() {
        return sEngine.getFrameworkName();
    }

    public static String getFrameworkVersion() {
        return sEngine.getFrameworkVersion();
    }

    public static void log(int priority, String tag, String msg) {
        sEngine.log(priority, tag, msg);
    }

    private static void ensureInit() {
        if (sEngine == null) {
            throw new IllegalStateException("XHelpers 未初始化，请在入口点调用 XHelpers.init(...)");
        }
    }

    // ----------------------------------------------------------------
    // 类 / 方法 / 字段查找
    // ----------------------------------------------------------------

    public static Class<?> findClass(String name, ClassLoader cl) {
        try {
            return cl.loadClass(name);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Class not found: " + name, e);
        }
    }

    public static Class<?> findClassIfExists(String name, ClassLoader cl) {
        try {
            return cl.loadClass(name);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Method findMethodExact(Class<?> clazz, String name, Class<?>... paramTypes) {
        try {
            return clazz.getDeclaredMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Method not found: " + clazz.getName() + "#" + name, e);
        }
    }

    public static Constructor<?> findConstructorExact(Class<?> clazz, Class<?>... paramTypes) {
        try {
            return clazz.getDeclaredConstructor(paramTypes);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Constructor not found: " + clazz.getName(), e);
        }
    }

    public static Field findField(Class<?> clazz, String fieldName) {
        Class<?> c = clazz;
        while (c != null) {
            try {
                return c.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        throw new RuntimeException("Field not found: " + clazz.getName() + "#" + fieldName);
    }

    // ----------------------------------------------------------------
    // 调用 / 字段读写
    // ----------------------------------------------------------------

    public static Object callMethod(Object obj, String name, Object... args) {
        Method m = findMethodBestMatch(obj.getClass(), name, args, false);
        try {
            m.setAccessible(true);
            return m.invoke(obj, args);
        } catch (Exception e) {
            throw new RuntimeException("callMethod failed: " + name, e);
        }
    }

    public static Object callStaticMethod(Class<?> clazz, String name, Object... args) {
        Method m = findMethodBestMatch(clazz, name, args, true);
        try {
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (Exception e) {
            throw new RuntimeException("callStaticMethod failed: " + name, e);
        }
    }

    public static Object getObjectField(Object obj, String fieldName) {
        try {
            Field f = findField(obj.getClass(), fieldName);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception e) {
            throw new RuntimeException("getObjectField failed: " + fieldName, e);
        }
    }

    public static void setObjectField(Object obj, String fieldName, Object value) {
        try {
            Field f = findField(obj.getClass(), fieldName);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) {
            throw new RuntimeException("setObjectField failed: " + fieldName, e);
        }
    }

    public static Object getStaticObjectField(Class<?> clazz, String fieldName) {
        try {
            Field f = findField(clazz, fieldName);
            f.setAccessible(true);
            return f.get(null);
        } catch (Exception e) {
            throw new RuntimeException("getStaticObjectField failed: " + fieldName, e);
        }
    }

    public static void setStaticObjectField(Class<?> clazz, String fieldName, Object value) {
        try {
            Field f = findField(clazz, fieldName);
            f.setAccessible(true);
            f.set(null, value);
        } catch (Exception e) {
            throw new RuntimeException("setStaticObjectField failed: " + fieldName, e);
        }
    }

    public static Object newInstance(Class<?> clazz, Object... args) {
        Class<?>[] argTypes = getArgTypes(args);
        Constructor<?> c = findConstructorBestMatch(clazz, argTypes);
        try {
            c.setAccessible(true);
            return c.newInstance(args);
        } catch (Exception e) {
            throw new RuntimeException("newInstance failed: " + clazz.getName(), e);
        }
    }

    // ----------------------------------------------------------------
    // Hook 桥接
    // ----------------------------------------------------------------

    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String name, Object... argsAndCallback) {
        int last = argsAndCallback.length - 1;
        XC_MethodHook callback = (XC_MethodHook) argsAndCallback[last];
        Class<?>[] paramTypes = new Class<?>[last];
        for (int i = 0; i < last; i++) {
            paramTypes[i] = (Class<?>) argsAndCallback[i];
        }
        Method m = findMethodExact(clazz, name, paramTypes);
        return hookMember(m, callback);
    }

    public static XC_MethodHook.Unhook findAndHookMethod(String className, ClassLoader cl, String name, Object... argsAndCallback) {
        Class<?> clazz = findClass(className, cl);
        return findAndHookMethod(clazz, name, argsAndCallback);
    }

    public static XC_MethodHook.Unhook findAndHookConstructor(Class<?> clazz, Object... argsAndCallback) {
        int last = argsAndCallback.length - 1;
        XC_MethodHook callback = (XC_MethodHook) argsAndCallback[last];
        Class<?>[] paramTypes = new Class<?>[last];
        for (int i = 0; i < last; i++) {
            paramTypes[i] = (Class<?>) argsAndCallback[i];
        }
        Constructor<?> c = findConstructorExact(clazz, paramTypes);
        return hookMember(c, callback);
    }

    public static XC_MethodHook.Unhook hookMember(Member member, XC_MethodHook callback) {
        ensureInit();
        return sEngine.hook(member, callback);
    }

    // ----------------------------------------------------------------
    // 内部辅助
    // ----------------------------------------------------------------

    private static Class<?>[] getArgTypes(Object... args) {
        if (args == null) {
            return new Class<?>[0];
        }
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = (args[i] != null) ? args[i].getClass() : null;
        }
        return types;
    }

    private static Method findMethodBestMatch(Class<?> clazz, String name, Object[] args, boolean staticOnly) {
        Class<?>[] argTypes = getArgTypes(args);
        Method m = searchMethods(clazz, name, argTypes, staticOnly, true);
        if (m != null) {
            return m;
        }
        m = searchMethods(clazz, name, argTypes, staticOnly, false);
        if (m != null) {
            return m;
        }
        throw new RuntimeException("No method " + name + " matching args in " + clazz.getName());
    }

    private static Method searchMethods(Class<?> clazz, String name, Class<?>[] argTypes, boolean staticOnly, boolean declaredOnly) {
        Method[] methods = declaredOnly ? clazz.getDeclaredMethods() : clazz.getMethods();
        for (Method m : methods) {
            if (!m.getName().equals(name)) {
                continue;
            }
            if (staticOnly && !Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            Class<?>[] pts = m.getParameterTypes();
            if (pts.length != argTypes.length) {
                continue;
            }
            boolean ok = true;
            for (int i = 0; i < pts.length; i++) {
                if (argTypes[i] != null && !isAssignable(pts[i], argTypes[i])) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return m;
            }
        }
        return null;
    }

    private static Constructor<?> findConstructorBestMatch(Class<?> clazz, Class<?>[] argTypes) {
        for (Constructor<?> c : clazz.getDeclaredConstructors()) {
            Class<?>[] pts = c.getParameterTypes();
            if (pts.length != argTypes.length) {
                continue;
            }
            boolean ok = true;
            for (int i = 0; i < pts.length; i++) {
                if (argTypes[i] != null && !isAssignable(pts[i], argTypes[i])) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return c;
            }
        }
        throw new RuntimeException("No constructor matching args in " + clazz.getName());
    }

    private static boolean isAssignable(Class<?> paramType, Class<?> argType) {
        if (paramType.isPrimitive()) {
            Class<?> boxed = PRIMITIVE_BOX.get(paramType);
            return boxed != null && boxed.equals(argType);
        }
        return paramType.isAssignableFrom(argType);
    }

    private static final Map<Class<?>, Class<?>> PRIMITIVE_BOX = new HashMap<>();

    static {
        PRIMITIVE_BOX.put(int.class, Integer.class);
        PRIMITIVE_BOX.put(long.class, Long.class);
        PRIMITIVE_BOX.put(boolean.class, Boolean.class);
        PRIMITIVE_BOX.put(double.class, Double.class);
        PRIMITIVE_BOX.put(float.class, Float.class);
        PRIMITIVE_BOX.put(short.class, Short.class);
        PRIMITIVE_BOX.put(byte.class, Byte.class);
        PRIMITIVE_BOX.put(char.class, Character.class);
    }
}
