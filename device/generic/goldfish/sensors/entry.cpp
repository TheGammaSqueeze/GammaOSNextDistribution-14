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

#include <android-base/unique_fd.h>
#include <qemud.h>
#include <multihal_sensors.h>

using ::android::base::unique_fd;
using ::android::hardware::sensors::V2_1::implementation::ISensorsSubHal;

namespace {

class QemudSensorsTransport : public goldfish::SensorsTransport {
 public:
    QemudSensorsTransport()
        : m_qemuSensorsFd(qemud_channel_open("sensors")) {}

    int Send(const void* msg, int size) override {
        return qemud_channel_send(m_qemuSensorsFd.get(), msg, size);
    }

    int Receive(void* msg, int maxsize) override {
        return qemud_channel_recv(m_qemuSensorsFd.get(), msg, maxsize);
    }

    bool Ok() const override {
        return m_qemuSensorsFd.ok();
    }

    int Fd() const override {
        return m_qemuSensorsFd.get();
    }

    const char* Name() const override {
        return "qemud_channel";
    }

 private:
    const unique_fd m_qemuSensorsFd;
};

goldfish::MultihalSensors impl([](){ return std::make_unique<QemudSensorsTransport>(); });

} // namespace

extern "C" ISensorsSubHal* sensorsHalGetSubHal_2_1(uint32_t* version) {
    *version = SUB_HAL_2_1_VERSION;
    return &impl;
}
