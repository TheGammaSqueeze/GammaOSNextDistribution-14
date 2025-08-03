/*
 * Copyright 2016 The Android Open Source Project
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

#ifndef AVB_MANAGER_H_
#define AVB_MANAGER_H_

#include <stdio.h>

#include <UniquePtr.h>

#include "avb_messages.h"
#include "secure_storage_interface.h"

#define TLOG_TAG "avb"
#include <trusty_log.h>

extern const unsigned int kRollbackSlotMax;

namespace avb {

// Implements request callbacks
class AvbManager {
public:
    // AvbManager takes ownership of |storage|, so |storage| will be deleted
    // when AvbManager is destructed.
    AvbManager(SecureStorageInterface* storage) : storage_(storage) {}

    void ReadRollbackIndex(const RollbackIndexRequest& request,
                           RollbackIndexResponse* response);
    void WriteRollbackIndex(const RollbackIndexRequest& request,
                            RollbackIndexResponse* response);
    // Client is responsible for managing versioning, by sending an initial
    // "GetVersion" request. Note this means that the "GetVersion" request
    // cannot be versioned.
    void GetVersion(const GetVersionRequest& request,
                    GetVersionResponse* response);
    // The Avb service provides storage for Android Things permanent attributes
    // structure, but these must still be verified against write-once fuses.
    void ReadPermanentAttributes(const ReadPermanentAttributesRequest& request,
                                 ReadPermanentAttributesResponse* response);
    void WritePermanentAttributes(
            const WritePermanentAttributesRequest& request,
            WritePermanentAttributesResponse* response);
    void ReadLockState(const ReadLockStateRequest& request,
                       ReadLockStateResponse* response);
    void WriteLockState(const WriteLockStateRequest& request,
                        WriteLockStateResponse* response);

private:
    int DeleteRollbackIndexFiles();

    UniquePtr<SecureStorageInterface> storage_;
};

}  // namespace avb

#endif  // AVB_MANAGER_H_
