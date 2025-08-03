/*
 * Copyright 2021 The Android Open Source Project
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

#include <string>

#include "main/shim/config.h"

bool bluetooth::shim::BtifConfigInterface::HasSection(
    const std::string& /* section */) {
  return false;
}
bool bluetooth::shim::BtifConfigInterface::HasProperty(
    const std::string& /* section */, const std::string& /* property */) {
  return false;
}
bool bluetooth::shim::BtifConfigInterface::GetInt(
    const std::string& /* section */, const std::string& /* key */,
    int* /* value */) {
  return false;
}
bool bluetooth::shim::BtifConfigInterface::SetInt(
    const std::string& /* section */, const std::string& /* key */,
    int /* value */) {
  return false;
}
bool bluetooth::shim::BtifConfigInterface::GetUint64(
    const std::string& /* section */, const std::string& /* key */,
    uint64_t* /* value */) {
  return false;
}
bool bluetooth::shim::BtifConfigInterface::SetUint64(
    const std::string& /* section */, const std::string& /* key */,
    uint64_t /* value */) {
  return false;
}
bool bluetooth::shim::BtifConfigInterface::GetStr(
    const std::string& /* section */, const std::string& /* key */,
    char* /* value */, int* /* size_bytes */) {
  return false;
}
std::optional<std::string> bluetooth::shim::BtifConfigInterface::GetStr(
    const std::string& /* section */, const std::string& /* key */) {
  return std::string();
}
bool bluetooth::shim::BtifConfigInterface::SetStr(
    const std::string& /* section */, const std::string& /* key */,
    const std::string& /* value */) {
  return false;
}
bool bluetooth::shim::BtifConfigInterface::GetBin(
    const std::string& /* section */, const std::string& /* key */,
    uint8_t* /* value */, size_t* /* length */) {
  return false;
}
size_t bluetooth::shim::BtifConfigInterface::GetBinLength(
    const std::string& /* section */, const std::string& /* key */) {
  return 0;
}
bool bluetooth::shim::BtifConfigInterface::SetBin(
    const std::string& /* section */, const std::string& /* key */,
    const uint8_t* /* value */, size_t /* length */) {
  return false;
}
bool bluetooth::shim::BtifConfigInterface::RemoveProperty(
    const std::string& /* section */, const std::string& /* key */) {
  return false;
}
void bluetooth::shim::BtifConfigInterface::RemoveSection(
    const std::string& /* section */){};
std::vector<std::string>
bluetooth::shim::BtifConfigInterface::GetPersistentDevices() {
  return std::vector<std::string>();
}
void bluetooth::shim::BtifConfigInterface::
    ConvertEncryptOrDecryptKeyIfNeeded(){};
void bluetooth::shim::BtifConfigInterface::Clear(){};
