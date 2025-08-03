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

#ifndef UPDATE_ENGINE_COMMON_MOCK_PREFS_H_
#define UPDATE_ENGINE_COMMON_MOCK_PREFS_H_

#include <string>
#include <vector>

#include <gmock/gmock.h>

#include "update_engine/common/constants.h"
#include "update_engine/common/prefs_interface.h"

namespace chromeos_update_engine {

class MockPrefs : public PrefsInterface {
 public:
  MOCK_CONST_METHOD2(GetString, bool(std::string_view key, std::string* value));
  MOCK_METHOD2(SetString, bool(std::string_view key, std::string_view value));
  MOCK_CONST_METHOD2(GetInt64, bool(std::string_view key, int64_t* value));
  MOCK_METHOD2(SetInt64, bool(std::string_view key, const int64_t value));

  MOCK_CONST_METHOD2(GetBoolean, bool(std::string_view key, bool* value));
  MOCK_METHOD2(SetBoolean, bool(std::string_view key, const bool value));

  MOCK_CONST_METHOD1(Exists, bool(std::string_view key));
  MOCK_METHOD1(Delete, bool(std::string_view key));
  MOCK_METHOD2(Delete,
               bool(std::string_view key, const std::vector<std::string>& nss));

  MOCK_CONST_METHOD2(GetSubKeys,
                     bool(std::string_view, std::vector<std::string>*));

  MOCK_METHOD2(AddObserver, void(std::string_view key, ObserverInterface*));
  MOCK_METHOD2(RemoveObserver, void(std::string_view key, ObserverInterface*));
  MOCK_METHOD(bool, StartTransaction, (), (override));
  MOCK_METHOD(bool, CancelTransaction, (), (override));
  MOCK_METHOD(bool, SubmitTransaction, (), (override));
};

}  // namespace chromeos_update_engine

#endif  // UPDATE_ENGINE_COMMON_MOCK_PREFS_H_
