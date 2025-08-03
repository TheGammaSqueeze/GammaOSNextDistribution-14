//
// Copyright (C) 2014 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#include "update_engine/common/fake_prefs.h"

#include <algorithm>
#include <utility>

#include <gtest/gtest.h>

using std::string;
using std::vector;

using chromeos_update_engine::FakePrefs;

namespace {

void CheckNotNull(std::string_view key, void* ptr) {
  EXPECT_NE(nullptr, ptr) << "Called Get*() for key \"" << key
                          << "\" with a null parameter.";
}

}  // namespace

namespace chromeos_update_engine {

FakePrefs::~FakePrefs() {
  EXPECT_TRUE(observers_.empty());
}

// Compile-time type-dependent constants definitions.
template <>
FakePrefs::PrefType const FakePrefs::PrefConsts<string>::type =
    FakePrefs::PrefType::kString;
template <>
string FakePrefs::PrefValue::*const  // NOLINT(runtime/string), not static str.
    FakePrefs::PrefConsts<string>::member = &FakePrefs::PrefValue::as_str;

template <>
FakePrefs::PrefType const FakePrefs::PrefConsts<int64_t>::type =
    FakePrefs::PrefType::kInt64;
template <>
int64_t FakePrefs::PrefValue::*const FakePrefs::PrefConsts<int64_t>::member =
    &FakePrefs::PrefValue::as_int64;

template <>
FakePrefs::PrefType const FakePrefs::PrefConsts<bool>::type =
    FakePrefs::PrefType::kBool;
template <>
bool FakePrefs::PrefValue::*const FakePrefs::PrefConsts<bool>::member =
    &FakePrefs::PrefValue::as_bool;

bool FakePrefs::GetString(std::string_view key, string* value) const {
  return GetValue(key, value);
}

bool FakePrefs::SetString(std::string_view key, std::string_view value) {
  SetValue(key, std::string(value));
  return true;
}

bool FakePrefs::GetInt64(std::string_view key, int64_t* value) const {
  return GetValue(key, value);
}

bool FakePrefs::SetInt64(std::string_view key, const int64_t value) {
  SetValue(key, value);
  return true;
}

bool FakePrefs::GetBoolean(std::string_view key, bool* value) const {
  return GetValue(key, value);
}

bool FakePrefs::SetBoolean(std::string_view key, const bool value) {
  SetValue(key, value);
  return true;
}

bool FakePrefs::Exists(std::string_view key) const {
  return values_.find(key) != values_.end();
}

bool FakePrefs::Delete(std::string_view key) {
  if (values_.find(key) == values_.end())
    return false;
  values_.erase(std::string{key});
  const auto observers_for_key = observers_.find(key);
  if (observers_for_key != observers_.end()) {
    std::vector<ObserverInterface*> copy_observers(observers_for_key->second);
    for (ObserverInterface* observer : copy_observers)
      observer->OnPrefDeleted(key);
  }
  return true;
}

bool FakePrefs::Delete(std::string_view key, const vector<string>& nss) {
  bool success = Delete(key);
  for (const auto& ns : nss) {
    vector<string> ns_keys;
    success = GetSubKeys(ns, &ns_keys) && success;
    for (const auto& sub_key : ns_keys) {
      auto last_key_seperator = sub_key.find_last_of(kKeySeparator);
      if (last_key_seperator != string::npos &&
          key == sub_key.substr(last_key_seperator + 1)) {
        success = Delete(sub_key) && success;
      }
    }
  }
  return success;
}

bool FakePrefs::GetSubKeys(std::string_view ns, vector<string>* keys) const {
  for (const auto& pr : values_)
    if (pr.first.compare(0, ns.length(), ns) == 0)
      keys->push_back(pr.first);
  return true;
}

string FakePrefs::GetTypeName(PrefType type) {
  switch (type) {
    case PrefType::kString:
      return "string";
    case PrefType::kInt64:
      return "int64_t";
    case PrefType::kBool:
      return "bool";
  }
  return "Unknown";
}

void FakePrefs::CheckKeyType(std::string_view key, PrefType type) const {
  auto it = values_.find(key);
  EXPECT_TRUE(it == values_.end() || it->second.type == type)
      << "Key \"" << key << "\" if defined as " << GetTypeName(it->second.type)
      << " but is accessed as a " << GetTypeName(type);
}

template <typename T>
void FakePrefs::SetValue(std::string_view key, T value) {
  std::string str_key{key};
  CheckKeyType(key, PrefConsts<T>::type);
  values_[str_key].type = PrefConsts<T>::type;
  values_[str_key].value.*(PrefConsts<T>::member) = std::move(value);
  const auto observers_for_key = observers_.find(key);
  if (observers_for_key != observers_.end()) {
    std::vector<ObserverInterface*> copy_observers(observers_for_key->second);
    for (ObserverInterface* observer : copy_observers)
      observer->OnPrefSet(key);
  }
}

template <typename T>
bool FakePrefs::GetValue(std::string_view key, T* value) const {
  CheckKeyType(key, PrefConsts<T>::type);
  auto it = values_.find(key);
  if (it == values_.end())
    return false;
  CheckNotNull(key, value);
  *value = it->second.value.*(PrefConsts<T>::member);
  return true;
}

void FakePrefs::AddObserver(std::string_view key, ObserverInterface* observer) {
  observers_[string{key}].push_back(observer);
}

void FakePrefs::RemoveObserver(std::string_view key,
                               ObserverInterface* observer) {
  string str_key{key};
  std::vector<ObserverInterface*>& observers_for_key = observers_[str_key];
  auto observer_it =
      std::find(observers_for_key.begin(), observers_for_key.end(), observer);
  EXPECT_NE(observer_it, observers_for_key.end())
      << "Trying to remove an observer instance not watching the key " << key;
  if (observer_it != observers_for_key.end())
    observers_for_key.erase(observer_it);
  if (observers_for_key.empty())
    observers_.erase(str_key);
}

}  // namespace chromeos_update_engine
