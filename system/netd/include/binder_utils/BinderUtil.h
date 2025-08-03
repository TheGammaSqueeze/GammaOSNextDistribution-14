/*
 * Copyright (C) 2019 The Android Open Source Project
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

#pragma once

#include "NetdPermissions.h"

#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/Status.h>
#include <fmt/format.h>
#include <private/android_filesystem_config.h>

#ifdef ANDROID_BINDER_STATUS_H
#define IS_BINDER_OK(__ex__) (__ex__ == ::android::binder::Status::EX_NONE)

#define EXCEPTION_TO_STRING(__ex__, str)    \
    case ::android::binder::Status::__ex__: \
        return str;

#define TO_EXCEPTION(__ex__) __ex__;

#else
#define IS_BINDER_OK(__ex__) (AStatus_isOk(AStatus_fromExceptionCode(__ex__)))

#define EXCEPTION_TO_STRING(__ex__, str) \
    case __ex__:                         \
        return str;

#define TO_EXCEPTION(__ex__) AStatus_getExceptionCode(AStatus_fromExceptionCode(__ex__));

#endif

inline std::string exceptionToString(int32_t exception) {
    switch (exception) {
        EXCEPTION_TO_STRING(EX_SECURITY, "SecurityException")
        EXCEPTION_TO_STRING(EX_BAD_PARCELABLE, "BadParcelableException")
        EXCEPTION_TO_STRING(EX_ILLEGAL_ARGUMENT, "IllegalArgumentException")
        EXCEPTION_TO_STRING(EX_NULL_POINTER, "NullPointerException")
        EXCEPTION_TO_STRING(EX_ILLEGAL_STATE, "IllegalStateException")
        EXCEPTION_TO_STRING(EX_NETWORK_MAIN_THREAD, "NetworkMainThreadException")
        EXCEPTION_TO_STRING(EX_UNSUPPORTED_OPERATION, "UnsupportedOperationException")
        EXCEPTION_TO_STRING(EX_SERVICE_SPECIFIC, "ServiceSpecificException")
        EXCEPTION_TO_STRING(EX_PARCELABLE, "ParcelableException")
        EXCEPTION_TO_STRING(EX_TRANSACTION_FAILED, "TransactionFailedException")
        default:
            return "UnknownException";
    }
}

using LogFn = std::function<void(const std::string& msg)>;

template <typename LogType>
void binderCallLogFn(const LogType& log, const LogFn& logFn) {
    using namespace std::string_literals;

    bool hasReturnArgs;
    std::string output;

    hasReturnArgs = !log.result.empty();
    output.append(log.method_name + "("s);

    // input args
    for (size_t i = 0; i < log.input_args.size(); ++i) {
        output.append(log.input_args[i].second);
        if (i != log.input_args.size() - 1) {
            output.append(", "s);
        }
    }
    output.append(")"s);

    const int exceptionCode = TO_EXCEPTION(log.exception_code);

    if (hasReturnArgs || !IS_BINDER_OK(exceptionCode)) {
        output.append(" -> "s);
    }

    // return status
    if (!IS_BINDER_OK(exceptionCode)) {
        // an exception occurred
        const int errCode = log.service_specific_error_code;
        output.append(fmt::format("{}({}, \"{}\")", exceptionToString(exceptionCode),
                                  (errCode != 0) ? errCode : exceptionCode, log.exception_message));
    }
    // return args
    if (hasReturnArgs) {
        output.append("{" + log.result + "}");
    }
    // duration time
    output.append(fmt::format(" <{:.2f}ms>", log.duration_ms));

    // escape newline characters to avoid multiline log entries
    logFn(::android::base::StringReplace(output, "\n", "\\n", true));
}

// The input permissions should be equivalent that this function would return ok if any of them is
// granted.
inline android::binder::Status checkAnyPermission(const std::vector<const char*>& permissions) {
    pid_t pid = android::IPCThreadState::self()->getCallingPid();
    uid_t uid = android::IPCThreadState::self()->getCallingUid();

    // TODO: Do the pure permission check in this function. Have another method
    // (e.g. checkNetworkStackPermission) to wrap AID_SYSTEM and
    // AID_NETWORK_STACK uid check.
    // If the caller is the system UID, don't check permissions.
    // Otherwise, if the system server's binder thread pool is full, and all the threads are
    // blocked on a thread that's waiting for us to complete, we deadlock. http://b/69389492
    //
    // From a security perspective, there is currently no difference, because:
    // 1. The system server has the NETWORK_STACK permission, which grants access to all the
    //    IPCs in this file.
    // 2. AID_SYSTEM always has all permissions. See ActivityManager#checkComponentPermission.
    if (uid == AID_SYSTEM) {
        return android::binder::Status::ok();
    }
    // AID_NETWORK_STACK own MAINLINE_NETWORK_STACK permission, don't IPC to system server to check
    // MAINLINE_NETWORK_STACK permission. Cross-process(netd, networkstack and system server)
    // deadlock: http://b/149766727
    if (uid == AID_NETWORK_STACK) {
        for (const char* permission : permissions) {
            if (std::strcmp(permission, PERM_MAINLINE_NETWORK_STACK) == 0) {
                return android::binder::Status::ok();
            }
        }
    }

    for (const char* permission : permissions) {
        if (checkPermission(android::String16(permission), pid, uid)) {
            return android::binder::Status::ok();
        }
    }

    auto err = android::base::StringPrintf(
            "UID %d / PID %d does not have any of the following permissions: %s", uid, pid,
            android::base::Join(permissions, ',').c_str());
    return android::binder::Status::fromExceptionCode(android::binder::Status::EX_SECURITY,
                                                      err.c_str());
}

inline android::binder::Status statusFromErrcode(int ret) {
    if (ret) {
        return android::binder::Status::fromServiceSpecificError(-ret, strerror(-ret));
    }
    return android::binder::Status::ok();
}

#define DEPRECATED \
    return binder::Status::fromExceptionCode(binder::Status::EX_UNSUPPORTED_OPERATION)
