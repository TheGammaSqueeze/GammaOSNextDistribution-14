/*
 * Copyright 2019 The Android Open Source Project
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

#include <aidl/android/hardware/bluetooth/BnBluetoothHci.h>
#include <aidl/android/hardware/bluetooth/BnBluetoothHciCallbacks.h>
#include <aidl/android/hardware/bluetooth/IBluetoothHci.h>
#include <android/binder_ibinder.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <android/hardware/bluetooth/1.0/types.h>
#include <android/hardware/bluetooth/1.1/IBluetoothHci.h>
#include <android/hardware/bluetooth/1.1/IBluetoothHciCallbacks.h>
#include <stdlib.h>

// AIDL uses syslog.h, so these defines conflict with os/log.h
#undef LOG_DEBUG
#undef LOG_INFO
#undef LOG_WARNING

#include <future>
#include <mutex>
#include <vector>

#include "common/init_flags.h"
#include "common/stop_watch.h"
#include "common/strings.h"
#include "hal/hci_hal.h"
#include "hal/link_clocker.h"
#include "hal/snoop_logger.h"
#include "os/alarm.h"
#include "os/log.h"
#include "os/system_properties.h"

using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using IBluetoothHci_1_1 = ::android::hardware::bluetooth::V1_1::IBluetoothHci;
using IBluetoothHciCallbacks_1_1 = ::android::hardware::bluetooth::V1_1::IBluetoothHciCallbacks;
using HidlStatus = ::android::hardware::bluetooth::V1_0::Status;
using aidl::android::hardware::bluetooth::IBluetoothHci;
using AidlStatus = ::aidl::android::hardware::bluetooth::Status;
using IBluetoothHci_1_0 = ::android::hardware::bluetooth::V1_0::IBluetoothHci;
using bluetooth::common::BindOnce;

namespace bluetooth {
namespace hal {
namespace {

class HciDeathRecipient : public ::android::hardware::hidl_death_recipient {
 public:
  virtual void serviceDied(uint64_t /*cookie*/, const android::wp<::android::hidl::base::V1_0::IBase>& /*who*/) {
    LOG_ERROR("The Bluetooth HAL service died. Dumping logs and crashing in 1 second.");
    common::StopWatch::DumpStopWatchLog();
    // At shutdown, sometimes the HAL service gets killed before Bluetooth.
    std::this_thread::sleep_for(std::chrono::seconds(1));
    LOG_ALWAYS_FATAL("The Bluetooth HAL died.");
  }
};

android::sp<HciDeathRecipient> hci_death_recipient_ = new HciDeathRecipient();

template <class VecType>
std::string GetTimerText(const char* func_name, VecType vec) {
  return common::StringFormat(
      "%s: len %zu, 1st 5 bytes '%s'",
      func_name,
      vec.size(),
      common::ToHexString(vec.begin(), std::min(vec.end(), vec.begin() + 5)).c_str());
}

class InternalHciCallbacks : public IBluetoothHciCallbacks_1_1 {
 public:
  InternalHciCallbacks(SnoopLogger* btsnoop_logger, LinkClocker* link_clocker)
      : btsnoop_logger_(btsnoop_logger), link_clocker_(link_clocker) {
    init_promise_ = new std::promise<void>();
  }

  void SetCallback(HciHalCallbacks* callback) {
    std::lock_guard<std::mutex> incoming_packet_callback_lock(incoming_packet_callback_mutex_);
    ASSERT(callback_ == nullptr && callback != nullptr);
    callback_ = callback;
  }

  void ResetCallback() {
    std::lock_guard<std::mutex> incoming_packet_callback_lock(incoming_packet_callback_mutex_);
    LOG_INFO("callbacks have been reset!");
    callback_ = nullptr;
  }

  std::promise<void>* GetInitPromise() {
    return init_promise_;
  }

  Return<void> initializationComplete(HidlStatus status) {
    common::StopWatch stop_watch(__func__);
    LOG_INFO("initialization complete with status: %d", status);
    CHECK_EQ(status, HidlStatus::SUCCESS);
    init_promise_->set_value();
    return Void();
  }

