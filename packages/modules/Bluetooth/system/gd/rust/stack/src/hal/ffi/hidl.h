#pragma once
#include "src/hal/hidl_hal.rs.h"

namespace bluetooth {
namespace hal {

void start_hal();
void stop_hal();
void send_command(rust::Slice<const uint8_t> data);
void send_acl(rust::Slice<const uint8_t> data);
void send_sco(rust::Slice<const uint8_t> data);
void send_iso(rust::Slice<const uint8_t> data);

}  // namespace hal
}  // namespace bluetooth
