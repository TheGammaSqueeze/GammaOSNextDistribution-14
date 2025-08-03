/*
 * Copyright 2020 The Android Open Source Project
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

#include "hci/acl_manager/acl_connection.h"

namespace bluetooth {
namespace hci {
namespace acl_manager {

AclConnection::QueueUpEnd* AclConnection::GetAclQueueEnd() const {
  return queue_up_end_;
}

}  // namespace acl_manager
}  // namespace hci
}  // namespace bluetooth
