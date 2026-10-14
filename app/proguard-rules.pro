-keep class com.handheld.playtime.hook.SystemUIHook { *; }
-keep class com.handheld.playtime.MainActivity {
    public static boolean isModuleActive();
}
-keep class com.handheld.playtime.config.** { *; }
-keep class com.handheld.playtime.battery.** { *; }
