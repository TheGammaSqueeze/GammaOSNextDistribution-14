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

#define LOG_TAG "libpixelusb-i2chelper"

#include "include/pixelusb/I2cHelper.h"

#include <dirent.h>
#include <utils/Log.h>

namespace android {
namespace hardware {
namespace google {
namespace pixel {
namespace usb {

// getI2cBusNumberString: Return the I2C bus number as the string type
//
// The bus number can be extracted from the sub-directory under the hsi2c sysfs
// device directory (e.g. /sys/devices/platform/10d60000.hsi2c/) and the pattern
// of the sub-directory is /^i2c-\d+$/ where \d+ is the bus number.
static string getI2cBusNumberString(const string hsi2cPath) {
    DIR *dp = opendir(hsi2cPath.c_str());

    if (dp != NULL) {
        struct dirent *ep;

        while ((ep = readdir(dp))) {
            if (ep->d_type == DT_DIR) {
                // Supposed that there is only one sub dir in the pattern "i2c-"
                if (string::npos != string(ep->d_name).find("i2c-")) {
                    std::strtok(ep->d_name, "-");
                    string busNumber = std::strtok(NULL, "-");
                    closedir(dp);
                    return busNumber;
                }
            }
        }
        closedir(dp);
        ALOGE("Failed to find the i2c sub dir under %s", hsi2cPath.c_str());
        return string("");
    }

    ALOGE("Failed to open %s", hsi2cPath.c_str());
    return string("");
}

// getI2cClientPath: Return the full path of the I2C client directory
//
// There are two forms of the directory path: in client ID and in I2C device name
// For example:
//   client ID: /sys/devices/platform/10d60000.hsi2c/i2c-7/7-0025/
//   device name: /sys/devices/platform/10d60000.hsi2c/i2c-7/i2c-max77759tcpc/
//
// The bus number and the client directory name differs across kernel versions and
// build targets. Search the bus number first to locate the first level of the sub
// directory, and then search the I2C device name under it.
//
// Append the I2c device name to the full path if found, otherwise, append "bus
// number" + "-" + client ID. Note that the client ID must be a 4-digit number
// with 0 stuffed in the type of string.
string getI2cClientPath(const string hsi2cPath, const string devName, const string clientId) {
    DIR *dp;
    string strBusNumber, i2cPathPartial, i2cClientPath;

    strBusNumber = getI2cBusNumberString(hsi2cPath);
    if (strBusNumber.empty()) {
        return string("");
    }

    i2cPathPartial = hsi2cPath + "/i2c-" + strBusNumber;
    dp = opendir(i2cPathPartial.c_str());
    if (dp != NULL) {
        struct dirent *ep;
        string i2cClientDevice = strBusNumber + "-" + clientId;

        while ((ep = readdir(dp))) {
            if (ep->d_type == DT_DIR) {
                if (string::npos != string(ep->d_name).find(devName)) {
                    closedir(dp);
                    return string(i2cPathPartial + "/" + devName + "/");
                }
                if (string::npos != string(ep->d_name).find(i2cClientDevice)) {
                    closedir(dp);
                    return string(i2cPathPartial + "/" + i2cClientDevice + "/");
                }
            }
        }
        closedir(dp);
    }

    ALOGE("Failed to open %s", i2cPathPartial.c_str());
    return string("");
}

}  // namespace usb
}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android
