/**
 * Copyright (C) 2023 The Android Open Source Project
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

#include <RtpHeaderExtension.h>
#include <gtest/gtest.h>

using namespace android::telephony::imsmedia;

const int32_t kIdentifier = 15;
const uint8_t kExtensionData[] = {0x01, 0x02};
const int32_t kExtensionDataSize = 2;

class RtpHeaderExtensionTest : public ::testing::Test
{
public:
    RtpHeaderExtensionTest() { extension = nullptr; }
    virtual ~RtpHeaderExtensionTest() {}

protected:
    RtpHeaderExtension* extension;

    virtual void SetUp() override
    {
        extension = new RtpHeaderExtension();
        extension->setExtensionData(kExtensionData, kExtensionDataSize);
        extension->setLocalIdentifier(kIdentifier);
        extension->setExtensionDataSize(kExtensionDataSize);
    }

    virtual void TearDown() override { delete extension; }
};

TEST_F(RtpHeaderExtensionTest, TestGetterSetter)
{
    EXPECT_EQ(memcmp(extension->getExtensionData(), kExtensionData, kExtensionDataSize), 0);
    EXPECT_EQ(extension->getLocalIdentifier(), kIdentifier);
    EXPECT_EQ(extension->getExtensionDataSize(), kExtensionDataSize);
}

TEST_F(RtpHeaderExtensionTest, TestParcel)
{
    android::Parcel parcel;
    extension->writeToParcel(&parcel);
    parcel.setDataPosition(0);

    RtpHeaderExtension* extension2 = new RtpHeaderExtension();
    extension2->readFromParcel(&parcel);
    EXPECT_EQ(*extension2, *extension);
    delete extension2;
}

TEST_F(RtpHeaderExtensionTest, TestAssign)
{
    RtpHeaderExtension extension2;
    extension2 = *extension;
    EXPECT_EQ(*extension, extension2);
}

TEST_F(RtpHeaderExtensionTest, TestEqual)
{
    RtpHeaderExtension* extension2 = new RtpHeaderExtension();
    extension2->setExtensionData(kExtensionData, kExtensionDataSize);
    extension2->setLocalIdentifier(kIdentifier);
    extension2->setExtensionDataSize(kExtensionDataSize);
    EXPECT_EQ(*extension, *extension2);
    delete extension2;
}

TEST_F(RtpHeaderExtensionTest, TestNotEqual)
{
    RtpHeaderExtension* extension2 = new RtpHeaderExtension();
    const uint8_t data[] = {0x03, 0x04};
    extension2->setExtensionData(data, 2);
    extension2->setLocalIdentifier(kIdentifier);
    extension2->setExtensionDataSize(kExtensionDataSize);

    RtpHeaderExtension* extension3 = new RtpHeaderExtension();
    extension3->setExtensionData(kExtensionData, kExtensionDataSize);
    extension3->setLocalIdentifier(9999);
    extension3->setExtensionDataSize(kExtensionDataSize);

    EXPECT_NE(*extension, *extension2);
    EXPECT_NE(*extension, *extension3);

    delete extension2;
    delete extension3;
}