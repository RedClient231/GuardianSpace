#include <jni.h>
#include <android/log.h>
#include <string>
#include <map>
#include <dlfcn.h>
#include <unistd.h>
#include <sys/mman.h>
#include <cstring>

#define LOG_TAG "BlackBoxCore"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Virtual process table
struct VirtualProcess {
    int pid;
    std::string packageName;
    std::string apkPath;
    int uid;
    bool isActive;
};

static std::map<int, VirtualProcess> sProcessTable;
static std::string sBaseDataDir;
static int sNextPid = 10000;

// Device spoof info
static std::string sSpoofModel = "Pixel 5";
static std::string sSpoofManufacturer = "Google";
static std::string sSpoofBrand = "google";

static jstring stringToJString(JNIEnv *env, const std::string &str) {
    return env->NewStringUTF(str.c_str());
}

static std::string jstringToString(JNIEnv *env, jstring jstr) {
    if (!jstr) return "";
    const char *chars = env->GetStringUTFChars(jstr, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return result;
}

extern "C" {

JNIEXPORT jint JNICALL
Java_com_tools_vspace_blackbox_BlackBoxEngine_nativeInit(JNIEnv *env, jobject thiz, jstring dataDir) {
    sBaseDataDir = jstringToString(env, dataDir);
    LOGI("BlackBoxCore initialized with data dir: %s", sBaseDataDir.c_str());

    // Create necessary directories
    std::string vdataDir = sBaseDataDir + "/virtual_data";
    mkdir(vdataDir.c_str(), 0755);

    return 0;
}

JNIEXPORT jint JNICALL
Java_com_tools_vspace_blackbox_BlackBoxEngine_nativeCreateProcess(
    JNIEnv *env, jobject thiz, jstring packageName, jstring apkPath, jint uid) {

    VirtualProcess proc;
    proc.pid = sNextPid++;
    proc.packageName = jstringToString(env, packageName);
    proc.apkPath = jstringToString(env, apkPath);
    proc.uid = uid;
    proc.isActive = true;

    sProcessTable[proc.pid] = proc;

    LOGI("Created virtual process: pid=%d, pkg=%s, uid=%d",
         proc.pid, proc.packageName.c_str(), proc.uid);

    return proc.pid;
}

JNIEXPORT jint JNICALL
Java_com_tools_vspace_blackbox_BlackBoxEngine_nativeHookSyscall(
    JNIEnv *env, jobject thiz, jint pid, jstring syscallName) {

    std::string name = jstringToString(env, syscallName);
    LOGD("Hooking syscall '%s' for pid %d", name.c_str(), pid);

    // Placeholder - actual PLT hooking is done by ggfix native lib
    return 0;
}

JNIEXPORT jstring JNICALL
Java_com_tools_vspace_blackbox_BlackBoxEngine_nativeGetVEnvInfo(JNIEnv *env, jobject thiz) {
    std::string info = "{";
    info += "\"model\":\"" + sSpoofModel + "\",";
    info += "\"manufacturer\":\"" + sSpoofManufacturer + "\",";
    info += "\"brand\":\"" + sSpoofBrand + "\",";
    info += "\"processCount\":" + std::to_string(sProcessTable.size());
    info += "}";
    return stringToJString(env, info);
}

JNIEXPORT void JNICALL
Java_com_tools_vspace_blackbox_BlackBoxEngine_nativeSpoofDeviceInfo(
    JNIEnv *env, jobject thiz, jstring model, jstring manufacturer, jstring brand) {

    sSpoofModel = jstringToString(env, model);
    sSpoofManufacturer = jstringToString(env, manufacturer);
    sSpoofBrand = jstringToString(env, brand);

    LOGI("Device spoofed: %s %s (%s)", sSpoofManufacturer.c_str(), sSpoofModel.c_str(), sSpoofBrand.c_str());
}

} // extern "C"
