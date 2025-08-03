/*
 * Copyright (C) 2022 The LineageOS Project
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

#define LOG_TAG "nqnfcinfo"

#include <errno.h>
#include <fcntl.h>

#include <fmt/format.h>

#include <android-base/logging.h>
#include <android-base/properties.h>

#include <linux/nfc/nfcinfo.h>

using ::android::base::SetProperty;

#define NQ_NCI_PATH "/dev/nq-nci"

int main() {
    // Open our fd
    int fd = open(NQ_NCI_PATH, O_RDONLY);

    // Make sure we could get the fd
    if (fd < 0) {
        LOG(ERROR) << "Could not open " << NQ_NCI_PATH << ": " << strerror(errno);
        return fd;
    }

    // NXP's kernel driver returns a struct that is union'ed with an integer
    // as the return value of the IOCTL call here. Presumably this is to avoid
    // dealing with copying memory to/from userspace, but it's a little silly
    // Since this is an unsigned type, I don't know how to check for errors...
    nqx_uinfo info{};
    info.i = ioctl(fd, NFCC_GET_INFO, 0);

    // Close fd
    close(fd);

    // Set properties based on IOCTL return
    SetProperty("vendor.qti.nfc.chipid", fmt::format("{:#02x}", info.info.chip_type));
    SetProperty("vendor.qti.nfc.fwver", fmt::format("{:02x}.{:02x}.{:02x}", info.info.rom_version,
                                                    info.info.fw_major, info.info.fw_minor));

    return 0;
}
