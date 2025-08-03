//#include "string"
#include "keystore/fake_bt_keystore.h"

#include <algorithm>
#include <functional>
#include <memory>

namespace bluetooth {
namespace fake_bluetooth_keystore {

class BluetoothKeystoreInterface::impl {
  friend BluetoothKeystoreInterface;
};

BluetoothKeystoreInterface::BluetoothKeystoreInterface() : impl(new class BluetoothKeystoreInterface::impl) {}

std::unique_ptr<BluetoothKeystoreInterface> new_bt_keystore_interface() {
  return std::make_unique<BluetoothKeystoreInterface>();
}

}  // namespace fake_bluetooth_keystore
}  // namespace bluetooth
