#define LOG_TAG "secnfc@1.2-service"
#include <android/hardware/nfc/1.2/INfc.h>
#include <log/log.h>

#include <hidl/LegacySupport.h>
#include "HalSecNfc.h"
#include "SecNfc.h"

// Generated HIDL files
using android::OK;
using android::sp;
using android::status_t;
using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;
using android::hardware::nfc::V1_2::INfc;
using android::hardware::nfc::V1_2::implementation::Nfc;

int main() {
  ALOGD("SEC NFC HAL Service 1.2 is starting.");
  sp<INfc> nfc_service = new Nfc();

  configureRpcThreadpool(1, true /*callerWillJoin*/);
  status_t status = nfc_service->registerAsService();
  if (status != OK) {
    LOG_ALWAYS_FATAL("Could not register service for NFC HAL Iface (%d).",
                     status);
    return -1;
  }

  ALOGD("NFC service is ready");
  joinRpcThreadpool();
  return 1;
}
