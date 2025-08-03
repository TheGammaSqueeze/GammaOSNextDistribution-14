//
// Copyright 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#pragma once

#include <android/hardware/bluetooth/1.1/IBluetoothHci.h>
#include <hidl/MQDescriptor.h>

#include "hci_packetizer.h"
#include "model/controller/dual_mode_controller.h"
#include "model/setup/async_manager.h"
#include "model/setup/test_channel_transport.h"
#include "model/setup/test_command_handler.h"
#include "model/setup/test_model.h"
#include "net/posix/posix_async_socket_connector.h"
#include "net/posix/posix_async_socket_server.h"
#include "os/log.h"

namespace android {
namespace hardware {
namespace bluetooth {
namespace V1_1 {
namespace sim {

class BluetoothDeathRecipient;

using android::net::AsyncDataChannel;
using android::net::AsyncDataChannelConnector;
using android::net::AsyncDataChannelServer;
using android::net::ConnectCallback;

using rootcanal::Device;
using rootcanal::Phy;

class BluetoothHci : public IBluetoothHci {
 public:
  BluetoothHci();

  ::android::hardware::Return<void> initialize(
      const sp<V1_0::IBluetoothHciCallbacks>& cb) override;
  ::android::hardware::Return<void> initialize_1_1(
      const sp<V1_1::IBluetoothHciCallbacks>& cb) override;

  ::android::hardware::Return<void> sendHciCommand(
      const ::android::hardware::hidl_vec<uint8_t>& packet) override;

  ::android::hardware::Return<void> sendAclData(
      const ::android::hardware::hidl_vec<uint8_t>& packet) override;

  ::android::hardware::Return<void> sendScoData(
      const ::android::hardware::hidl_vec<uint8_t>& packet) override;

  ::android::hardware::Return<void> sendIsoData(
      const ::android::hardware::hidl_vec<uint8_t>& packet) override;

  ::android::hardware::Return<void> close() override;

  static void OnPacketReady();

  static BluetoothHci* get();

 private:
  ::android::hardware::Return<void> initialize_impl(
      const sp<V1_0::IBluetoothHciCallbacks>& cb,
      const sp<V1_1::IBluetoothHciCallbacks>& cb_1_1);

  sp<BluetoothDeathRecipient> death_recipient_;

  std::function<void(sp<BluetoothDeathRecipient>&)> unlink_cb_;

  void HandleIncomingPacket();

  std::shared_ptr<AsyncDataChannelServer> test_socket_server_;
  std::shared_ptr<AsyncDataChannelServer> hci_socket_server_;
  std::shared_ptr<AsyncDataChannelServer> link_socket_server_;
  std::shared_ptr<AsyncDataChannelConnector> connector_;
  rootcanal::AsyncManager async_manager_;

  void SetUpTestChannel();
  void SetUpHciServer(ConnectCallback on_connect);
  void SetUpLinkLayerServer(ConnectCallback on_connect);
  std::shared_ptr<Device> ConnectToRemoteServer(const std::string& server,
                                                int port, Phy::Type phy_type);

  std::shared_ptr<rootcanal::DualModeController> controller_;

  rootcanal::TestChannelTransport test_channel_transport_;
  rootcanal::TestChannelTransport remote_hci_transport_;
  rootcanal::TestChannelTransport remote_link_layer_transport_;

  rootcanal::AsyncUserId user_id_ = async_manager_.GetNextUserId();
  rootcanal::TestModel test_model_{
      [this]() { return async_manager_.GetNextUserId(); },
      [this](rootcanal::AsyncUserId user_id, std::chrono::milliseconds delay,
             const rootcanal::TaskCallback& task) {
        return async_manager_.ExecAsync(user_id, delay, task);
      },

      [this](rootcanal::AsyncUserId user_id, std::chrono::milliseconds delay,
             std::chrono::milliseconds period,
             const rootcanal::TaskCallback& task) {
        return async_manager_.ExecAsyncPeriodically(user_id, delay, period,
                                                    task);
      },

      [this](rootcanal::AsyncUserId user) {
        async_manager_.CancelAsyncTasksFromUser(user);
      },

      [this](rootcanal::AsyncTaskId task) {
        async_manager_.CancelAsyncTask(task);
      },

      [this](const std::string& server, int port, Phy::Type phy_type) {
        return ConnectToRemoteServer(server, port, phy_type);
      }};
  rootcanal::TestCommandHandler test_channel_{test_model_};
};

extern "C" IBluetoothHci* HIDL_FETCH_IBluetoothHci(const char* name);

}  // namespace sim
}  // namespace V1_1
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