  Return<void> hciEventReceived(const hidl_vec<uint8_t>& event) override {
    common::StopWatch stop_watch(GetTimerText(__func__, event));
    std::vector<uint8_t> received_hci_packet(event.begin(), event.end());
    link_clocker_->OnHciEvent(received_hci_packet);
    btsnoop_logger_->Capture(
        received_hci_packet, SnoopLogger::Direction::INCOMING, SnoopLogger::PacketType::EVT);
    {
      std::lock_guard<std::mutex> incoming_packet_callback_lock(incoming_packet_callback_mutex_);
      if (callback_ != nullptr) {
        callback_->hciEventReceived(std::move(received_hci_packet));
      }
    }
    return Void();
  }

  Return<void> aclDataReceived(const hidl_vec<uint8_t>& data) override {
    common::StopWatch stop_watch(GetTimerText(__func__, data));
    std::vector<uint8_t> received_hci_packet(data.begin(), data.end());
    link_clocker_->OnAclDataReceived(received_hci_packet);
    btsnoop_logger_->Capture(
        received_hci_packet, SnoopLogger::Direction::INCOMING, SnoopLogger::PacketType::ACL);
    {
      std::lock_guard<std::mutex> incoming_packet_callback_lock(incoming_packet_callback_mutex_);
      if (callback_ != nullptr) {
        callback_->aclDataReceived(std::move(received_hci_packet));
      }
    }
    return Void();
  }

  Return<void> scoDataReceived(const hidl_vec<uint8_t>& data) override {
    common::StopWatch stop_watch(GetTimerText(__func__, data));
    std::vector<uint8_t> received_hci_packet(data.begin(), data.end());
    btsnoop_logger_->Capture(
        received_hci_packet, SnoopLogger::Direction::INCOMING, SnoopLogger::PacketType::SCO);
    {
      std::lock_guard<std::mutex> incoming_packet_callback_lock(incoming_packet_callback_mutex_);
      if (callback_ != nullptr) {
        callback_->scoDataReceived(std::move(received_hci_packet));
      }
    }
    return Void();
  }

  Return<void> isoDataReceived(const hidl_vec<uint8_t>& data) override {
    common::StopWatch stop_watch(GetTimerText(__func__, data));
    std::vector<uint8_t> received_hci_packet(data.begin(), data.end());
    btsnoop_logger_->Capture(received_hci_packet, SnoopLogger::Direction::INCOMING, SnoopLogger::PacketType::ISO);

    {
      std::lock_guard<std::mutex> incoming_packet_callback_lock(incoming_packet_callback_mutex_);
      if (callback_ != nullptr) {
        callback_->isoDataReceived(std::move(received_hci_packet));
      }
    }
    return Void();
  }

 private:
  std::mutex incoming_packet_callback_mutex_;
  std::promise<void>* init_promise_ = nullptr;
  HciHalCallbacks* callback_ = nullptr;
  SnoopLogger* btsnoop_logger_ = nullptr;
  LinkClocker* link_clocker_ = nullptr;
};

static constexpr char kBluetoothAidlHalServiceName[] =
    "android.hardware.bluetooth.IBluetoothHci/default";

class AidlHciCallbacks : public ::aidl::android::hardware::bluetooth::BnBluetoothHciCallbacks {
 public:
  AidlHciCallbacks(SnoopLogger* btsnoop_logger, LinkClocker* link_clocker)
      : btsnoop_logger_(btsnoop_logger), link_clocker_(link_clocker) {
    init_promise_ = new std::promise<void>();
  }

  void SetCallback(HciHalCallbacks* callback) {
    std::lock_guard<std::mutex> incoming_packet_callback_lock(incoming_packet_callback_mutex_);
    ASSERT(callback_ == nullptr && callback != nullptr);
    callback_ = callback;
  }

  void ResetCallback() {
    std::lock_guard<std::mutex> incoming_packet_callback_lock(incoming_packet_callback_mutex_);
    callback_ = nullptr;
  }

  std::promise<void>* GetInitPromise() {
    return init_promise_;
  }

