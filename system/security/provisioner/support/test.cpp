/*
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

#include <aidl/Gtest.h>
#include <aidl/Vintf.h>
#include <android/hardware/security/keymint/IRemotelyProvisionedComponent.h>
#include <binder/IServiceManager.h>
#include <binder/ProcessState.h>
#include <gtest/gtest.h>
#include <rkp/support/rkpd_client.h>

using ::android::getAidlHalInstanceNames;
using ::android::sp;
using ::android::String16;
using ::android::hardware::security::keymint::IRemotelyProvisionedComponent;
using ::android::security::rkp::RemotelyProvisionedKey;
using ::android::security::rkp::support::getRpcKey;

// TODO(b/272600606): Add tests for error cases
class RkpdClientTest : public testing::TestWithParam<std::string> {
  public:
    virtual void SetUp() override {
        auto rpcName = String16(GetParam().c_str());
        String16 avfName = String16(IRemotelyProvisionedComponent::descriptor) + String16("/avf");
        if (avfName == rpcName) {
            GTEST_SKIP() << "Skipping test for avf";
        }
        rpc_ = android::waitForService<IRemotelyProvisionedComponent>(rpcName);
        ASSERT_NE(rpc_, nullptr);
    }

    sp<IRemotelyProvisionedComponent> rpc_;
};

TEST_P(RkpdClientTest, getRpcKey) {
    std::optional<RemotelyProvisionedKey> key = getRpcKey(rpc_, /*keyId=*/0);

    ASSERT_TRUE(key.has_value()) << "Failed to get remotely provisioned attestation key";
    ASSERT_FALSE(key->keyBlob.empty()) << "Key blob is empty";
    ASSERT_FALSE(key->encodedCertChain.empty()) << "Certificate is empty";
}

INSTANTIATE_TEST_SUITE_P(
    PerInstance, RkpdClientTest,
    testing::ValuesIn(getAidlHalInstanceNames(IRemotelyProvisionedComponent::descriptor)),
    ::android::PrintInstanceNameToString);

int main(int argc, char** argv) {
    testing::InitGoogleTest(&argc, argv);

    // We need one thread to issue requests to RKPD and one to handle
    // asynchronous responses from RKPD.
    android::ProcessState::self()->setThreadPoolMaxThreadCount(2);
    android::ProcessState::self()->startThreadPool();
    return RUN_ALL_TESTS();
}
