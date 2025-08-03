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

#include <trusty_unittest.h>

#include "secure_storage_fake.h"

namespace avb {

TEST(AvbTest, BasicReadTest) {
    uint32_t slot = 0;
    uint64_t value = 0;
    RollbackIndexRequest request(slot, value);
    RollbackIndexResponse response;
    AvbManager avb_manager(new SecureStorageFake);
    avb_manager.ReadRollbackIndex(request, &response);
    EXPECT_EQ(AvbError::kNone, response.get_error(), "Read failed");
    EXPECT_EQ(1, response.get_value(), "Did not read expected value");
}

TEST(AvbTest, ValidWriteTest) {
    uint32_t slot = 0;
    uint64_t value = 2;  // Read always returns 1
    RollbackIndexRequest request(slot, value);
    RollbackIndexResponse response;
    AvbManager avb_manager(new SecureStorageFake);
    avb_manager.WriteRollbackIndex(request, &response);
    EXPECT_EQ(AvbError::kNone, response.get_error(), "Write failed");
    EXPECT_EQ(2, response.get_value(), "Did not write expected value");
}

TEST(AvbTest, InvalidWriteTest) {
    uint32_t slot = 0;
    uint64_t value = 0;  // Read always returns 1
    RollbackIndexRequest request(slot, value);
    RollbackIndexResponse response;
    AvbManager avb_manager(new SecureStorageFake);
    avb_manager.WriteRollbackIndex(request, &response);
    EXPECT_EQ(AvbError::kInvalid, response.get_error(),
              "Allowed writing index value less than existing value");
    EXPECT_EQ(1, response.get_value(),
              "Did not read expected value after failed write");
}

TEST(AvbTest, SlotUpperBitsSetTest) {
    uint32_t slot = 0x00010000;
    uint64_t value = 0;
    RollbackIndexRequest request(slot, value);
    RollbackIndexResponse response;
    AvbManager avb_manager(new SecureStorageFake);
    avb_manager.ReadRollbackIndex(request, &response);
    EXPECT_EQ(AvbError::kInvalid, response.get_error(),
              "Slot was not rejected");
}

TEST(AvbTest, SlotMaxValueTest) {
    uint32_t slot = kRollbackSlotMax + 1;
    uint64_t value = 0;
    RollbackIndexRequest request(slot, value);
    RollbackIndexResponse response;
    AvbManager avb_manager(new SecureStorageFake);
    avb_manager.ReadRollbackIndex(request, &response);
    EXPECT_EQ(AvbError::kInvalid, response.get_error(),
              "Failed to reject write to slot > max slot");
}

TEST(AvbTest, SlotFlagTest) {
    uint32_t slot = 0x0000f000;
    uint64_t value = 0;
    RollbackIndexRequest request(slot, value);
    RollbackIndexResponse response;
    AvbManager avb_manager(new SecureStorageFake);
    avb_manager.ReadRollbackIndex(request, &response);
    EXPECT_EQ(AvbError::kNone, response.get_error(),
              "Could not validate 0xf flag");
}

}  // namespace avb

int main() {
    return RUN_ALL_TESTS() ? 0 : 1;
}
