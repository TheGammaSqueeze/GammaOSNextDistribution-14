/*
 * Copyright (C) 2020 The Android Open Source Project
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

/* this program is used to read a set of system properties and their values
 * from the emulator program and set them in the currently-running emulated
 * system. It does so by connecting to the 'boot-properties' qemud service.
 *
 * This file parses the sys/class/virtio-ports/<id>/name
 * and set up vendor.qemu.vport.modem=/dev/<id> so that reference-ril
 * can open it later
 */

#include <fstream>
#include <string>
#include <android-base/strings.h>
#include <log/log.h>
#include <cutils/properties.h>

#include <dirent.h>
#include <error.h>
#include <string.h>
#include <unistd.h>

static void set_port_prop(const char* filename, const char* portname) {
    std::ifstream myfile(filename);
    if (myfile.is_open()) {
        const std::string portdev = std::string{"/dev/"} + portname;

        for (std::string line; std::getline(myfile, line); ) {
            std::string serialname = android::base::Trim(line);
            if (serialname.empty()) {
                continue;
            }
            serialname = std::string("vendor.qemu.vport.") + serialname;
            if(property_set(serialname.c_str(), portdev.c_str()) < 0) {
                ALOGW("could not set property '%s' to '%s'", serialname.c_str(),
                      portdev.c_str());
            } else {
                ALOGI("successfully set property '%s' to '%s'", serialname.c_str(), portdev.c_str());
            }
        }
        myfile.close();
    } else {
        ALOGW("could not open '%s'", filename);
    }
}

static void close_dir(DIR *dp) { closedir(dp); }

static void read_virio_ports_dir(const char *cpath)
{
    std::unique_ptr<DIR, decltype(&close_dir)> mydp(opendir(cpath),
                                                    &close_dir);

    if (!mydp) {
        ALOGW("cannot open dir %s; %s\n", cpath, strerror(errno));
        return;
    }

    const std::string path(cpath);

    struct dirent *files;
    while ((files = readdir(mydp.get())) != NULL) {
        if (strcmp(files->d_name, ".") == 0 ||
            strcmp(files->d_name, "..") == 0) {
            continue;
        }

        std::string filename = path + std::string("/") + std::string(files->d_name) + "/name";
        set_port_prop(filename.c_str(), files->d_name);
    }
}

void parse_virtio_serial() {
    read_virio_ports_dir("/sys/class/virtio-ports");
}



