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

#ifndef HARDWARE_GOOGLE_PIXEL_USB_UTILSCOMMON_H_
#define HARDWARE_GOOGLE_PIXEL_USB_UTILSCOMMON_H_

#include <android-base/chrono_utils.h>
#include <android-base/unique_fd.h>
#include <hardware/google/pixel/pixelstats/pixelatoms.pb.h>

#include <string>

namespace android {
namespace hardware {
namespace google {
namespace pixel {
namespace usb {

constexpr int kBufferSize = 512;
constexpr int kMaxFilePathLength = 256;
constexpr int kEpollEvents = 10;
constexpr bool kDebug = false;
constexpr int kDisconnectWaitUs = 100000;
constexpr int kPullUpDelay = 500000;
constexpr int kShutdownMonitor = 100;

constexpr char kBuildType[] = "ro.build.type";
constexpr char kPersistentVendorConfig[] = "persist.vendor.usb.usbradio.config";
constexpr char kVendorConfig[] = "vendor.usb.config";
constexpr char kVendorRndisConfig[] = "vendor.usb.rndis.config";
constexpr char kUvcEnabled[] = "ro.usb.uvc.enabled";

#define GADGET_PATH "/config/usb_gadget/g1/"
#define PULLUP_PATH GADGET_PATH "UDC"
#define PERSISTENT_BOOT_MODE "ro.bootmode"
#define VENDOR_ID_PATH GADGET_PATH "idVendor"
#define PRODUCT_ID_PATH GADGET_PATH "idProduct"
#define DEVICE_CLASS_PATH GADGET_PATH "bDeviceClass"
#define DEVICE_SUB_CLASS_PATH GADGET_PATH "bDeviceSubClass"
#define DEVICE_PROTOCOL_PATH GADGET_PATH "bDeviceProtocol"
#define DESC_USE_PATH GADGET_PATH "os_desc/use"
#define OS_DESC_PATH GADGET_PATH "os_desc/b.1"
#define CONFIG_PATH GADGET_PATH "configs/b.1/"
#define FUNCTIONS_PATH GADGET_PATH "functions/"
#define FUNCTION_NAME "function"
#define FUNCTION_PATH CONFIG_PATH FUNCTION_NAME
#define RNDIS_PATH FUNCTIONS_PATH "gsi.rndis"

using ::android::base::boot_clock;
using android::hardware::google::pixel::PixelAtoms::VendorUsbDataSessionEvent;

// Adds the given fd to the epollfd(epfd).
int addEpollFd(const ::android::base::unique_fd &epfd, const ::android::base::unique_fd &fd);
// Extracts vendor functions from the vendor init properties.
std::string getVendorFunctions();
// Removes all the usb functions link in the specified path.
int unlinkFunctions(const char *path);
// Creates a configfs link for the function.
int linkFunction(const char *function, int index);
// Sets the USB VID and PID. Returns true on success, false on failure
bool setVidPidCommon(const char *vid, const char *pid);
// Pulls down USB gadget. Returns true on success, false on failure
bool resetGadgetCommon();
void BuildVendorUsbDataSessionEvent(bool is_host, boot_clock::time_point currentTime,
                                    boot_clock::time_point startTime,
                                    std::vector<std::string> *states,
                                    std::vector<boot_clock::time_point> *timestamps,
                                    VendorUsbDataSessionEvent *event);

}  // namespace usb
}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android

#endif  // HARDWARE_GOOGLE_PIXEL_USB_UTILSCOMMON_H_
