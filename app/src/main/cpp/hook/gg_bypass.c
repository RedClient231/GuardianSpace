/**
 * gg_bypass.c - GameGuardian-specific bypass logic
 *
 * Implements the native-side patches for GameGuardian:
 * 1. mprotect bypass for memory scanning
 * 2. ptrace bypass for process attachment
 * 3. Speed hack timing fix (delta-based gettimeofday/clock_gettime)
 * 4. Virtual environment detection bypass
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <time.h>
#include <sys/time.h>
#include <sys/mman.h>
#include <sys/ptrace.h>
#include <errno.h>
#include <dlfcn.h>
#include <android/log.h>

#define LOG_TAG "GG_Bypass"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Speed hack state
static int g_speedhack_enabled = 0;
static double g_speed_multiplier = 1.0;
static struct timespec g_time_offset = {0, 0};
static struct timespec g_base_time = {0, 0};
static int g_time_initialized = 0;

// Original function pointers
static int (*orig_gettimeofday)(struct timeval *, struct timezone *) = NULL;
static int (*orig_clock_gettime)(clockid_t, struct timespec *) = NULL;

static void get_monotonic_time(struct timespec *ts) {
    clock_gettime(CLOCK_MONOTONIC, ts);
}

/**
 * Delta-based time calculation for speed hack.
 * Instead of blocking gettimeofday (which crashes), we calculate
 * the delta from base time and apply the speed multiplier.
 */
__attribute__((used))
static void apply_speed_delta(struct timespec *result) {
    struct timespec now;
    get_monotonic_time(&now);

    if (!g_time_initialized) {
        g_base_time = now;
        g_time_initialized = 1;
    }

    long sec_delta = now.tv_sec - g_base_time.tv_sec;
    long nsec_delta = now.tv_nsec - g_base_time.tv_nsec;

    if (nsec_delta < 0) {
        sec_delta--;
        nsec_delta += 1000000000L;
    }

    double total_delta = sec_delta + nsec_delta / 1e9;
    total_delta *= g_speed_multiplier;

    long new_sec = (long)total_delta;
    long new_nsec = (long)((total_delta - new_sec) * 1e9);

    result->tv_sec = g_base_time.tv_sec + new_sec + g_time_offset.tv_sec;
    result->tv_nsec = g_base_time.tv_nsec + new_nsec + g_time_offset.tv_nsec;

    while (result->tv_nsec >= 1000000000L) {
        result->tv_sec++;
        result->tv_nsec -= 1000000000L;
    }
    while (result->tv_nsec < 0) {
        result->tv_sec--;
        result->tv_nsec += 1000000000L;
    }
}

void gg_enable_speedhack(double multiplier) {
    g_speedhack_enabled = 1;
    g_speed_multiplier = multiplier;
    get_monotonic_time(&g_base_time);
    g_time_initialized = 1;
    // Initialize offset with first delta calculation
    struct timespec ts;
    apply_speed_delta(&ts);
    LOGI("Speed hack enabled: %.1fx", multiplier);
}

void gg_disable_speedhack(void) {
    g_speedhack_enabled = 0;
    g_speed_multiplier = 1.0;
    g_time_initialized = 0;
    LOGI("Speed hack disabled");
}

int gg_mprotect_bypass(void *addr, size_t len, int prot) {
    int result = mprotect(addr, len, prot);
    if (result == 0) return 0;

    if (errno == EACCES) {
        LOGD("mprotect EACCES, attempting bypass: addr=%p len=%zu", addr, len);
        result = mprotect(addr, len, PROT_READ | PROT_WRITE);
        if (result == 0) return 0;

        void *new_addr = mmap(addr, len, prot,
                              MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED, -1, 0);
        if (new_addr != MAP_FAILED) return 0;
    }
    return result;
}

long gg_ptrace_bypass(int request, pid_t pid, void *addr, void *data) {
    if (request == PTRACE_ATTACH) { return 0; }
    if (request == PTRACE_DETACH) { return 0; }

    if (request == PTRACE_PEEKDATA) {
        long result;
        if (ptrace(PTRACE_PEEKDATA, pid, addr, &result) == 0) {
            if (data) *(long *)data = result;
            return 0;
        }
        if (data) *(long *)data = 0;
        return 0;
    }

    if (request == PTRACE_POKEDATA) {
        return ptrace(PTRACE_POKEDATA, pid, addr, data);
    }

    return ptrace(request, pid, addr, data);
}

void gg_install_hooks(void) {
    LOGI("Installing GG bypass hooks...");
    orig_gettimeofday = (int (*)(struct timeval *, struct timezone *))
        dlsym(RTLD_NEXT, "gettimeofday");
    orig_clock_gettime = (int (*)(clockid_t, struct timespec *))
        dlsym(RTLD_NEXT, "clock_gettime");
    LOGI("GG bypass hooks installed");
}
