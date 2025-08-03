/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/result.h>
#include <jni.h>
#include <time.h>

using android::base::ErrnoError;
using android::base::Error;
using android::base::Result;
using android::base::WriteStringToFd;

constexpr size_t kNumBytesPerMB = 1024 * 1024;

Result<double> measure_send_rate(int fd, int num_bytes_to_send) {
    std::string data;
    data.assign(num_bytes_to_send, 'a');
    struct timespec start;
    if (clock_gettime(CLOCK_MONOTONIC, &start) == -1) {
        return ErrnoError() << "failed to clock_gettime";
    }
    if (!WriteStringToFd(data, fd)) {
        return Error() << "Cannot send data to client";
    }
    struct timespec finish;
    if (clock_gettime(CLOCK_MONOTONIC, &finish) == -1) {
        return ErrnoError() << "failed to clock_gettime";
    }
    double elapsed_seconds = finish.tv_sec - start.tv_sec + (finish.tv_nsec - start.tv_nsec) / 1e9;
    LOG(INFO) << "Host:Finished sending data in " << elapsed_seconds << " seconds.";
    double send_rate = (double)num_bytes_to_send / kNumBytesPerMB / elapsed_seconds;
    return {send_rate};
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_android_microdroid_benchmark_IoVsockHostNative_measureSendRate(__unused JNIEnv *env,
                                                                        __unused jclass clazz,
                                                                        int fd,
                                                                        int num_bytes_to_send) {
    if (auto res = measure_send_rate(fd, num_bytes_to_send); res.ok()) {
        return res.value();
    } else {
        LOG(ERROR) << "Cannot send data from host to VM: " << res.error();
        abort();
    }
}
