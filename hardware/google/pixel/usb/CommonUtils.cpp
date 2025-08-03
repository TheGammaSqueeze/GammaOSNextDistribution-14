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

#define LOG_TAG "libpixelusb-common"

#include "include/pixelusb/CommonUtils.h"

#include <android-base/file.h>
#include <android-base/properties.h>
#include <sys/epoll.h>
#include <sys/eventfd.h>
#include <sys/inotify.h>
#include <utils/Log.h>

#include <chrono>
#include <memory>
#include <mutex>

namespace android {
namespace hardware {
namespace google {
namespace pixel {
namespace usb {

// Android metrics requires number of elements in any repeated field cannot exceed 127 elements
constexpr int kWestworldRepeatedFieldSizeLimit = 127;

using ::android::base::GetProperty;
using ::android::base::SetProperty;
using ::android::base::WriteStringToFile;
using ::std::chrono::microseconds;
using ::std::chrono::steady_clock;
using ::std::literals::chrono_literals::operator""ms;
using android::hardware::google::pixel::PixelAtoms::VendorUsbDataSessionEvent;
using android::hardware::google::pixel::PixelAtoms::
        VendorUsbDataSessionEvent_UsbDataRole_USB_ROLE_DEVICE;
using android::hardware::google::pixel::PixelAtoms::
        VendorUsbDataSessionEvent_UsbDataRole_USB_ROLE_HOST;
using android::hardware::google::pixel::PixelAtoms::VendorUsbDataSessionEvent_UsbDeviceState;
using android::hardware::google::pixel::PixelAtoms::
        VendorUsbDataSessionEvent_UsbDeviceState_USB_STATE_ADDRESSED;
using android::hardware::google::pixel::PixelAtoms::
        VendorUsbDataSessionEvent_UsbDeviceState_USB_STATE_ATTACHED;
using android::hardware::google::pixel::PixelAtoms::
        VendorUsbDataSessionEvent_UsbDeviceState_USB_STATE_CONFIGURED;
using android::hardware::google::pixel::PixelAtoms::
        VendorUsbDataSessionEvent_UsbDeviceState_USB_STATE_DEFAULT;
using android::hardware::google::pixel::PixelAtoms::
        VendorUsbDataSessionEvent_UsbDeviceState_USB_STATE_NOT_ATTACHED;
using android::hardware::google::pixel::PixelAtoms::
        VendorUsbDataSessionEvent_UsbDeviceState_USB_STATE_POWERED;
using android::hardware::google::pixel::PixelAtoms::
        VendorUsbDataSessionEvent_UsbDeviceState_USB_STATE_SUSPENDED;
using android::hardware::google::pixel::PixelAtoms::
        VendorUsbDataSessionEvent_UsbDeviceState_USB_STATE_UNKNOWN;

int addEpollFd(const base::unique_fd &epfd, const base::unique_fd &fd) {
    struct epoll_event event;
    int ret;

    event.data.fd = fd;
    event.events = EPOLLIN;

    ret = epoll_ctl(epfd, EPOLL_CTL_ADD, fd, &event);
    if (ret)
        ALOGE("epoll_ctl error %d", errno);

    return ret;
}

std::string getVendorFunctions() {
    if (GetProperty(kBuildType, "") == "user")
        return "user";

    std::string bootMode = GetProperty(PERSISTENT_BOOT_MODE, "");
    std::string persistVendorFunctions = GetProperty(kPersistentVendorConfig, "");
    std::string vendorFunctions = GetProperty(kVendorConfig, "");
    std::string ret = "";

    if (vendorFunctions != "") {
        ret = vendorFunctions;
    } else if (bootMode == "usbradio" || bootMode == "factory" || bootMode == "ffbm-00" ||
               bootMode == "ffbm-01" || bootMode == "usbuwb") {
        if (persistVendorFunctions != "")
            ret = persistVendorFunctions;
        else
            ret = "diag";
        // vendor.usb.config will reflect the current configured functions
        SetProperty(kVendorConfig, ret);
    }

    return ret;
}

int unlinkFunctions(const char *path) {
    DIR *config = opendir(path);
    struct dirent *function;
    char filepath[kMaxFilePathLength];
    int ret = 0;

    if (config == NULL)
        return -1;

    // d_type does not seems to be supported in /config
    // so filtering by name.
    while (((function = readdir(config)) != NULL)) {
        if ((strstr(function->d_name, FUNCTION_NAME) == NULL))
            continue;
        // build the path for each file in the folder.
        snprintf(filepath, kMaxFilePathLength, "%s/%s", path, function->d_name);
        ret = remove(filepath);
        if (ret) {
            ALOGE("Unable  remove file %s errno:%d", filepath, errno);
            break;
        }
    }

    closedir(config);
    return ret;
}

int linkFunction(const char *function, int index) {
    char functionPath[kMaxFilePathLength];
    char link[kMaxFilePathLength];

    snprintf(functionPath, kMaxFilePathLength, "%s%s", FUNCTIONS_PATH, function);
    snprintf(link, kMaxFilePathLength, "%s%d", FUNCTION_PATH, index);
    if (symlink(functionPath, link)) {
        ALOGE("Cannot create symlink %s -> %s errno:%d", link, functionPath, errno);
        return -1;
    }
    return 0;
}

bool setVidPidCommon(const char *vid, const char *pid) {
    if (!WriteStringToFile(vid, VENDOR_ID_PATH))
        return false;

    if (!WriteStringToFile(pid, PRODUCT_ID_PATH))
        return false;

    return true;
}

bool resetGadgetCommon() {
    ALOGI("setCurrentUsbFunctions None");

    if (!WriteStringToFile("none", PULLUP_PATH))
        ALOGI("Gadget cannot be pulled down");

    if (!WriteStringToFile("0", DEVICE_CLASS_PATH))
        return false;

    if (!WriteStringToFile("0", DEVICE_SUB_CLASS_PATH))
        return false;

    if (!WriteStringToFile("0", DEVICE_PROTOCOL_PATH))
        return false;

    if (!WriteStringToFile("0", DESC_USE_PATH))
        return false;

    if (unlinkFunctions(CONFIG_PATH))
        return false;

    return true;
}

static VendorUsbDataSessionEvent_UsbDeviceState stringToUsbDeviceStateProto(
        const std::string &state) {
    if (state == "not attached\n") {
        return VendorUsbDataSessionEvent_UsbDeviceState_USB_STATE_NOT_ATTACHED;
    } else if (state == "attached\n") {
        return VendorUsbDataSessionEvent_UsbDeviceState_USB_STATE_ATTACHED;
    } else if (state == "powered\n") {
        return VendorUsbDataSessionEvent_UsbDeviceState_USB_STATE_POWERED;
    } else if (state == "default\n") {
        return VendorUsbDataSessionEvent_UsbDeviceState_USB_STATE_DEFAULT;
    } else if (state == "addressed\n") {
        return VendorUsbDataSessionEvent_UsbDeviceState_USB_STATE_ADDRESSED;
    } else if (state == "configured\n") {
        return VendorUsbDataSessionEvent_UsbDeviceState_USB_STATE_CONFIGURED;
    } else if (state == "suspended\n") {
        return VendorUsbDataSessionEvent_UsbDeviceState_USB_STATE_SUSPENDED;
    } else {
        return VendorUsbDataSessionEvent_UsbDeviceState_USB_STATE_UNKNOWN;
    }
}

void BuildVendorUsbDataSessionEvent(bool is_host, boot_clock::time_point currentTime,
                                    boot_clock::time_point startTime,
                                    std::vector<std::string> *states,
                                    std::vector<boot_clock::time_point> *timestamps,
                                    VendorUsbDataSessionEvent *event) {
    if (is_host) {
        event->set_usb_role(VendorUsbDataSessionEvent_UsbDataRole_USB_ROLE_HOST);
    } else {
        event->set_usb_role(VendorUsbDataSessionEvent_UsbDataRole_USB_ROLE_DEVICE);
    }

    for (int i = 0; i < states->size() && i < kWestworldRepeatedFieldSizeLimit; i++) {
        event->add_usb_states(stringToUsbDeviceStateProto(states->at(i)));
    }

    for (int i = 0; i < timestamps->size() && i < kWestworldRepeatedFieldSizeLimit; i++) {
        event->add_elapsed_time_ms(
                std::chrono::duration_cast<std::chrono::milliseconds>(timestamps->at(i) - startTime)
                        .count());
    }

    event->set_duration_ms(
            std::chrono::duration_cast<std::chrono::milliseconds>(currentTime - startTime).count());
}

}  // namespace usb
}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android
