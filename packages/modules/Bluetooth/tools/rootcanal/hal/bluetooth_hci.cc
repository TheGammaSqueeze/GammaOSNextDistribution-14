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

#define LOG_TAG "android.hardware.bluetooth@1.1.sim"

#include "bluetooth_hci.h"

#include <cutils/properties.h>
#include <netdb.h>
#include <netinet/in.h>
#include <string.h>

#include "hci_internals.h"
#include "log/log.h"
#include "model/devices/hci_device.h"
#include "model/devices/link_layer_socket_device.h"
#include "model/hci/hci_socket_transport.h"

namespace android {
namespace hardware {
namespace bluetooth {
namespace V1_1 {
namespace sim {

using android::hardware::hidl_vec;
using ::bluetooth::hci::Address;
using rootcanal::AsyncTaskId;
using rootcanal::DualModeController;
using rootcanal::HciDevice;
using rootcanal::HciSocketTransport;
using rootcanal::LinkLayerSocketDevice;
using rootcanal::TaskCallback;

namespace {

bool BtTestConsoleEnabled() {
  // Assume enabled by default.
  return property_get_bool("vendor.bt.rootcanal_test_console", true);
}

}  // namespace

class BluetoothDeathRecipient : public hidl_death_recipient {
 public:
  BluetoothDeathRecipient(const sp<IBluetoothHci> hci) : mHci(hci) {}

  void serviceDied(
      uint64_t /* cookie */,
      const wp<::android::hidl::base::V1_0::IBase>& /* who */) override {
    LOG_ERROR("BluetoothDeathRecipient::serviceDied - Bluetooth service died");
    has_died_ = true;
    mHci->close();
  }
  sp<IBluetoothHci> mHci;
  bool getHasDied() const { return has_died_; }
  void setHasDied(bool has_died) { has_died_ = has_died; }

