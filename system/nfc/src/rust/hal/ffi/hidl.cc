#include "hal/ffi/hidl.h"

#include <log/log.h>
#include <stdlib.h>

using ::android::wp;
using ::android::hardware::hidl_death_recipient;
using ::android::hidl::base::V1_0::IBase;

using android::OK;
using android::sp;
using android::status_t;

using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using android::hardware::nfc::V1_0::INfc;
using INfcV1_1 = android::hardware::nfc::V1_1::INfc;
using INfcV1_2 = android::hardware::nfc::V1_2::INfc;
using android::hardware::nfc::V1_1::INfcClientCallback;

namespace nfc {
namespace hal {
namespace {

class NfcHalDeathRecipient : public hidl_death_recipient {
 public:
  virtual void serviceDied(
      uint64_t /*cookie*/,
      const android::wp<::android::hidl::base::V1_0::IBase>& /*who*/) {
    LOG_FATAL("Nfc HAL service died!");
    abort();
  }
};

class NfcCallbackTrampoline : public INfcClientCallback {
 public:
  NfcCallbackTrampoline() {}

  Return<void> sendEvent_1_1(
      ::android::hardware::nfc::V1_1::NfcEvent event,
      ::android::hardware::nfc::V1_0::NfcStatus event_status) override {
    on_event(event, event_status);
    return Void();
  }
  Return<void> sendEvent(
      ::android::hardware::nfc::V1_0::NfcEvent event,
      ::android::hardware::nfc::V1_0::NfcStatus event_status) override {
    on_event((::android::hardware::nfc::V1_1::NfcEvent)event, event_status);
    return Void();
  }

  Return<void> sendData(const ::android::hardware::nfc::V1_0::NfcData& data) {
    on_data(rust::Slice(&data[0], data.size()));
    return Void();
  }
};

android::sp<NfcHalDeathRecipient> nfc_death_recipient_;
android::sp<INfc> nci_;
android::sp<INfcV1_1> nci_1_1_;
android::sp<INfcV1_2> nci_1_2_;
android::sp<NfcCallbackTrampoline> trampoline_;

}  // namespace

void start_hal() {
  ALOG_ASSERT(nci_ != nullptr, "Stale value of the NCI port");

  nci_ = nci_1_1_ = nci_1_2_ = INfcV1_2::getService();
  if (nci_1_2_ == nullptr) {
    nci_ = nci_1_1_ = INfcV1_1::getService();
    if (nci_1_1_ == nullptr) {
      nci_ = INfc::getService();
    }
  }
  LOG_FATAL_IF(nci_ == nullptr, "Failed to retrieve the NFC HAL!");
  ALOGI("%s: INfc::getService() returned %p (%s)", __func__, nci_.get(),
        (nci_->isRemote() ? "remote" : "local"));
  if (nci_) {
    nfc_death_recipient_ = new NfcHalDeathRecipient();
    auto death_link = nci_->linkToDeath(nfc_death_recipient_, 0);
    ALOG_ASSERT(death_link.isOk(),
                "Unable to set the death recipient for the Nfc HAL");
  }

  trampoline_ = new NfcCallbackTrampoline();
  if (nci_1_1_ != nullptr) {
    nci_1_1_->open_1_1(trampoline_);
  } else {
    nci_->open(trampoline_);
  }
}

void stop_hal() {
  ALOG_ASSERT(nci_ == nullptr, "The NCI communication was already closed");

  auto death_unlink = nci_->unlinkToDeath(nfc_death_recipient_);
  if (!death_unlink.isOk()) {
    ALOGE("Error unlinking death recipient from the Bluetooth HAL");
  }
  nci_->close();
  nci_ = nullptr;
  nci_1_1_ = nullptr;
  nci_1_2_ = nullptr;
  trampoline_ = nullptr;
}

void send_command(rust::Slice<const uint8_t> data) {
  ALOG_ASSERT(nci_ == nullptr, "The NCI communication was already closed");
  nci_->write(hidl_vec<uint8_t>(data.data(), data.data() + data.length()));
}

}  // namespace hal
}  // namespace nfc
