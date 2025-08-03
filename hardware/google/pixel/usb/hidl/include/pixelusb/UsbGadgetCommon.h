/*
 * Copyright (C) 2018 The Android Open Source Project
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

#ifndef HARDWARE_GOOGLE_PIXEL_USB_USBGADGETCOMMON_H
#define HARDWARE_GOOGLE_PIXEL_USB_USBGADGETCOMMON_H

#include <android/hardware/usb/gadget/1.0/IUsbGadget.h>
#include <pixelusb/MonitorFfs.h>

namespace android {
namespace hardware {
namespace google {
namespace pixel {
namespace usb {

using ::android::hardware::usb::gadget::V1_0::Status;

//**************** Helper functions ************************//

// Sets the USB VID and PID.
Status setVidPid(const char *vid, const char *pid);
// Adds Adb to the usb configuration.
Status addAdb(MonitorFfs *monitorFfs, int *functionCount);
// Adds all applicable generic android usb functions other than ADB.
Status addGenericAndroidFunctions(MonitorFfs *monitorFfs, uint64_t functions, bool *ffsEnabled,
                                  int *functionCount);
// Pulls down USB gadget.
Status resetGadget();

}  // namespace usb
}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android
#endif
