/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "NetworkStatsNative"

#include <cutils/qtaguid.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <inttypes.h>
#include <jni.h>
#include <nativehelper/ScopedUtfChars.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <utils/Log.h>
#include <utils/misc.h>

#include "bpf/BpfUtils.h"
#include "netdbpf/BpfNetworkStats.h"
#include "netdbpf/NetworkTraceHandler.h"

using android::bpf::bpfGetUidStats;
using android::bpf::bpfGetIfaceStats;
using android::bpf::bpfRegisterIface;
using android::bpf::NetworkTraceHandler;

namespace android {

static const char* QTAGUID_IFACE_STATS = "/proc/net/xt_qtaguid/iface_stat_fmt";
static const char* QTAGUID_UID_STATS = "/proc/net/xt_qtaguid/stats";

static void nativeRegisterIface(JNIEnv* env, jclass clazz, jstring iface) {
    ScopedUtfChars iface8(env, iface);
    if (iface8.c_str() == nullptr) return;
    bpfRegisterIface(iface8.c_str());
}

static jobject statsValueToEntry(JNIEnv* env, StatsValue* stats) {
    // Find the Java class that represents the structure
    jclass gEntryClass = env->FindClass("android/net/NetworkStats$Entry");
    if (gEntryClass == nullptr) {
        return nullptr;
    }

    // Find the constructor.
    jmethodID constructorID = env->GetMethodID(gEntryClass, "<init>", "()V");
    if (constructorID == nullptr) {
        return nullptr;
    }

    // Create a new instance of the Java class
    jobject result = env->NewObject(gEntryClass, constructorID);
    if (result == nullptr) {
        return nullptr;
    }

    // Set the values of the structure fields in the Java object
    env->SetLongField(result, env->GetFieldID(gEntryClass, "rxBytes", "J"), stats->rxBytes);
    env->SetLongField(result, env->GetFieldID(gEntryClass, "txBytes", "J"), stats->txBytes);
    env->SetLongField(result, env->GetFieldID(gEntryClass, "rxPackets", "J"), stats->rxPackets);
    env->SetLongField(result, env->GetFieldID(gEntryClass, "txPackets", "J"), stats->txPackets);

    return result;
}

static int parseIfaceStats(const char* iface, StatsValue* stats) {
    FILE *fp = fopen(QTAGUID_IFACE_STATS, "r");
    if (fp == NULL) {
        return -1;
    }

    char buffer[384];
    char cur_iface[32];
    uint64_t rxBytes, rxPackets, txBytes, txPackets, tcpRxPackets, tcpTxPackets;

    while (fgets(buffer, sizeof(buffer), fp) != NULL) {
        int matched = sscanf(buffer, "%31s %" SCNu64 " %" SCNu64 " %" SCNu64
                " %" SCNu64 " " "%*u %" SCNu64 " %*u %*u %*u %*u "
                "%*u %" SCNu64 " %*u %*u %*u %*u", cur_iface, &rxBytes,
                &rxPackets, &txBytes, &txPackets, &tcpRxPackets, &tcpTxPackets);
        if (matched >= 5) {
            if (!iface || !strcmp(iface, cur_iface)) {
                stats->rxBytes += rxBytes;
                stats->rxPackets += rxPackets;
                stats->txBytes += txBytes;
                stats->txPackets += txPackets;
            }
        }
    }

    if (fclose(fp) != 0) {
        return -1;
    }
    return 0;
}

static int parseUidStats(const uint32_t uid, StatsValue* stats) {
    FILE *fp = fopen(QTAGUID_UID_STATS, "r");
    if (fp == NULL) {
        return -1;
    }

    char buffer[384];
    char iface[32];
    uint32_t idx, cur_uid, set;
    uint64_t tag, rxBytes, rxPackets, txBytes, txPackets;

    while (fgets(buffer, sizeof(buffer), fp) != NULL) {
        if (sscanf(buffer,
                "%" SCNu32 " %31s 0x%" SCNx64 " %u %u %" SCNu64 " %" SCNu64
                " %" SCNu64 " %" SCNu64 "",
                &idx, iface, &tag, &cur_uid, &set, &rxBytes, &rxPackets,
                &txBytes, &txPackets) == 9) {
            if (uid == cur_uid && tag == 0L) {
                stats->rxBytes += rxBytes;
                stats->rxPackets += rxPackets;
                stats->txBytes += txBytes;
                stats->txPackets += txPackets;
            }
        }
    }

    if (fclose(fp) != 0) {
        return -1;
    }
    return 0;
}

static jobject nativeGetTotalStat(JNIEnv* env, jclass clazz) {
    StatsValue stats = {};

    if (bpfGetIfaceStats(nullptr, &stats) == 0) {
        return statsValueToEntry(env, &stats);
    } else {
        if (parseIfaceStats(nullptr, &stats) == 0) {
            return statsValueToEntry(env, &stats);
        } else {
            return nullptr;
        }
    }
}

static jobject nativeGetIfaceStat(JNIEnv* env, jclass clazz, jstring iface) {
    ScopedUtfChars iface8(env, iface);
    if (iface8.c_str() == nullptr) {
        return nullptr;
    }

    StatsValue stats = {};

    if (bpfGetIfaceStats(iface8.c_str(), &stats) == 0) {
        return statsValueToEntry(env, &stats);
    } else {
        if (parseIfaceStats(iface8.c_str(), &stats) == 0) {
            return statsValueToEntry(env, &stats);
        } else {
            return nullptr;
        }
    }
}

static jobject nativeGetUidStat(JNIEnv* env, jclass clazz, jint uid) {
    StatsValue stats = {};

    if (bpfGetUidStats(uid, &stats) == 0) {
        return statsValueToEntry(env, &stats);
    } else {
        if (parseUidStats(uid, &stats) == 0) {
            return statsValueToEntry(env, &stats);
        } else {
            return nullptr;
        }
    }
}

static void nativeInitNetworkTracing(JNIEnv* env, jclass clazz) {
    NetworkTraceHandler::InitPerfettoTracing();
}

static const JNINativeMethod gMethods[] = {
        {
            "nativeRegisterIface",
            "(Ljava/lang/String;)V",
            (void*)nativeRegisterIface
        },
        {
            "nativeGetTotalStat",
            "()Landroid/net/NetworkStats$Entry;",
            (void*)nativeGetTotalStat
        },
        {
            "nativeGetIfaceStat",
            "(Ljava/lang/String;)Landroid/net/NetworkStats$Entry;",
            (void*)nativeGetIfaceStat
        },
        {
            "nativeGetUidStat",
            "(I)Landroid/net/NetworkStats$Entry;",
            (void*)nativeGetUidStat
        },
        {
            "nativeInitNetworkTracing",
            "()V",
            (void*)nativeInitNetworkTracing
        },
};

int register_android_server_net_NetworkStatsService(JNIEnv* env) {
    return jniRegisterNativeMethods(env,
            "android/net/connectivity/com/android/server/net/NetworkStatsService", gMethods,
            NELEM(gMethods));
}

}
