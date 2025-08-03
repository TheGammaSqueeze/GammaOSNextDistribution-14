/*
 * Copyright (C) 2009 The Android Open Source Project
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
 * This program should be run as root and called from
 * /system/etc/init.ranchu.rc exclusively.
 */

#include <string_view>
#include <android-base/unique_fd.h>
#include <cutils/properties.h>
#include <unistd.h>
#include <qemu_pipe_bp.h>
#include <qemud.h>
#include <string.h>
#include <stdio.h>
#include <debug.h>

namespace {
constexpr char kBootPropertiesService[] = "boot-properties";
constexpr char kHeartbeatService[] = "QemuMiscPipe";

// qemu-props will not set these properties.
const char* const k_properties_to_ignore[] = {
    "dalvik.vm.heapsize",
    "ro.opengles.version",
    "qemu.adb.secure",
    nullptr,
};

// These properties will not be prefixed with "vendor.".
const char* const k_system_properties[] = {
    "qemu.sf.lcd_density",
    "qemu.hw.mainkeys",
    nullptr,
};

bool check_if_property_in_list(const char* prop_name, const char* const* prop_list) {
    for (; *prop_list; ++prop_list) {
        if (!strcmp(prop_name, *prop_list)) {
            return true;
        }
    }
    return false;
}

// We don't want to rename properties which already have the prefix
// or the system properties.
bool need_prepend_prefix(const char* prop, const std::string_view prefix) {
    return strncmp(prefix.data(), prop, prefix.size()) &&
           !check_if_property_in_list(prop, k_system_properties);
}

// Deprecated, consider replacing with androidboot
int setBootProperties() {
    using android::base::unique_fd;
    unique_fd qemud;

    for (int tries = 5; tries > 0; --tries) {
        qemud = unique_fd(qemud_channel_open(kBootPropertiesService));
        if (qemud.ok()) {
            break;
        } else if (tries > 1) {
            sleep(1);
        } else {
            return FAILURE(1);
        }
    }

    if (qemud_channel_send(qemud.get(), "list", -1) < 0) {
        return FAILURE(1);
    }

    while (true) {
        char temp[PROPERTY_KEY_MAX + PROPERTY_VALUE_MAX + 2];
        const int len = qemud_channel_recv(qemud.get(), temp, sizeof(temp) - 1);

        /* lone NUL-byte signals end of properties */
        if (len < 0 || len > (sizeof(temp) - 1) || !temp[0]) {
            break;
        }

        temp[len] = '\0';
        char* prop_value = strchr(temp, '=');
        if (!prop_value) {
            continue;
        }

        *prop_value = 0;
        ++prop_value;

        if (check_if_property_in_list(temp, k_properties_to_ignore)) {
            continue;
        }

        char renamed_property[sizeof(temp)];
        const char* final_prop_name = nullptr;

        using namespace std::literals;
        static constexpr std::string_view k_vendor_prefix = "vendor."sv;
        if (need_prepend_prefix(temp, k_vendor_prefix)) {
            snprintf(renamed_property, sizeof(renamed_property), "%.*s%s",
                     int(k_vendor_prefix.size()), k_vendor_prefix.data(), temp);

            final_prop_name = renamed_property;
        } else {
            final_prop_name = temp;
        }

        if (property_set(final_prop_name, prop_value) < 0) {
            ALOGW("could not set property '%s' to '%s'", final_prop_name, prop_value);
        } else {
            ALOGI("successfully set property '%s' to '%s'", final_prop_name, prop_value);
        }
    }

    return 0;
}

}  // namespace

static int s_QemuMiscPipe = -1;
static void sendHeartBeat();
static void sendMessage(const char* mesg);
static void closeMiscPipe();
extern void parse_virtio_serial();

int main(const int argc, const char* argv[])
{
    if ((argc == 2) && !strcmp(argv[1], "bootcomplete")) {
        sendMessage("bootcomplete");
        return 0;
    }

    int r = setBootProperties();
    if (r) {
        return r;
    }

    parse_virtio_serial();

    sendHeartBeat();
    while (s_QemuMiscPipe >= 0) {
        usleep(5000000); /* 5 seconds */
        sendHeartBeat();
        char temp[PROPERTY_VALUE_MAX];
        property_get("vendor.qemu.dev.bootcomplete", temp, "");
        if (strcmp(temp, "1") == 0) {
            break;
        }
    }

    while (s_QemuMiscPipe >= 0) {
        usleep(30 * 1000000);
        sendHeartBeat();
    }

    closeMiscPipe();
    return 0;
}

void sendHeartBeat() {
    sendMessage("heartbeat");
}

void sendMessage(const char* mesg) {
   if (s_QemuMiscPipe < 0) {
        s_QemuMiscPipe = qemu_pipe_open_ns(NULL, kHeartbeatService, O_RDWR);
        if (s_QemuMiscPipe < 0) {
            ALOGE("failed to open %s", kHeartbeatService);
            return;
        }
    }

    int32_t cmd_len = strlen(mesg) + 1; //including trailing '\0'
    qemu_pipe_write_fully(s_QemuMiscPipe, &cmd_len, sizeof(cmd_len));
    qemu_pipe_write_fully(s_QemuMiscPipe, mesg, cmd_len);

    int r = qemu_pipe_read_fully(s_QemuMiscPipe, &cmd_len, sizeof(cmd_len));
    if (r || (cmd_len < 0)) {
        closeMiscPipe();
        return;
    }

    while (cmd_len > 0) {
        char buf[64];
        const size_t chunk = std::min<size_t>(cmd_len, sizeof(buf));
        r = qemu_pipe_read_fully(s_QemuMiscPipe, buf, chunk);
        if (r) {
            closeMiscPipe();
            return;
        } else {
            cmd_len -= chunk;
        }
    }
}

void closeMiscPipe() {
    if (s_QemuMiscPipe >= 0) {
        close(s_QemuMiscPipe);
        s_QemuMiscPipe = -1;
    }
}
