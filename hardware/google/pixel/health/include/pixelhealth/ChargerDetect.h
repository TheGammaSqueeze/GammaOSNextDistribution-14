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

#ifndef HARDWARE_GOOGLE_PIXEL_HEALTH_CHARGER_DETECT_H
#define HARDWARE_GOOGLE_PIXEL_HEALTH_CHARGER_DETECT_H

#include <aidl/android/hardware/health/HealthInfo.h>
#include <android-base/strings.h>
#include <healthd/BatteryMonitor.h>

using android::BatteryMonitor;

namespace hardware {
namespace google {
namespace pixel {
namespace health {

class ChargerDetect {
  public:
    // Deprecated. Use onlineUpdate(HealthInfo*)
    static void onlineUpdate(struct android::BatteryProperties *props);
    static void onlineUpdate(aidl::android::hardware::health::HealthInfo *health_info);
    static void populateTcpmPsyName(std::string *tcpmPsyName);

  private:
    static int getPsyUsbType(const std::string &path, std::string *type);
    static int readFromFile(const std::string &path, std::string *buf);
    static int getIntField(const std::string &path);
};

}  // namespace health
}  // namespace pixel
}  // namespace google
}  // namespace hardware

#endif
