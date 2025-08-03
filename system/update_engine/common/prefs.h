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

#ifndef UPDATE_ENGINE_COMMON_PREFS_H_
#define UPDATE_ENGINE_COMMON_PREFS_H_

#include <functional>
#include <map>
#include <string>
#include <string_view>
#include <vector>

#include <base/files/file_path.h>

#include "gtest/gtest_prod.h"  // for FRIEND_TEST
#include "update_engine/common/prefs_interface.h"

namespace chromeos_update_engine {

// Implements a preference store by storing the value associated with a key
// in a given storage passed during construction.
class PrefsBase : public PrefsInterface {
 public:
  // Storage interface used to set and retrieve keys.
  class StorageInterface {
   public:
    StorageInterface() = default;
    virtual ~StorageInterface() = default;

    // Get the key named |key| and store its value in the referenced |value|.
    // Returns whether the operation succeeded.
    virtual bool GetKey(std::string_view key, std::string* value) const = 0;

    // Get the keys stored within the namespace. If there are no keys in the
    // namespace, |keys| will be empty. Returns whether the operation succeeded.
    virtual bool GetSubKeys(std::string_view ns,
                            std::vector<std::string>* keys) const = 0;

    // Set the value of the key named |key| to |value| regardless of the
    // previous value. Returns whether the operation succeeded.
    virtual bool SetKey(std::string_view key, std::string_view value) = 0;

    // Returns whether the key named |key| exists.
    virtual bool KeyExists(std::string_view key) const = 0;

    // Deletes the value associated with the key name |key|. Returns whether the
    // key was deleted.
    virtual bool DeleteKey(std::string_view key) = 0;

    // Makes a copy of prefs directory called prefs_tmp, which is modified
    // during update_engine checkpointing
    virtual bool CreateTemporaryPrefs() { return false; }

    // Deletes prefs_tmp directory
    virtual bool DeleteTemporaryPrefs() { return false; }

    // Replaces prefs with prefs_tmp to make update_engine checkpointing more
    // atomic
    virtual bool SwapPrefs() { return false; }

   private:
    DISALLOW_COPY_AND_ASSIGN(StorageInterface);
  };

  explicit PrefsBase(StorageInterface* storage) : storage_(storage) {}

  // PrefsInterface methods.
  bool GetString(std::string_view key, std::string* value) const override;
  bool SetString(std::string_view key, std::string_view value) override;
  bool GetInt64(std::string_view key, int64_t* value) const override;
  bool SetInt64(std::string_view key, const int64_t value) override;
  bool GetBoolean(std::string_view key, bool* value) const override;
  bool SetBoolean(std::string_view key, const bool value) override;
  bool StartTransaction() override;
  bool CancelTransaction() override;
  bool SubmitTransaction() override;

  bool Exists(std::string_view key) const override;
  bool Delete(std::string_view key) override;
  bool Delete(std::string_view pref_key,
              const std::vector<std::string>& nss) override;

  bool GetSubKeys(std::string_view ns,
                  std::vector<std::string>* keys) const override;

  void AddObserver(std::string_view key, ObserverInterface* observer) override;
  void RemoveObserver(std::string_view key,
                      ObserverInterface* observer) override;

 private:
  // The registered observers watching for changes.
  std::map<std::string, std::vector<ObserverInterface*>, std::less<>>
      observers_;

  // The concrete implementation of the storage used for the keys.
  StorageInterface* storage_;

  DISALLOW_COPY_AND_ASSIGN(PrefsBase);
};

// Implements a preference store by storing the value associated with
// a key in a separate file named after the key under a preference
// store directory.

class Prefs : public PrefsBase {
 public:
  Prefs() : PrefsBase(&file_storage_) {}

  // Initializes the store by associating this object with |prefs_dir|
  // as the preference store directory. Returns true on success, false
  // otherwise.
  bool Init(const base::FilePath& prefs_dir);

 private:
  FRIEND_TEST(PrefsTest, GetFileNameForKey);
  FRIEND_TEST(PrefsTest, GetFileNameForKeyBadCharacter);
  FRIEND_TEST(PrefsTest, GetFileNameForKeyEmpty);

  class FileStorage : public PrefsBase::StorageInterface {
   public:
    FileStorage() = default;

    bool Init(const base::FilePath& prefs_dir);

    // PrefsBase::StorageInterface overrides.
    bool GetKey(std::string_view key, std::string* value) const override;
    bool GetSubKeys(std::string_view ns,
                    std::vector<std::string>* keys) const override;
    bool SetKey(std::string_view key, std::string_view value) override;
    bool KeyExists(std::string_view key) const override;
    bool DeleteKey(std::string_view key) override;
    bool CreateTemporaryPrefs() override;
    bool DeleteTemporaryPrefs() override;
    bool SwapPrefs() override;

   private:
    FRIEND_TEST(PrefsTest, GetFileNameForKey);
    FRIEND_TEST(PrefsTest, GetFileNameForKeyBadCharacter);
    FRIEND_TEST(PrefsTest, GetFileNameForKeyEmpty);

    // Sets |filename| to the full path to the file containing the data
    // associated with |key|. Returns true on success, false otherwise.
    bool GetFileNameForKey(std::string_view key,
                           base::FilePath* filename) const;

    // Returns path of prefs_tmp used during update_engine checkpointing
    std::string GetTemporaryDir() const;

    // Preference store directory.
    base::FilePath prefs_dir_;
  };

  // The concrete file storage implementation.
  FileStorage file_storage_;

  DISALLOW_COPY_AND_ASSIGN(Prefs);
};

// Implements a preference store in memory. The stored values are lost when the
// object is destroyed.

class MemoryPrefs : public PrefsBase {
 public:
  MemoryPrefs() : PrefsBase(&mem_storage_) {}

 private:
  class MemoryStorage : public PrefsBase::StorageInterface {
   public:
    MemoryStorage() = default;

    // PrefsBase::StorageInterface overrides.
    bool GetKey(std::string_view, std::string* value) const override;
    bool GetSubKeys(std::string_view ns,
                    std::vector<std::string>* keys) const override;
    bool SetKey(std::string_view key, std::string_view value) override;
    bool KeyExists(std::string_view key) const override;
    bool DeleteKey(std::string_view key) override;

   private:
    // The std::map holding the values in memory.
    std::map<std::string, std::string, std::less<>> values_;
  };

  // The concrete memory storage implementation.
  MemoryStorage mem_storage_;

  DISALLOW_COPY_AND_ASSIGN(MemoryPrefs);
};
}  // namespace chromeos_update_engine

#endif  // UPDATE_ENGINE_COMMON_PREFS_H_
