#pragma once
#include <memory>

#include "rust/cxx.h"

namespace bluetooth {
namespace fake_bluetooth_keystore {

class BluetoothKeystoreInterface {
 public:
  BluetoothKeystoreInterface();

 private:
  class impl;
  std::shared_ptr<impl> impl;
};

std::unique_ptr<BluetoothKeystoreInterface> new_bt_keystore_interface();

}  // namespace fake_bluetooth_keystore
}  // namespace bluetooth

#include "src/bridge.rs.h"