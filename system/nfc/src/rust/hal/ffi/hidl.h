#pragma once
#include <android/hardware/nfc/1.0/INfc.h>
#include <android/hardware/nfc/1.1/INfc.h>
#include <android/hardware/nfc/1.2/INfc.h>

namespace nfc {
namespace hal {

using android::hardware::nfc::V1_0::NfcStatus;
using android::hardware::nfc::V1_1::NfcEvent;

}  // namespace hal
}  // namespace nfc

#include "hal/hidl_hal.rs.h"

namespace nfc {
namespace hal {

void start_hal();
void stop_hal();
void send_command(rust::Slice<const uint8_t> data);

}  // namespace hal
}  // namespace nfc
