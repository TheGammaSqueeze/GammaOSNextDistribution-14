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

#include "KernelConfigs.h"

#include <android-base/logging.h>

#include <map>
#include <string>

#include <zlib.h>
#include "vintf/KernelConfigParser.h"

#define BUFFER_SIZE sysconf(_SC_PAGESIZE)

namespace android {
namespace kernelconfigs {

status_t LoadKernelConfigs(std::map<std::string, std::string>* configs) {
    vintf::KernelConfigParser parser;
    gzFile f = gzopen("/proc/config.gz", "rb");
    if (f == NULL) {
        LOG(ERROR) << "Could not open /proc/config.gz: " << errno;
        return -errno;
    }

    char buf[BUFFER_SIZE];
    int len;
    while ((len = gzread(f, buf, sizeof buf)) > 0) {
        parser.process(buf, len);
    }
    status_t err = OK;
    if (len < 0) {
        int errnum;
        const char* errmsg = gzerror(f, &errnum);
        LOG(ERROR) << "Could not read /proc/config.gz: " << errmsg;
        err = (errnum == Z_ERRNO ? -errno : errnum);
    }
    parser.finish();
    gzclose(f);
    *configs = std::move(parser.configs());
    return err;
}

}  // namespace kernelconfigs
}  // namespace android
