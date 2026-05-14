#ifndef BLACKBOX_CORE_H
#define BLACKBOX_CORE_H

#include <jni.h>
#include <string>

#ifdef __cplusplus
extern "C" {
#endif

// Initialize the virtualization engine
JNIEXPORT jint JNICALL
Java_com_tools_vspace_blackbox_BlackBoxEngine_nativeInit(JNIEnv *env, jobject thiz, jstring dataDir);

// Create a virtual process
JNIEXPORT jint JNICALL
Java_com_tools_vspace_blackbox_BlackBoxEngine_nativeCreateProcess(
    JNIEnv *env, jobject thiz, jstring packageName, jstring apkPath, jint uid);

// Hook a system call in the virtual process
JNIEXPORT jint JNICALL
Java_com_tools_vspace_blackbox_BlackBoxEngine_nativeHookSyscall(
    JNIEnv *env, jobject thiz, jint pid, jstring syscallName);

// Get virtual environment info
JNIEXPORT jstring JNICALL
Java_com_tools_vspace_blackbox_BlackBoxEngine_nativeGetVEnvInfo(JNIEnv *env, jobject thiz);

// Spoof device info for anti-detection
JNIEXPORT void JNICALL
Java_com_tools_vspace_blackbox_BlackBoxEngine_nativeSpoofDeviceInfo(
    JNIEnv *env, jobject thiz, jstring model, jstring manufacturer, jstring brand);

#ifdef __cplusplus
}
#endif

#endif // BLACKBOX_CORE_H
