/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "chre_host/time_syncer.h"
#include <chre_host/host_protocol_host.h>
#include "chre_host/log.h"

namespace android::chre {
// TODO(b/247124878): Can we add a static assert to make sure these functions
//  are not called when connection->isTimeSyncNeeded() returns false?
bool TimeSyncer::sendTimeSync(ChreConnection *connection) {
  if (!connection->isTimeSyncNeeded()) {
    LOGW("Platform doesn't require time sync. Ignore the request.");
    return true;
  }
  int64_t timeOffsetUs = 0;
  if (!connection->getTimeOffset(&timeOffsetUs)) {
    LOGE("Failed to get time offset.");
    return false;
  }
  flatbuffers::FlatBufferBuilder builder(64);
  // clientId doesn't matter for time sync request so the default id is used.
  HostProtocolHost::encodeTimeSyncMessage(builder, timeOffsetUs);
  return connection->sendMessage(builder.GetBufferPointer(), builder.GetSize());
}

bool TimeSyncer::sendTimeSyncWithRetry(ChreConnection *connection,
                                       size_t numOfRetries,
                                       useconds_t retryDelayUs) {
  if (!connection->isTimeSyncNeeded()) {
    LOGW("Platform doesn't require time sync. Ignore the request.");
    return true;
  }
  bool success = false;
  while (!success && (numOfRetries-- > 0)) {
    success = sendTimeSync(connection);
    if (!success) {
      usleep(retryDelayUs);
    }
  }
  return success;
}
}  // namespace android::chre