  ::ndk::ScopedAStatus initializationComplete(AidlStatus status) {
    common::StopWatch stop_watch(__func__);
    ASSERT(status == AidlStatus::SUCCESS);
    init_promise_->set_value();
    return ::ndk::ScopedAStatus::ok();
  }

  ::ndk::ScopedAStatus hciEventReceived(const std::vector<uint8_t>& event) override {
    common::StopWatch stop_watch(GetTimerText(__func__, event));
    std::vector<uint8_t> received_hci_packet(event.begin(), event.end());
    link_clocker_->OnHciEvent(received_hci_packet);
    btsnoop_logger_->Capture(
        received_hci_packet, SnoopLogger::Direction::INCOMING, SnoopLogger::PacketType::EVT);
    bool sent = false;
    {
      std::lock_guard<std::mutex> incoming_packet_callback_lock(incoming_packet_callback_mutex_);
      if (callback_ != nullptr) {
        callback_->hciEventReceived(std::move(received_hci_packet));
        sent = true;
      }
    }
    if (!sent) {
      LOG_INFO("Dropping HCI Event, since callback_ is null");
    }
    return ::ndk::ScopedAStatus::ok();
  }

  ::ndk::ScopedAStatus aclDataReceived(const std::vector<uint8_t>& data) override {
    common::StopWatch stop_watch(GetTimerText(__func__, data));
    std::vector<uint8_t> received_hci_packet(data.begin(), data.end());
    link_clocker_->OnAclDataReceived(received_hci_packet);
    btsnoop_logger_->Capture(
        received_hci_packet, SnoopLogger::Direction::INCOMING, SnoopLogger::PacketType::ACL);
    bool sent = false;
    {
      std::lock_guard<std::mutex> incoming_packet_callback_lock(incoming_packet_callback_mutex_);
      if (callback_ != nullptr) {
        callback_->aclDataReceived(std::move(received_hci_packet));
        sent = true;
      }
    }
    if (!sent) {
      LOG_INFO("Dropping ACL Data, since callback_ is null");
    }
    return ::ndk::ScopedAStatus::ok();
  }

  ::ndk::ScopedAStatus scoDataReceived(const std::vector<uint8_t>& data) override {
    common::StopWatch stop_watch(GetTimerText(__func__, data));
    std::vector<uint8_t> received_hci_packet(data.begin(), data.end());
    btsnoop_logger_->Capture(
        received_hci_packet, SnoopLogger::Direction::INCOMING, SnoopLogger::PacketType::SCO);
    bool sent = false;
    {
      std::lock_guard<std::mutex> incoming_packet_callback_lock(incoming_packet_callback_mutex_);
      if (callback_ != nullptr) {
        callback_->scoDataReceived(std::move(received_hci_packet));
        sent = true;
      }
    }
    if (!sent) {
      LOG_INFO("Dropping SCO Data, since callback_ is null");
    }
    return ::ndk::ScopedAStatus::ok();
  }

  ::ndk::ScopedAStatus isoDataReceived(const std::vector<uint8_t>& data) override {
    common::StopWatch stop_watch(GetTimerText(__func__, data));
    std::vector<uint8_t> received_hci_packet(data.begin(), data.end());
    btsnoop_logger_->Capture(
        received_hci_packet, SnoopLogger::Direction::INCOMING, SnoopLogger::PacketType::ISO);
    bool sent = false;
    {
      std::lock_guard<std::mutex> incoming_packet_callback_lock(incoming_packet_callback_mutex_);
      if (callback_ != nullptr) {
        callback_->isoDataReceived(std::move(received_hci_packet));
        sent = true;
      }
    }
    if (!sent) {
      LOG_INFO("Dropping ISO Data, since callback_ is null");
    }
    return ::ndk::ScopedAStatus::ok();
  }

 private:
  std::mutex incoming_packet_callback_mutex_;
  std::promise<void>* init_promise_ = nullptr;
  HciHalCallbacks* callback_ = nullptr;
  SnoopLogger* btsnoop_logger_ = nullptr;
  LinkClocker* link_clocker_ = nullptr;
};

}  // namespace

