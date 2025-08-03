//
// Copyright (C) 2012 The Android Open Source Project
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

#include "update_engine/common/prefs.h"

#include <algorithm>
#include <filesystem>
#include <unistd.h>

#include <android-base/file.h>
#include <base/files/file_enumerator.h>
#include <base/files/file_util.h>
#include <base/logging.h>
#include <base/strings/string_number_conversions.h>
#include <base/strings/string_split.h>
#include <base/strings/string_util.h>

#include "update_engine/common/utils.h"

using std::string;
using std::vector;

namespace chromeos_update_engine {

namespace {

void DeleteEmptyDirectories(const base::FilePath& path) {
  base::FileEnumerator path_enum(
      path, false /* recursive */, base::FileEnumerator::DIRECTORIES);
  for (base::FilePath dir_path = path_enum.Next(); !dir_path.empty();
       dir_path = path_enum.Next()) {
    DeleteEmptyDirectories(dir_path);
    if (base::IsDirectoryEmpty(dir_path))
#if BASE_VER < 800000
      base::DeleteFile(dir_path, false);
#else
      base::DeleteFile(dir_path);
#endif
  }
}

}  // namespace

bool PrefsBase::GetString(const std::string_view key, string* value) const {
  return storage_->GetKey(key, value);
}

bool PrefsBase::SetString(std::string_view key, std::string_view value) {
  TEST_AND_RETURN_FALSE(storage_->SetKey(key, value));
  const auto observers_for_key = observers_.find(key);
  if (observers_for_key != observers_.end()) {
    std::vector<ObserverInterface*> copy_observers(observers_for_key->second);
    for (ObserverInterface* observer : copy_observers)
      observer->OnPrefSet(key);
  }
  return true;
}

bool PrefsBase::GetInt64(const std::string_view key, int64_t* value) const {
  string str_value;
  if (!GetString(key, &str_value))
    return false;
  base::TrimWhitespaceASCII(str_value, base::TRIM_ALL, &str_value);
  if (str_value.empty()) {
    LOG(ERROR) << "When reading pref " << key
               << ", got an empty value after trim";
    return false;
  }
  if (!base::StringToInt64(str_value, value)) {
    LOG(ERROR) << "When reading pref " << key << ", failed to convert value "
               << str_value << " to integer";
    return false;
  }
  return true;
}

bool PrefsBase::SetInt64(std::string_view key, const int64_t value) {
  return SetString(key, base::NumberToString(value));
}

bool PrefsBase::GetBoolean(std::string_view key, bool* value) const {
  string str_value;
  if (!GetString(key, &str_value))
    return false;
  base::TrimWhitespaceASCII(str_value, base::TRIM_ALL, &str_value);
  if (str_value == "false") {
    *value = false;
    return true;
  }
  if (str_value == "true") {
    *value = true;
    return true;
  }
  return false;
}

bool PrefsBase::SetBoolean(std::string_view key, const bool value) {
  return SetString(key, value ? "true" : "false");
}

bool PrefsBase::Exists(std::string_view key) const {
  return storage_->KeyExists(key);
}

bool PrefsBase::Delete(std::string_view key) {
  TEST_AND_RETURN_FALSE(storage_->DeleteKey(key));
  const auto observers_for_key = observers_.find(key);
  if (observers_for_key != observers_.end()) {
    std::vector<ObserverInterface*> copy_observers(observers_for_key->second);
    for (ObserverInterface* observer : copy_observers)
      observer->OnPrefDeleted(key);
  }
  return true;
}

bool PrefsBase::Delete(std::string_view pref_key, const vector<string>& nss) {
  // Delete pref key for platform.
  bool success = Delete(pref_key);
  // Delete pref key in each namespace.
  for (const auto& ns : nss) {
    vector<string> namespace_keys;
    success = GetSubKeys(ns, &namespace_keys) && success;
    for (const auto& key : namespace_keys) {
      auto last_key_seperator = key.find_last_of(kKeySeparator);
      if (last_key_seperator != string::npos &&
          pref_key == key.substr(last_key_seperator + 1)) {
        success = Delete(key) && success;
      }
    }
  }
  return success;
}

bool PrefsBase::GetSubKeys(std::string_view ns, vector<string>* keys) const {
  return storage_->GetSubKeys(ns, keys);
}

void PrefsBase::AddObserver(std::string_view key, ObserverInterface* observer) {
  observers_[std::string{key}].push_back(observer);
}

void PrefsBase::RemoveObserver(std::string_view key,
                               ObserverInterface* observer) {
  std::vector<ObserverInterface*>& observers_for_key =
      observers_[std::string{key}];
  auto observer_it =
      std::find(observers_for_key.begin(), observers_for_key.end(), observer);
  if (observer_it != observers_for_key.end())
    observers_for_key.erase(observer_it);
}

string PrefsInterface::CreateSubKey(const vector<string>& ns_and_key) {
  return base::JoinString(ns_and_key, string(1, kKeySeparator));
}

// Prefs

bool Prefs::Init(const base::FilePath& prefs_dir) {
  return file_storage_.Init(prefs_dir);
}

bool PrefsBase::StartTransaction() {
  return storage_->CreateTemporaryPrefs();
}

bool PrefsBase::CancelTransaction() {
  return storage_->DeleteTemporaryPrefs();
}

bool PrefsBase::SubmitTransaction() {
  return storage_->SwapPrefs();
}

std::string Prefs::FileStorage::GetTemporaryDir() const {
  return prefs_dir_.value() + "_tmp";
}

bool Prefs::FileStorage::CreateTemporaryPrefs() {
  // Delete any existing prefs_tmp
  DeleteTemporaryPrefs();
  // Get the paths to the source and destination directories.
  std::filesystem::path source_directory(prefs_dir_.value());
  std::filesystem::path destination_directory(GetTemporaryDir());

  if (!std::filesystem::exists(source_directory)) {
    LOG(ERROR) << "prefs directory does not exist: " << source_directory;
    return false;
  }
  // Copy the directory.
  std::filesystem::copy(source_directory, destination_directory);

  return true;
}

bool Prefs::FileStorage::DeleteTemporaryPrefs() {
  std::filesystem::path destination_directory(GetTemporaryDir());

  if (std::filesystem::exists(destination_directory)) {
    return std::filesystem::remove_all(destination_directory);
  }
  return true;
}

bool Prefs::FileStorage::SwapPrefs() {
  if (!utils::DeleteDirectory(prefs_dir_.value().c_str())) {
    LOG(ERROR) << "Failed to remove prefs dir " << prefs_dir_;
    return false;
  }
  if (rename(GetTemporaryDir().c_str(), prefs_dir_.value().c_str()) != 0) {
    LOG(ERROR) << "Error replacing prefs with prefs_tmp" << strerror(errno);
    return false;
  }
  if (!utils::FsyncDirectory(
          android::base::Dirname(prefs_dir_.value()).c_str())) {
    PLOG(ERROR) << "Failed to fsync prefs parent dir after swapping prefs";
  }
  return true;
}

bool Prefs::FileStorage::Init(const base::FilePath& prefs_dir) {
  prefs_dir_ = prefs_dir;
  if (!std::filesystem::exists(prefs_dir_.value())) {
    LOG(INFO) << "Prefs dir does not exist, possibly due to an interrupted "
                 "transaction.";
    if (std::filesystem::exists(GetTemporaryDir())) {
      SwapPrefs();
    }
  }

  // Delete empty directories. Ignore errors when deleting empty directories.
  DeleteEmptyDirectories(prefs_dir_);
  return true;
}

bool Prefs::FileStorage::GetKey(std::string_view key, string* value) const {
  base::FilePath filename;
  TEST_AND_RETURN_FALSE(GetFileNameForKey(key, &filename));
  if (!base::ReadFileToString(filename, value)) {
    return false;
  }
  return true;
}

bool Prefs::FileStorage::GetSubKeys(std::string_view ns,
                                    vector<string>* keys) const {
  base::FilePath filename;
  TEST_AND_RETURN_FALSE(GetFileNameForKey(ns, &filename));
  base::FileEnumerator namespace_enum(
      prefs_dir_, true, base::FileEnumerator::FILES);
  for (base::FilePath f = namespace_enum.Next(); !f.empty();
       f = namespace_enum.Next()) {
    auto filename_str = filename.value();
    if (f.value().compare(0, filename_str.length(), filename_str) == 0) {
      // Only return the key portion excluding the |prefs_dir_| with slash.
      keys->push_back(f.value().substr(
          prefs_dir_.AsEndingWithSeparator().value().length()));
    }
  }
  return true;
}

bool Prefs::FileStorage::SetKey(std::string_view key, std::string_view value) {
  base::FilePath filename;
  TEST_AND_RETURN_FALSE(GetFileNameForKey(key, &filename));
  if (!base::DirectoryExists(filename.DirName())) {
    // Only attempt to create the directory if it doesn't exist to avoid calls
    // to parent directories where we might not have permission to write to.
    TEST_AND_RETURN_FALSE(base::CreateDirectory(filename.DirName()));
  }
  TEST_AND_RETURN_FALSE(
      utils::WriteStringToFileAtomic(filename.value(), value));
  return true;
}

bool Prefs::FileStorage::KeyExists(std::string_view key) const {
  base::FilePath filename;
  TEST_AND_RETURN_FALSE(GetFileNameForKey(key, &filename));
  return base::PathExists(filename);
}

bool Prefs::FileStorage::DeleteKey(std::string_view key) {
  base::FilePath filename;
  TEST_AND_RETURN_FALSE(GetFileNameForKey(key, &filename));
#if BASE_VER < 800000
  TEST_AND_RETURN_FALSE(base::DeleteFile(filename, false));
#else
  TEST_AND_RETURN_FALSE(base::DeleteFile(filename));
#endif
  return true;
}

bool Prefs::FileStorage::GetFileNameForKey(std::string_view key,
                                           base::FilePath* filename) const {
  // Allows only non-empty keys containing [A-Za-z0-9_-/].
  TEST_AND_RETURN_FALSE(!key.empty());
  for (char c : key)
    TEST_AND_RETURN_FALSE(base::IsAsciiAlpha(c) || base::IsAsciiDigit(c) ||
                          c == '_' || c == '-' || c == kKeySeparator);
  if (std::filesystem::exists(GetTemporaryDir())) {
    *filename =
        base::FilePath(GetTemporaryDir())
            .Append(base::FilePath::StringPieceType(key.data(), key.size()));
  } else {
    *filename = prefs_dir_.Append(
        base::FilePath::StringPieceType(key.data(), key.size()));
  }
  return true;
}

// MemoryPrefs

bool MemoryPrefs::MemoryStorage::GetKey(std::string_view key,
                                        string* value) const {
  auto it = values_.find(key);
  if (it == values_.end())
    return false;
  *value = it->second;
  return true;
}

bool MemoryPrefs::MemoryStorage::GetSubKeys(std::string_view ns,
                                            vector<string>* keys) const {
  auto lower_comp = [](const auto& pr, const auto& ns) {
    return std::string_view{pr.first.data(), ns.length()} < ns;
  };
  auto upper_comp = [](const auto& ns, const auto& pr) {
    return ns < std::string_view{pr.first.data(), ns.length()};
  };
  auto lower_it =
      std::lower_bound(begin(values_), end(values_), ns, lower_comp);
  auto upper_it = std::upper_bound(lower_it, end(values_), ns, upper_comp);
  while (lower_it != upper_it)
    keys->push_back((lower_it++)->first);
  return true;
}

bool MemoryPrefs::MemoryStorage::SetKey(std::string_view key,
                                        std::string_view value) {
  values_[std::string{key}] = value;
  return true;
}

bool MemoryPrefs::MemoryStorage::KeyExists(std::string_view key) const {
  return values_.find(key) != values_.end();
}

bool MemoryPrefs::MemoryStorage::DeleteKey(std::string_view key) {
  auto it = values_.find(key);
  if (it != values_.end())
    values_.erase(it);
  return true;
}

}  // namespace chromeos_update_engine
