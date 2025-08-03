/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include <android/crash_detail.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <string>

inline crash_detail_t* _Nullable android_crash_detail_register_strs(const char* _Nonnull name,
                                                                    const char* _Nonnull data) {
    return android_crash_detail_register(name, strlen(name), data, strlen(data));
}

int main(int argc, char** argv) {
    if (argc < 2) {
        return 1;
    }
    if (strcmp(argv[1], "crash_without_crash_detail") == 0) {
        abort();
    }
    if (strcmp(argv[1], "crash_with_single_crash_detail") == 0) {
        android_crash_detail_register_strs("crash_detail_name", "crash_detail_data");
        abort();
    }
    if (strcmp(argv[1], "crash_with_multiple_crash_details") == 0) {
        android_crash_detail_register_strs("crash_detail_name1", "crash_detail_data1");
        android_crash_detail_register_strs("crash_detail_name2", "crash_detail_data2");
        abort();
    }
    if (strcmp(argv[1], "crash_with_unregistered_crash_details") == 0) {
        android_crash_detail_register_strs("crash_detail_name1", "crash_detail_data1");
        android_crash_detail_unregister(
                android_crash_detail_register_strs("crash_detail_name2", "crash_detail_data2"));
        abort();
    }
    if (strcmp(argv[1], "crash_with_binary_crash_detail") == 0) {
        android_crash_detail_register("\254\0", 2, "\255\0", 2);
        abort();
    }
    if (strcmp(argv[1], "crash_with_single_crash_detail_many_used") == 0) {
        for (int i = 0; i < 1000; ++i) {
            std::string name = "CRASH_DETAIL_NAME" + std::to_string(i);
            std::string value = "CRASH_DETAIL_VALUE" + std::to_string(i);
            auto* h = android_crash_detail_register_strs(name.data(), value.data());
            android_crash_detail_unregister(h);
        }

        android_crash_detail_register_strs("crash_detail_name", "crash_detail_data");
        abort();
    }
    if (strcmp(argv[1], "crash_with_changing_crash_detail") == 0) {
        char name[] = "crash_detail_name";
        char data[] = "crash_detail_data";
        android_crash_detail_register_strs(name, data);
        name[0] = 'C';
        data[0] = 'C';
        abort();
    }
    return 0;
}
