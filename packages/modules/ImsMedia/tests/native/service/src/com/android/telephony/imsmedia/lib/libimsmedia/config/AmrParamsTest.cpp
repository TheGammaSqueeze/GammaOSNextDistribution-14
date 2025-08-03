/**
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

#include <AmrParams.h>
#include <gtest/gtest.h>

using namespace android::telephony::imsmedia;

const int32_t kAmrMode = 8;
const bool kOctetAligned = false;
const int32_t kMaxRedundancyMillis = 240;

TEST(AmrParamsTest, TestGetterSetter)
{
    AmrParams* param = new AmrParams();
    param->setAmrMode(kAmrMode);
    param->setOctetAligned(kOctetAligned);
    param->setMaxRedundancyMillis(kMaxRedundancyMillis);
    EXPECT_EQ(param->getAmrMode(), kAmrMode);
    EXPECT_EQ(param->getOctetAligned(), kOctetAligned);
    EXPECT_EQ(param->getMaxRedundancyMillis(), kMaxRedundancyMillis);
    delete param;
}

TEST(AmrParamsTest, TestParcel)
{
    AmrParams* param = new AmrParams();
    param->setAmrMode(kAmrMode);
    param->setOctetAligned(kOctetAligned);
    param->setMaxRedundancyMillis(kMaxRedundancyMillis);

    android::Parcel parcel;
    param->writeToParcel(&parcel);
    parcel.setDataPosition(0);

    AmrParams* param2 = new AmrParams();
    param2->readFromParcel(&parcel);
    EXPECT_EQ(*param, *param2);
    delete param;
    delete param2;
}

TEST(AmrParamsTest, TestAssign)
{
    AmrParams param;
    param.setAmrMode(kAmrMode);
    param.setOctetAligned(kOctetAligned);
    param.setMaxRedundancyMillis(kMaxRedundancyMillis);

    AmrParams param2;
    param2 = param;
    EXPECT_EQ(param, param2);
}

TEST(AmrParamsTest, TestEqual)
{
    AmrParams* param = new AmrParams();
    param->setAmrMode(kAmrMode);
    param->setOctetAligned(kOctetAligned);
    param->setMaxRedundancyMillis(kMaxRedundancyMillis);

    AmrParams* param2 = new AmrParams();
    param2->setAmrMode(kAmrMode);
    param2->setOctetAligned(kOctetAligned);
    param2->setMaxRedundancyMillis(kMaxRedundancyMillis);
    EXPECT_EQ(*param, *param2);
    delete param;
    delete param2;
}

TEST(AmrParamsTest, TestNotEqual)
{
    AmrParams* param = new AmrParams();
    param->setAmrMode(kAmrMode);
    param->setOctetAligned(kOctetAligned);
    param->setMaxRedundancyMillis(kMaxRedundancyMillis);

    AmrParams* param2 = new AmrParams();
    param2->setAmrMode(2);
    param2->setOctetAligned(kOctetAligned);
    param2->setMaxRedundancyMillis(kMaxRedundancyMillis);

    AmrParams* param3 = new AmrParams();
    param3->setAmrMode(kAmrMode);
    param3->setOctetAligned(true);
    param3->setMaxRedundancyMillis(kMaxRedundancyMillis);

    EXPECT_NE(*param, *param2);
    EXPECT_NE(*param, *param2);
    EXPECT_NE(*param, *param3);
    delete param;
    delete param2;
    delete param3;
}