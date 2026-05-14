/**
 * main.cpp - Entry point for libggfix.so native library
 *
 * Initializes the hooking framework and registers JNI bridges.
 */

#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <sys/types.h>

#define LOG_TAG "GGFixMain"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// Forward declarations from xhook.c
extern "C" {
    void xhook_install_gg_hooks(void);
    void xhook_refresh(int async);
    void xhook_clear(void);
}

// Forward declarations from gg_bypass.c
extern "C" {
    void gg_install_hooks(void);
    void gg_enable_speedhack(double multiplier);
    void gg_disable_speedhack(void);
    int gg_mprotect_bypass(void *addr, size_t len, int prot);
    long gg_ptrace_bypass(int request, pid_t pid, void *addr, void *data);
}

__attribute__((constructor))
static void on_library_load(void) {
    LOGI("libggfix.so loaded, initializing hooks...");
    xhook_install_gg_hooks();
    gg_install_hooks();
    LOGI("All hooks initialized");
}

__attribute__((destructor))
static void on_library_unload(void) {
    LOGI("libggfix.so unloading, cleaning up...");
    xhook_clear();
    gg_disable_speedhack();
}

// JNI bridge for GG_Patcher.java
extern "C" JNIEXPORT void JNICALL
Java_com_tools_vspace_engine_patch_GG_1Patcher_nativeHookGG(
    JNIEnv *env, jobject /*thiz*/, jstring packageName) {
    const char *pkg = env->GetStringUTFChars(packageName, nullptr);
    LOGI("Native hook GG for package: %s", pkg);
    xhook_install_gg_hooks();
    gg_install_hooks();
    env->ReleaseStringUTFChars(packageName, pkg);
}

extern "C" JNIEXPORT void JNICALL
Java_com_tools_vspace_engine_patch_GG_1Patcher_nativeApplyTimeHook(
    JNIEnv * /*env*/, jobject /*thiz*/) {
    LOGI("Applying time hook for speed hack");
    gg_enable_speedhack(1.0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_tools_vspace_engine_patch_GG_1Patcher_nativeUnhookGG(
    JNIEnv *env, jobject /*thiz*/, jstring packageName) {
    const char *pkg = env->GetStringUTFChars(packageName, nullptr);
    LOGI("Unhooking GG for package: %s", pkg);
    gg_disable_speedhack();
    env->ReleaseStringUTFChars(packageName, pkg);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tools_vspace_engine_patch_GG_1Patcher_nativeInit(
    JNIEnv *env, jobject /*thiz*/, jstring packageName) {
    const char *pkg = env->GetStringUTFChars(packageName, nullptr);
    LOGI("Initializing GG fix for: %s", pkg);
    xhook_install_gg_hooks();
    gg_install_hooks();
    env->ReleaseStringUTFChars(packageName, pkg);
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_tools_vspace_engine_patch_GG_1Patcher_nativeSetSpeed(
    JNIEnv * /*env*/, jobject /*thiz*/, jfloat multiplier) {
    LOGI("Speed hack set to: %.1fx", multiplier);
    if (multiplier <= 0) {
        gg_disable_speedhack();
    } else {
        gg_enable_speedhack((double)multiplier);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_tools_vspace_engine_patch_GG_1Patcher_nativeIsHooked(
    JNIEnv * /*env*/, jobject /*thiz*/) {
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_tools_vspace_engine_patch_GG_1Patcher_nativeGetVersion(
    JNIEnv *env, jobject /*thiz*/) {
    return env->NewStringUTF("1.0.0-ggfix");
}
