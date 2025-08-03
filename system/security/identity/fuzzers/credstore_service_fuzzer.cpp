/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <android-base/logging.h>
#include <fuzzbinder/libbinder_driver.h>
#include <sys/stat.h>

#include "CredentialStoreFactory.h"

using android::security::identity::CredentialStoreFactory;
using namespace android;

void clearDirectory(const char* dirpath, bool recursive) {
    DIR* dir = opendir(dirpath);
    CHECK(dir != nullptr);
    dirent* e;
    struct stat s;
    while ((e = readdir(dir)) != nullptr) {
        if ((strcmp(e->d_name, ".") == 0) || (strcmp(e->d_name, "..") == 0)) {
            continue;
        }
        std::string filename(dirpath);
        filename.push_back('/');
        filename.append(e->d_name);
        int stat_result = lstat(filename.c_str(), &s);
        CHECK_EQ(0, stat_result) << "unable to stat " << filename;
        if (S_ISDIR(s.st_mode)) {
            if (recursive) {
                clearDirectory(filename.c_str(), true);
                int rmdir_result = rmdir(filename.c_str());
                CHECK_EQ(0, rmdir_result) << filename;
            }
        } else {
            int unlink_result = unlink(filename.c_str());
            CHECK_EQ(0, unlink_result) << filename;
        }
    }
    closedir(dir);
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    std::string dataDir = "/data/cred_store_fuzzer";
    mkdir(dataDir.c_str(), 0700);
    sp<CredentialStoreFactory> service = sp<CredentialStoreFactory>::make(dataDir);
    fuzzService(service, FuzzedDataProvider(data, size));
    clearDirectory(dataDir.c_str(), true);
    rmdir(dataDir.c_str());
    return 0;
}
