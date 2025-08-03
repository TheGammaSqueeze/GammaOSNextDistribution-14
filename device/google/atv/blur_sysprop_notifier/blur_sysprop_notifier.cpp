/*
 * Copyright 2021 The Android Open Source Project
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

#include <binder/Parcel.h>
#include <binder/ProcessState.h>
#include <binder/IServiceManager.h>
#include <binder/TextOutput.h>
#include <cutils/ashmem.h>

#include <getopt.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

using namespace android;

/**
 * This is a small program designed to trigger notifySyspropsChanged in the system
 * server. This exists in order to fix an issue with missing callbacks for unreadable
 * GPU buffers on TV devices and should be removed as soon as possible.
 */
int main() {
    sp<IServiceManager> sm = defaultServiceManager();

    if (sm == nullptr) {
        aerr << "service: Unable to get default service manager!" << endl;
        return 20;
    }

    sp<IBinder> service = sm->checkService(String16("activity"));
    if (service != nullptr) {
        Parcel data;
        service->transact(IBinder::SYSPROPS_TRANSACTION, data, NULL, 0);
    } else {
        aout << "ActivityManagerService not found" << endl;
        return 10;
    }
    return 0;
}
