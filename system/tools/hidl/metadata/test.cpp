/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <hidl/metadata.h>

#include <optional>

using ::android::HidlInterfaceMetadata;
using ::testing::ElementsAre;

static std::optional<HidlInterfaceMetadata> metadataForModule(const std::string& name) {
    for (const HidlInterfaceMetadata& info : HidlInterfaceMetadata::all()) {
        if (name == info.name) return info;
    }
    return std::nullopt;
}

TEST(AidlMetadata, HasTestInstances) {
    const auto& info = metadataForModule("android.hardware.tests.bar@1.0::IBar");
    ASSERT_NE(info, std::nullopt);
    EXPECT_THAT(info->inherited, ElementsAre("android.hardware.tests.foo@1.0::IFoo"));
}

TEST(AidlMetadata, HasPrebuiltInstances) {
    for (const std::string& iface : {"hidl.metadata.test@1.0::IBar", "hidl.metadata.test@1.0::IBaz",
                                     "hidl.metadata.test@1.0::IFoo"}) {
        const auto& info = metadataForModule(iface);
        ASSERT_NE(info, std::nullopt) << iface;
    }
}
