/*
 * Copyright 2020 The Android Open Source Project
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

/* BluetoothKeystore Interface */

#include "btif_keystore.h"

#include <base/functional/bind.h>
#include <base/location.h>
#include <base/logging.h>
#include <bluetooth/log.h>
#include <hardware/bluetooth.h>

#include <map>

#include "btif_common.h"
#include "btif_storage.h"
#include "main/shim/config.h"
#include "main/shim/shim.h"
#include "os/parameter_provider.h"

using base::Bind;
using base::Unretained;
using bluetooth::bluetooth_keystore::BluetoothKeystoreCallbacks;
using bluetooth::bluetooth_keystore::BluetoothKeystoreInterface;

namespace bluetooth {
namespace bluetooth_keystore {
class BluetoothKeystoreInterfaceImpl;
std::unique_ptr<BluetoothKeystoreInterface> bluetoothKeystoreInstance;
const int CONFIG_COMPARE_ALL_PASS = 0b11;

class BluetoothKeystoreInterfaceImpl
    : public bluetooth::bluetooth_keystore::BluetoothKeystoreInterface {
  ~BluetoothKeystoreInterfaceImpl() override = default;

  void init(BluetoothKeystoreCallbacks* callbacks) override {
    log::verbose("");
    this->callbacks = callbacks;

    bluetooth::os::ParameterProvider::SetCommonCriteriaConfigCompareResult(
        CONFIG_COMPARE_ALL_PASS);
    ConvertEncryptOrDecryptKeyIfNeeded();
  }

  void ConvertEncryptOrDecryptKeyIfNeeded() {
    log::verbose("");
    if (!callbacks) {
      log::info("callback isn't ready.");
      return;
    }
    do_in_jni_thread(
        FROM_HERE, base::BindOnce([]() {
          shim::BtifConfigInterface::ConvertEncryptOrDecryptKeyIfNeeded();
        }));
  }

  bool set_encrypt_key_or_remove_key(std::string prefix,
                                     std::string decryptedString) override {
    log::verbose("prefix: {}", prefix);

    if (!callbacks) {
      log::warn("callback isn't ready. prefix: {}", prefix);
      return false;
    }

    // Save the value into a map.
    key_map[prefix] = decryptedString;

    do_in_jni_thread(base::BindOnce(
        &bluetooth::bluetooth_keystore::BluetoothKeystoreCallbacks::
            set_encrypt_key_or_remove_key,
        base::Unretained(callbacks), prefix, decryptedString));
    return true;
  }

  std::string get_key(std::string prefix) override {
    log::verbose("prefix: {}", prefix);

    if (!callbacks) {
      log::warn("callback isn't ready. prefix: {}", prefix);
      return "";
    }

    std::string decryptedString;
    // try to find the key.
    std::map<std::string, std::string>::iterator iter = key_map.find(prefix);
    if (iter == key_map.end()) {
      decryptedString = callbacks->get_key(prefix);
      // Save the value into a map.
      key_map[prefix] = decryptedString;
      log::verbose("get key from bluetoothkeystore.");
    } else {
      decryptedString = iter->second;
    }
    return decryptedString;
  }

  void clear_map() override {
    log::verbose("");

    std::map<std::string, std::string> empty_map;
    key_map.swap(empty_map);
    key_map.clear();
  }

 private:
  BluetoothKeystoreCallbacks* callbacks = nullptr;
  std::map<std::string, std::string> key_map;
};

BluetoothKeystoreInterface* getBluetoothKeystoreInterface() {
  if (!bluetoothKeystoreInstance) {
    bluetoothKeystoreInstance.reset(new BluetoothKeystoreInterfaceImpl());
  }

  return bluetoothKeystoreInstance.get();
}

}  // namespace bluetooth_keystore
}  // namespace bluetooth
