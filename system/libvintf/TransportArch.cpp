/*
 * Copyright (C) 2017 The Android Open Source Project
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


#include "TransportArch.h"

#include "parse_string.h"

namespace android {
namespace vintf {

bool TransportArch::empty() const {
    return transport == Transport::EMPTY && arch == Arch::ARCH_EMPTY;
}

bool TransportArch::isValid(std::string* error) const {
    if (error) {
        error->clear();
    }

    switch (transport) {
        case Transport::EMPTY: // fallthrough
        case Transport::HWBINDER:
            if (arch == Arch::ARCH_EMPTY && !ip.has_value() && !port.has_value()) {
                return true;
            }
            if (error) {
                *error += "Transport " + to_string(transport) +
                          " requires empty ip and port attributes as well as empty arch attribute";
            }
            return false;

        case Transport::PASSTHROUGH:
            if (arch != Arch::ARCH_EMPTY && !ip.has_value() && !port.has_value()) {
                return true;
            }
            if (error) {
                *error += "Passthrough HALs requires arch attribute and no ip or port attributes";
            }
            return false;
        case Transport::INET:
            if (arch == Arch::ARCH_EMPTY && ip.has_value() && port.has_value()) {
                return true;
            }
            if (error) {
                *error += "Transport " + to_string(transport) +
                          " requires ip and port attributes as well as empty arch attribute";
            }
            return false;
    }
}

} // namespace vintf
} // namespace android