class HciHalHidl : public HciHal {
 public:
  void registerIncomingPacketCallback(HciHalCallbacks* callback) override {
    if (aidl_callbacks_) {
      aidl_callbacks_->SetCallback(callback);
    }
    if (hidl_callbacks_) {
      hidl_callbacks_->SetCallback(callback);
    }
  }

  void unregisterIncomingPacketCallback() override {
    if (aidl_callbacks_) {
      aidl_callbacks_->ResetCallback();
    }
    if (hidl_callbacks_) {
      hidl_callbacks_->ResetCallback();
    }
  }

  void sendHciCommand(HciPacket command) override {
    btsnoop_logger_->Capture(
        command, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::CMD);
    if (aidl_hci_) {
      aidl_hci_->sendHciCommand(command);
    } else {
      bt_hci_->sendHciCommand(command);
    }
  }

  void sendAclData(HciPacket packet) override {
    btsnoop_logger_->Capture(
        packet, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::ACL);
    if (aidl_hci_) {
      aidl_hci_->sendAclData(packet);
    } else {
      bt_hci_->sendAclData(packet);
    }
  }

  void sendScoData(HciPacket packet) override {
    btsnoop_logger_->Capture(
        packet, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::SCO);
    if (aidl_hci_) {
      aidl_hci_->sendScoData(packet);
    } else {
      bt_hci_->sendScoData(packet);
    }
  }

  void sendIsoData(HciPacket packet) override {
    if (aidl_hci_ == nullptr && bt_hci_1_1_ == nullptr) {
      LOG_ERROR("ISO is not supported in HAL v1.0");
      return;
    }

    btsnoop_logger_->Capture(packet, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::ISO);
    if (aidl_hci_) {
      aidl_hci_->sendIsoData(packet);
    } else {
      bt_hci_1_1_->sendIsoData(packet);
    }
  }

 protected:
  void ListDependencies(ModuleList* list) const {
    list->add<LinkClocker>();
    list->add<SnoopLogger>();
  }

  void Start() override {
    common::StopWatch stop_watch(__func__);

    // Start can't be called more than once before Stop is called.
    ASSERT(bt_hci_ == nullptr);
    ASSERT(bt_hci_1_1_ == nullptr);
    ASSERT(aidl_hci_ == nullptr);

    link_clocker_ = GetDependency<LinkClocker>();
    btsnoop_logger_ = GetDependency<SnoopLogger>();

    if (AServiceManager_isDeclared(kBluetoothAidlHalServiceName)) {
      start_aidl();
      aidl_callbacks_->GetInitPromise()->get_future().wait();
    } else {
      start_hidl();
      hidl_callbacks_->GetInitPromise()->get_future().wait();
    }
  }

  void start_aidl() {
    common::StopWatch stop_watch(__func__);
    ::ndk::SpAIBinder binder(AServiceManager_waitForService(kBluetoothAidlHalServiceName));
    aidl_hci_ = IBluetoothHci::fromBinder(binder);
    if (aidl_hci_ != nullptr) {
      LOG_INFO("Using the AIDL interface");
      aidl_death_recipient_ =
          ::ndk::ScopedAIBinder_DeathRecipient(AIBinder_DeathRecipient_new([](void* /* cookie*/) {
            LOG_ERROR("The Bluetooth HAL service died. Dumping logs and crashing in 1 second.");
            common::StopWatch::DumpStopWatchLog();
            // At shutdown, sometimes the HAL service gets killed before Bluetooth.
            std::this_thread::sleep_for(std::chrono::seconds(1));
            LOG_ALWAYS_FATAL("The Bluetooth HAL died.");
          }));

      auto death_link =
          AIBinder_linkToDeath(aidl_hci_->asBinder().get(), aidl_death_recipient_.get(), this);

      ASSERT_LOG(
          death_link == STATUS_OK, "Unable to set the death recipient for the Bluetooth HAL");

      aidl_callbacks_ =
          ::ndk::SharedRefBase::make<AidlHciCallbacks>(btsnoop_logger_, link_clocker_);
      aidl_hci_->initialize(aidl_callbacks_);
    }
  }

