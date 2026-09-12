# Add project specific ProGuard rules here.

# ========================
# Xposed 框架入口类
# ========================
# LSPosed 入口，注册在 META-INF/xposed/java_init.list
-keep class io.github.aw1y2z.sesame.hook.LibXposedEntry { *; }
# LSPatch/NPatch 入口，注册在 assets/xposed_init
-keep class io.github.aw1y2z.sesame.hook.LegacyXposedEntry { *; }

# ========================
# 被反射加载的类（Class.forName）
# ========================
-keep class io.github.aw1y2z.sesame.model.extensions.ExtensionsHandle { *; }
-keep class io.github.aw1y2z.sesame.model.extensions.ExtensionsHandleAlpha { *; }
-keep class io.github.aw1y2z.sesame.model.task.antOrchard.AntOrchard { *; }

# ========================
# Jackson 序列化/反序列化
# 保留字段名，防止 JSON 解析失败
# ========================
-keepclassmembers class io.github.aw1y2z.sesame.entity.** { <fields>; }
-keepclassmembers class io.github.aw1y2z.sesame.model.** { <fields>; }
-keepclassmembers class io.github.aw1y2z.sesame.data.** { <fields>; }
-keepclassmembers class io.github.aw1y2z.sesame.util.Status { <fields>; }

# ========================
# Lombok @Data 生成的 getter/setter
# Jackson 反射调用方法名，需保留
# ========================
-keepclassmembers class io.github.aw1y2z.sesame.entity.** {
    public *** get*();
    public void set*(...);
}
-keepclassmembers class io.github.aw1y2z.sesame.model.** {
    public *** get*();
    public void set*(...);
}
-keepclassmembers class io.github.aw1y2z.sesame.data.** {
    public *** get*();
    public void set*(...);
}

# ========================
# Xposed 框架接口（防 R8 删接口）
# ========================
-keep class de.robv.android.xposed.** { *; }
-keep interface org.lsposed.lucid.** { *; }

# ========================
# 通用
# ========================
-dontwarn javax.annotation.**
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
