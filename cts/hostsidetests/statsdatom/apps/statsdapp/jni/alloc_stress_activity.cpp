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
 *
 */

#include <android/log.h>
#include <jni.h>
#include <sys/mman.h>
#include <unistd.h>

#include <sstream>
#define LOG(...) __android_log_write(ANDROID_LOG_INFO, "ALLOC-STRESS", __VA_ARGS__)

size_t s = 4 * (1 << 20); // 4 MB
volatile void *gptr;
extern "C" JNIEXPORT void JNICALL
Java_com_android_server_cts_device_statsdatom_MemoryHogger_allocate(JNIEnv *, jobject /* this */) {
    volatile void *ptr;
    long long allocCount = 0;
    while (1) {
        ptr = mmap(NULL, s, PROT_READ | PROT_WRITE, MAP_ANONYMOUS | MAP_PRIVATE, 0, 0);
        if (ptr == MAP_FAILED) {
            // Likely exhausted address space
            break;
        }
        memset((void *)ptr, (int)allocCount >> 10, s);
        allocCount += s;
        std::stringstream ss;
        ss << "total alloc: " << allocCount / (1 << 20);
        LOG(ss.str().c_str());
        gptr = ptr;

        // If we are too aggressive allocating, we will end up triggering the
        // OOM reaper instead of LMKd.
        usleep(5000);
    }
}