  void start_hidl() {
    common::StopWatch stop_watch(__func__);

    LOG_INFO("Trying to find a HIDL interface");

    auto get_service_alarm = new os::Alarm(GetHandler());
    get_service_alarm->Schedule(
        BindOnce([] {
          const std::string kBoardProperty = "ro.product.board";
          const std::string kCuttlefishBoard = "cutf";
          auto board_name = os::GetSystemProperty(kBoardProperty);
          bool emulator = board_name.has_value() && board_name.value() == kCuttlefishBoard;
          if (emulator) {
            LOG_ERROR("board_name: %s", board_name.value().c_str());
            LOG_ERROR("Unable to get a Bluetooth service after 500ms, start the HAL before starting Bluetooth");
            return;
          }
          LOG_ALWAYS_FATAL("Unable to get a Bluetooth service after 500ms, start the HAL before starting Bluetooth");
        }),
        std::chrono::milliseconds(500));

    bt_hci_1_1_ = IBluetoothHci_1_1::getService();

    if (bt_hci_1_1_ != nullptr) {
      bt_hci_ = bt_hci_1_1_;
    } else {
      bt_hci_ = IBluetoothHci_1_0::getService();
    }

    get_service_alarm->Cancel();
    delete get_service_alarm;

    ASSERT(bt_hci_ != nullptr);
    auto death_link = bt_hci_->linkToDeath(hci_death_recipient_, 0);
    ASSERT_LOG(death_link.isOk(), "Unable to set the death recipient for the Bluetooth HAL");
    hidl_callbacks_ = new InternalHciCallbacks(btsnoop_logger_, link_clocker_);

    if (bt_hci_1_1_ != nullptr) {
      bt_hci_1_1_->initialize_1_1(hidl_callbacks_);
    } else {
      bt_hci_->initialize(hidl_callbacks_);
    }
  }

  void Stop() override {
    if (bt_hci_ != nullptr) {
      stop_hidl();
    }
    if (aidl_hci_ != nullptr) {
      stop_aidl();
    }
  }

  std::string ToString() const override {
    return std::string("HciHalHidl");
  }

 private:
  void stop_hidl() {
    ASSERT(bt_hci_ != nullptr);
    auto death_unlink = bt_hci_->unlinkToDeath(hci_death_recipient_);
    if (!death_unlink.isOk()) {
      LOG_ERROR("Error unlinking death recipient from the Bluetooth HAL");
    }
    auto close_status = bt_hci_->close();
    if (!close_status.isOk()) {
      LOG_ERROR("Error calling close on the Bluetooth HAL");
    }
    bt_hci_ = nullptr;
    bt_hci_1_1_ = nullptr;
    hidl_callbacks_->ResetCallback();
  }

  void stop_aidl() {
    ASSERT(aidl_hci_ != nullptr);
    auto death_unlink =
        AIBinder_unlinkToDeath(aidl_hci_->asBinder().get(), aidl_death_recipient_.get(), this);
    if (death_unlink != STATUS_OK) {
      LOG_ERROR("Error unlinking death recipient from the Bluetooth HAL");
    }
    auto close_status = aidl_hci_->close();
    if (!close_status.isOk()) {
      LOG_ERROR("Error calling close on the Bluetooth HAL");
    }
    aidl_hci_ = nullptr;
    aidl_callbacks_->ResetCallback();
  }
  android::sp<InternalHciCallbacks> hidl_callbacks_;
  android::sp<IBluetoothHci_1_0> bt_hci_;
  android::sp<IBluetoothHci_1_1> bt_hci_1_1_;
  std::shared_ptr<IBluetoothHci> aidl_hci_;
  std::shared_ptr<AidlHciCallbacks> aidl_callbacks_;
  ::ndk::ScopedAIBinder_DeathRecipient aidl_death_recipient_;
  SnoopLogger* btsnoop_logger_;
  LinkClocker* link_clocker_;
};

const ModuleFactory HciHal::Factory = ModuleFactory([]() { return new HciHalHidl(); });

}  // namespace hal
}  // namespace bluetooth
