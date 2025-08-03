/*
 * Copyright (C) 2021 The Android Open Source Project
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

#pragma once

#include <vintf/Version.h>

namespace android::vintf {

std::string toXml(const Version& o, SerializeFlags::Type flags = SerializeFlags::EVERYTHING);
std::string toXml(const SepolicyVersion& o,
                  SerializeFlags::Type flags = SerializeFlags::EVERYTHING);
std::string toXml(const KernelConfigTypedValue& o,
                  SerializeFlags::Type flags = SerializeFlags::EVERYTHING);
std::string toXml(const ManifestHal& o, SerializeFlags::Type flags = SerializeFlags::EVERYTHING);
std::string toXml(const MatrixHal& o, SerializeFlags::Type flags = SerializeFlags::EVERYTHING);

[[nodiscard]] bool fromXml(Version* o, const std::string& xml, std::string* error = nullptr);
[[nodiscard]] bool fromXml(SepolicyVersion* o, const std::string& xml,
                           std::string* error = nullptr);
[[nodiscard]] bool fromXml(KernelConfigTypedValue* o, const std::string& xml,
                           std::string* error = nullptr);
[[nodiscard]] bool fromXml(MatrixHal* o, const std::string& xml, std::string* error = nullptr);
[[nodiscard]] bool fromXml(ManifestHal* o, const std::string& xml, std::string* error = nullptr);

}  // namespace android::vintf
