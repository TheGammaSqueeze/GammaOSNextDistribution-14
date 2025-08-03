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

#ifndef HARDWARE_GOOGLE_PIXEL_USB_I2CHELPER_H_
#define HARDWARE_GOOGLE_PIXEL_USB_I2CHELPER_H_

#include <string>

using ::std::string;

namespace android {
namespace hardware {
namespace google {
namespace pixel {
namespace usb {

// Search the path of the i2c client
string getI2cClientPath(const string hsi2cPath, const string devName, const string clientId);

}  // namespace usb
}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android

#endif  // HARDWARE_GOOGLE_PIXEL_USB_I2CHELPER_H_