 private:
  bool has_died_{false};
};

BluetoothHci::BluetoothHci()
    : death_recipient_(new BluetoothDeathRecipient(this)) {}

Return<void> BluetoothHci::initialize(
    const sp<V1_0::IBluetoothHciCallbacks>& cb) {
  return initialize_impl(cb, nullptr);
}

Return<void> BluetoothHci::initialize_1_1(
    const sp<V1_1::IBluetoothHciCallbacks>& cb) {
  return initialize_impl(cb, cb);
}

Return<void> BluetoothHci::initialize_impl(
    const sp<V1_0::IBluetoothHciCallbacks>& cb,
    const sp<V1_1::IBluetoothHciCallbacks>& cb_1_1) {
  LOG_INFO("%s", __func__);
  if (cb == nullptr) {
    LOG_ERROR("cb == nullptr! -> Unable to call initializationComplete(ERR)");
    return Void();
  }

  death_recipient_->setHasDied(false);
  auto link_ret = cb->linkToDeath(death_recipient_, 0);
  ASSERT_LOG(link_ret.isOk(), "Error calling linkToDeath.");

  test_channel_transport_.RegisterCommandHandler(
      [this](const std::string& name, const std::vector<std::string>& args) {
        async_manager_.ExecAsync(
            user_id_, std::chrono::milliseconds(0),
            [this, name, args]() { test_channel_.HandleCommand(name, args); });
      });

  controller_ = std::make_shared<DualModeController>();

  char mac_property[PROPERTY_VALUE_MAX] = "";
  property_get("vendor.bt.rootcanal_mac_address", mac_property,
               "3C:5A:B4:01:02:03");
  auto addr = Address::FromString(std::string(mac_property));
  if (addr) {
    controller_->SetAddress(*addr);
  } else {
    LOG_ALWAYS_FATAL("Invalid address: %s", mac_property);
  }

  controller_->RegisterEventChannel(
      [this, cb](std::shared_ptr<std::vector<uint8_t>> packet) {
        hidl_vec<uint8_t> hci_event(packet->begin(), packet->end());
        auto ret = cb->hciEventReceived(hci_event);
        if (!ret.isOk()) {
          LOG_ERROR("Error sending event callback");
          if (!death_recipient_->getHasDied()) {
            LOG_ERROR("Closing");
            close();
          }
        }
      });

  controller_->RegisterAclChannel(
      [this, cb](std::shared_ptr<std::vector<uint8_t>> packet) {
        hidl_vec<uint8_t> acl_packet(packet->begin(), packet->end());
        auto ret = cb->aclDataReceived(acl_packet);
        if (!ret.isOk()) {
          LOG_ERROR("Error sending acl callback");
          if (!death_recipient_->getHasDied()) {
            LOG_ERROR("Closing");
            close();
          }
        }
      });

  controller_->RegisterScoChannel(
      [this, cb](std::shared_ptr<std::vector<uint8_t>> packet) {
        hidl_vec<uint8_t> sco_packet(packet->begin(), packet->end());
        auto ret = cb->scoDataReceived(sco_packet);
        if (!ret.isOk()) {
          LOG_ERROR("Error sending sco callback");
          if (!death_recipient_->getHasDied()) {
            LOG_ERROR("Closing");
            close();
          }
        }
      });

  if (cb_1_1 != nullptr) {
    controller_->RegisterIsoChannel(
        [this, cb_1_1](std::shared_ptr<std::vector<uint8_t>> packet) {
          hidl_vec<uint8_t> iso_packet(packet->begin(), packet->end());
          auto ret = cb_1_1->isoDataReceived(iso_packet);
          if (!ret.isOk()) {
            LOG_ERROR("Error sending iso callback");
            if (!death_recipient_->getHasDied()) {
              LOG_ERROR("Closing");
              close();
            }
          }
        });
  }

  // Add the controller as a device in the model.
  size_t controller_index = test_model_.AddDevice(controller_);
  size_t low_energy_phy_index =
      test_model_.AddPhy(rootcanal::Phy::Type::LOW_ENERGY);
  size_t classic_phy_index = test_model_.AddPhy(rootcanal::Phy::Type::BR_EDR);
  test_model_.AddDeviceToPhy(controller_index, low_energy_phy_index);
  test_model_.AddDeviceToPhy(controller_index, classic_phy_index);
  test_model_.SetTimerPeriod(std::chrono::milliseconds(10));
  test_model_.StartTimer();

  // Send responses to logcat if the test channel is not configured.
  test_channel_.RegisterSendResponse([](const std::string& response) {
    LOG_INFO("No test channel yet: %s", response.c_str());
  });

  if (BtTestConsoleEnabled()) {
    test_socket_server_ =
        std::make_shared<net::PosixAsyncSocketServer>(6111, &async_manager_);
    hci_socket_server_ =
        std::make_shared<net::PosixAsyncSocketServer>(6211, &async_manager_);
    link_socket_server_ =
        std::make_shared<net::PosixAsyncSocketServer>(6311, &async_manager_);
    connector_ =
        std::make_shared<net::PosixAsyncSocketConnector>(&async_manager_);
    SetUpTestChannel();
    SetUpHciServer([this](std::shared_ptr<AsyncDataChannel> socket,
                          AsyncDataChannelServer* srv) {
      auto transport = HciSocketTransport::Create(socket);
      test_model_.AddHciConnection(
          HciDevice::Create(transport, rootcanal::ControllerProperties()));
      srv->StartListening();
    });
    SetUpLinkLayerServer([this](std::shared_ptr<AsyncDataChannel> socket,
                                AsyncDataChannelServer* srv) {
      auto phy_type = Phy::Type::BR_EDR;
      test_model_.AddLinkLayerConnection(
          LinkLayerSocketDevice::Create(socket, phy_type), phy_type);
      srv->StartListening();
    });
  } else {
    // This should be configurable in the future.
    LOG_INFO("Adding Beacons so the scan list is not empty.");
    test_channel_.AddDevice({"beacon", "be:ac:10:00:00:01", "1000"});
    test_model_.AddDeviceToPhy(controller_index + 1, low_energy_phy_index);
    test_channel_.AddDevice({"beacon", "be:ac:10:00:00:02", "1000"});
    test_model_.AddDeviceToPhy(controller_index + 2, low_energy_phy_index);
    test_channel_.AddDevice(
        {"scripted_beacon", "5b:ea:c1:00:00:03",
         "/data/vendor/bluetooth/bluetooth_sim_ble_playback_file",
         "/data/vendor/bluetooth/bluetooth_sim_ble_playback_events"});
    test_model_.AddDeviceToPhy(controller_index + 3, low_energy_phy_index);
    test_channel_.List({});
  }

  unlink_cb_ = [this, cb](sp<BluetoothDeathRecipient>& death_recipient) {
    if (death_recipient->getHasDied())
      LOG_INFO("Skipping unlink call, service died.");
    else {
      auto ret = cb->unlinkToDeath(death_recipient);
      if (!ret.isOk()) {
        ASSERT_LOG(death_recipient_->getHasDied(),
                   "Error calling unlink, but no death notification.");
      }
    }
  };

  auto init_ret = cb->initializationComplete(V1_0::Status::SUCCESS);
  if (!init_ret.isOk()) {
    ASSERT_LOG(death_recipient_->getHasDied(),
               "Error sending init callback, but no death notification.");
  }
  return Void();
}

Return<void> BluetoothHci::close() {
  LOG_INFO("%s", __func__);
  test_model_.Reset();
  return Void();
}

Return<void> BluetoothHci::sendHciCommand(const hidl_vec<uint8_t>& packet) {
  async_manager_.ExecAsync(user_id_, std::chrono::milliseconds(0),
                           [this, packet]() {
                             std::shared_ptr<std::vector<uint8_t>> packet_copy =
                                 std::shared_ptr<std::vector<uint8_t>>(
                                     new std::vector<uint8_t>(packet));
                             controller_->HandleCommand(packet_copy);
                           });
  return Void();
}

Return<void> BluetoothHci::sendAclData(const hidl_vec<uint8_t>& packet) {
  async_manager_.ExecAsync(user_id_, std::chrono::milliseconds(0),
                           [this, packet]() {
                             std::shared_ptr<std::vector<uint8_t>> packet_copy =
                                 std::shared_ptr<std::vector<uint8_t>>(
                                     new std::vector<uint8_t>(packet));
                             controller_->HandleAcl(packet_copy);
                           });
  return Void();
}

Return<void> BluetoothHci::sendScoData(const hidl_vec<uint8_t>& packet) {
  async_manager_.ExecAsync(user_id_, std::chrono::milliseconds(0),
                           [this, packet]() {
                             std::shared_ptr<std::vector<uint8_t>> packet_copy =
                                 std::shared_ptr<std::vector<uint8_t>>(
                                     new std::vector<uint8_t>(packet));
                             controller_->HandleSco(packet_copy);
                           });
  return Void();
}

Return<void> BluetoothHci::sendIsoData(const hidl_vec<uint8_t>& packet) {
  async_manager_.ExecAsync(user_id_, std::chrono::milliseconds(0),
                           [this, packet]() {
                             std::shared_ptr<std::vector<uint8_t>> packet_copy =
                                 std::shared_ptr<std::vector<uint8_t>>(
                                     new std::vector<uint8_t>(packet));
                             controller_->HandleIso(packet_copy);
                           });
  return Void();
}

void BluetoothHci::SetUpHciServer(ConnectCallback connection_callback) {
  test_channel_.RegisterSendResponse([](const std::string& response) {
    LOG_INFO("No HCI Response channel: %s", response.c_str());
  });

  if (!remote_hci_transport_.SetUp(hci_socket_server_, connection_callback)) {
    LOG_ERROR("Remote HCI channel SetUp failed.");
    return;
  }
}

void BluetoothHci::SetUpLinkLayerServer(ConnectCallback connection_callback) {
  remote_link_layer_transport_.SetUp(link_socket_server_, connection_callback);

  test_channel_.RegisterSendResponse([](const std::string& response) {
    LOG_INFO("No LinkLayer Response channel: %s", response.c_str());
  });
}

std::shared_ptr<Device> BluetoothHci::ConnectToRemoteServer(
    const std::string& server, int port, Phy::Type phy_type) {
  auto socket = connector_->ConnectToRemoteServer(server, port);
  if (!socket->Connected()) return nullptr;
  return LinkLayerSocketDevice::Create(socket, phy_type);
}

void BluetoothHci::SetUpTestChannel() {
  bool transport_configured = test_channel_transport_.SetUp(
      test_socket_server_, [this](std::shared_ptr<AsyncDataChannel> conn_fd,
                                  AsyncDataChannelServer*) {
        LOG_INFO("Test channel connection accepted.");
        test_channel_.RegisterSendResponse(
            [this, conn_fd](const std::string& response) {
              test_channel_transport_.SendResponse(conn_fd, response);
            });

        conn_fd->WatchForNonBlockingRead([this](AsyncDataChannel* conn_fd) {
          test_channel_transport_.OnCommandReady(conn_fd, []() {});
        });
        return false;
      });
  test_channel_.RegisterSendResponse([](const std::string& response) {
    LOG_INFO("No test channel: %s", response.c_str());
  });

  if (!transport_configured) {
    LOG_ERROR("Test channel SetUp failed.");
    return;
  }

  LOG_INFO("Test channel SetUp() successful");
}

/* Fallback to shared library if there is no service. */
IBluetoothHci* HIDL_FETCH_IBluetoothHci(const char* /* name */) {
  return new BluetoothHci();
}

}  // namespace sim
}  // namespace V1_1
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
