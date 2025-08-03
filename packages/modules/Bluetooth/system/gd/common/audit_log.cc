/*
 * Copyright 2022 The Android Open Source Project
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

#include "common/audit_log.h"

#include "common/strings.h"
#include "hci/hci_packets.h"
#include "os/log.h"

namespace {
#if defined(__ANDROID__) && !defined (FUZZ_TARGET)

// Tags for security logging, should be in sync with
// frameworks/base/core/java/android/app/admin/SecurityLogTags.logtags
constexpr int SEC_TAG_BLUETOOTH_CONNECTION = 210039;

#endif /* defined(__ANDROID__) && !defined (FUZZ_TARGET) */
}  // namespace

namespace bluetooth {
namespace common {

void LogConnectionAdminAuditEvent(
    [[maybe_unused]] const char* action,
    [[maybe_unused]] const hci::Address& address,
    [[maybe_unused]] hci::ErrorCode status) {
#if defined(__ANDROID__) && !defined (FUZZ_TARGET)

  android_log_event_list(SEC_TAG_BLUETOOTH_CONNECTION)
      << ADDRESS_TO_LOGGABLE_CSTR(address) << /* success */ int32_t(status == hci::ErrorCode::SUCCESS)
      << common::StringFormat("%s: %s", action, ErrorCodeText(status).c_str()).c_str() << LOG_ID_SECURITY;

#endif /* defined(__ANDROID__) && !defined (FUZZ_TARGET) */
}

}  // namespace common
}  // namespace bluetooth
