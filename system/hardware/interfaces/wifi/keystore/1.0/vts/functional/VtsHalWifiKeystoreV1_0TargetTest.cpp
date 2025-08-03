/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include <aidl/android/system/keystore2/IKeystoreOperation.h>
#include <aidl/android/system/keystore2/IKeystoreSecurityLevel.h>
#include <aidl/android/system/keystore2/IKeystoreService.h>
#include <aidl/android/system/keystore2/ResponseCode.h>
#include <android/binder_manager.h>
#include <android/system/wifi/keystore/1.0/IKeystore.h>
#include <binder/IServiceManager.h>
#include <cutils/properties.h>
#include <gtest/gtest.h>
#include <hidl/GtestPrinter.h>
#include <hidl/ServiceManagement.h>
#include <keymint_support/authorization_set.h>
#include <utils/String16.h>

using namespace std;
using namespace ::testing;
using namespace android;
using android::system::wifi::keystore::V1_0::IKeystore;

namespace keymint = ::aidl::android::hardware::security::keymint;
namespace ks2 = ::aidl::android::system::keystore2;

int main(int argc, char** argv) {
    InitGoogleTest(&argc, argv);
    int status = RUN_ALL_TESTS();
    return status;
}

namespace {

enum KeyPurpose {
    ENCRYPTION,
    SIGNING,
};

// The fixture for testing the Wifi Keystore HAL
class WifiKeystoreHalTest : public TestWithParam<std::string> {
   protected:
    void SetUp() override {
        keystore = IKeystore::getService(GetParam());
        ASSERT_TRUE(keystore);

        ::ndk::SpAIBinder ks2Binder(AServiceManager_getService(kKeystoreServiceName));
        ks2Service = ks2::IKeystoreService::fromBinder(ks2Binder);

        ASSERT_TRUE(ks2Service);
        resetState();
    }

    void TearDown() override { resetState(); }

    bool isDebuggableBuild() {
        char value[PROPERTY_VALUE_MAX] = {0};
        property_get("ro.system.build.type", value, "");
        if (strcmp(value, "userdebug") == 0) {
            return true;
        }
        if (strcmp(value, "eng") == 0) {
            return true;
        }
        return false;
    }

    /**
     * Resets the relevant state of the system between tests
     */
    void resetState() {
        deleteKey(kTestKeyName, true);
        deleteKey(kTestKeyName, false);
    }

    ks2::KeyDescriptor keyDescriptor(const std::string& alias, bool useWifiNamespace) {
        if (useWifiNamespace) {
            return {
                .domain = ks2::Domain::SELINUX,
                .nspace = 102,  // Namespace Wifi
                .alias = alias,
                .blob = {},
            };
        } else {
            return {
                .domain = ks2::Domain::APP,
                .nspace = -1,  // ignored - should be -1.
                .alias = alias,
                .blob = {},
            };
        }
    }

    /**
     * Delete a key if it exists.
     *
     * @param keyName: name of the key to delete
     * @param useWifiNamespace: delete the key from the wifi namespace
     *        instead of the process' namespace. (Requires special
     *        privileges on the test's part)
     *
     * @return true iff the key existed and is now deleted, false otherwise.
     */
    bool deleteKey(std::string keyName, bool useWifiNamespace) {
        String16 keyName16(keyName.data(), keyName.size());
        auto rc = ks2Service->deleteKey(keyDescriptor(keyName, useWifiNamespace));
        if (!rc.isOk() &&
            rc.getServiceSpecificError() != int32_t(ks2::ResponseCode::KEY_NOT_FOUND)) {
            cout << "deleteKey: failed binder call" << rc.getDescription() << endl;
            return false;
        }

        return true;
    }

    /**
     * Generate a key for a specific purpose.
     *
     * This generates a key which can be used either for signing
     * or encryption. The signing key is setup to be used in
     * the Wifi Keystore HAL's sign() call. The data
     * about the key returning from its generation is discarded.
     * If this returns 'true' the key generation has completed
     * and the key is ready for use.
     *
     * @param keyName: name of the key to generate
     * @param purpose: the purpose the generated key will support
     * @param useWifiNamespace: generate the key in the wifi namespace
     *        instead of the process' namespace. (Requires special
     *        privileges on the test's part)
     *
     * @return true iff the key was successfully generated and is
     * ready for use, false otherwise.
     */
    bool generateKey(std::string keyName, KeyPurpose purpose, bool useWifiNamespace) {
        constexpr uint32_t kAESKeySize = 256;

        vector<uint8_t> entropy;
        keymint::AuthorizationSetBuilder key_parameters;
        if (purpose == KeyPurpose::SIGNING) {
            key_parameters.EcdsaSigningKey(keymint::EcCurve::P_256);
        }

        if (purpose == KeyPurpose::ENCRYPTION) {
            key_parameters.AesEncryptionKey(kAESKeySize);
        }

        key_parameters.NoDigestOrPadding()
            .Authorization(keymint::TAG_BLOCK_MODE, keymint::BlockMode::CBC)
            .Authorization(keymint::TAG_NO_AUTH_REQUIRED);

        std::shared_ptr<ks2::IKeystoreSecurityLevel> securityLevel;

        fflush(stdout);

        auto rc = ks2Service->getSecurityLevel(keymint::SecurityLevel::TRUSTED_ENVIRONMENT,
                                               &securityLevel);
        if (!rc.isOk()) {
            cout << "generateKey: Failed to get security level: " << rc.getDescription() << endl;
            return false;
        }

        ks2::KeyMetadata keyMetadata;

        rc = securityLevel->generateKey(keyDescriptor(keyName, useWifiNamespace),
                                        {} /* attestation key */, key_parameters.vector_data(),
                                        0 /* flags */, entropy, &keyMetadata);
        if (!rc.isOk()) {
            cout << "generateKey: Failed to generate key: " << rc.getDescription() << endl;
            return false;
        }

        return true;
    }

    constexpr static const char kKeystoreServiceName[] =
        "android.system.keystore2.IKeystoreService/default";
    constexpr static const char kTestKeyName[] = "TestKeyName";

    sp<IKeystore> keystore;
    std::shared_ptr<ks2::IKeystoreService> ks2Service;
};

TEST_P(WifiKeystoreHalTest, Sign_nullptr_key_name) {
    IKeystore::KeystoreStatusCode statusCode;

    auto callback = [&statusCode](IKeystore::KeystoreStatusCode status,
                                  const ::android::hardware::hidl_vec<uint8_t>& /*value*/) {
        statusCode = status;
        return;
    };

    ::android::hardware::hidl_vec<uint8_t> dataToSign;
    dataToSign.resize(100);
    keystore->sign(nullptr, dataToSign, callback);
    EXPECT_EQ(IKeystore::KeystoreStatusCode::ERROR_UNKNOWN, statusCode);
}

TEST_P(WifiKeystoreHalTest, Sign_empty_key_name) {
    IKeystore::KeystoreStatusCode statusCode;

    auto callback = [&statusCode](IKeystore::KeystoreStatusCode status,
                                  const ::android::hardware::hidl_vec<uint8_t>& /*value*/) {
        statusCode = status;
        return;
    };

    ::android::hardware::hidl_vec<uint8_t> dataToSign;
    dataToSign.resize(100);
    keystore->sign("", dataToSign, callback);
    EXPECT_EQ(IKeystore::KeystoreStatusCode::ERROR_UNKNOWN, statusCode);
}

TEST_P(WifiKeystoreHalTest, Sign_empty_data) {
    if (!isDebuggableBuild()) {
        GTEST_SKIP() << "Device not running a debuggable build, cannot make test keys";
    }

    bool callbackInvoked = false;

    auto callback = [&callbackInvoked](IKeystore::KeystoreStatusCode /*status*/,
                                       const ::android::hardware::hidl_vec<uint8_t>& /*value*/) {
        // The result is ignored; this callback is a no-op.
        callbackInvoked = true;
        return;
    };

    bool result = generateKey(kTestKeyName, KeyPurpose::SIGNING, true);
    EXPECT_EQ(result, true);

    // The data to sign is empty. The return code is not important, and the attempt could be
    // interpreted as valid or an error case. The goal is to determine that the callback
    // was invokved.
    ::android::hardware::hidl_vec<uint8_t> dataToSign;
    keystore->sign(kTestKeyName, dataToSign, callback);
    EXPECT_EQ(true, callbackInvoked);
}

TEST_P(WifiKeystoreHalTest, Sign_wrong_key_purpose) {
    if (!isDebuggableBuild()) {
        GTEST_SKIP() << "Device not running a debuggable build, cannot make test keys";
    }

    IKeystore::KeystoreStatusCode statusCode;

    auto callback = [&statusCode](IKeystore::KeystoreStatusCode status,
                                  const ::android::hardware::hidl_vec<uint8_t>& /*value*/) {
        statusCode = status;
        return;
    };

    // Create a key which cannot sign; any signing attempt should fail.
    bool result = generateKey(kTestKeyName, KeyPurpose::ENCRYPTION, true);
    EXPECT_EQ(result, true);

    ::android::hardware::hidl_vec<uint8_t> dataToSign;
    dataToSign.resize(100);
    keystore->sign(kTestKeyName, dataToSign, callback);
    EXPECT_EQ(IKeystore::KeystoreStatusCode::ERROR_UNKNOWN, statusCode);
}

TEST_P(WifiKeystoreHalTest, Sign_success) {
    if (!isDebuggableBuild()) {
        GTEST_SKIP() << "Device not running a debuggable build, cannot make test keys";
    }

    IKeystore::KeystoreStatusCode statusCode;

    auto callback = [&statusCode](IKeystore::KeystoreStatusCode status,
                                  const ::android::hardware::hidl_vec<uint8_t>& /*value*/) {
        statusCode = status;
        return;
    };

    ::android::hardware::hidl_vec<uint8_t> dataToSign;

    bool result = generateKey(kTestKeyName, KeyPurpose::SIGNING, true);
    EXPECT_EQ(result, true);

    // With data the signing attempt should succeed

    dataToSign.resize(100);
    keystore->sign(kTestKeyName, dataToSign, callback);
    EXPECT_EQ(IKeystore::KeystoreStatusCode::SUCCESS, statusCode);

    result = deleteKey(kTestKeyName, true);
    EXPECT_EQ(result, true);
}

TEST_P(WifiKeystoreHalTest, GetBlob_null_key_name) {
    IKeystore::KeystoreStatusCode statusCode;

    auto callback = [&statusCode](IKeystore::KeystoreStatusCode status,
                                  const ::android::hardware::hidl_vec<uint8_t>& /*value*/) {
        statusCode = status;
        return;
    };

    // Attempting to get a blob on a non-existent key should fail.
    statusCode = IKeystore::KeystoreStatusCode::SUCCESS;
    keystore->getBlob(nullptr, callback);
    EXPECT_EQ(IKeystore::KeystoreStatusCode::ERROR_UNKNOWN, statusCode);
}

TEST_P(WifiKeystoreHalTest, GetBlob_empty_key_name) {
    IKeystore::KeystoreStatusCode statusCode;

    auto callback = [&statusCode](IKeystore::KeystoreStatusCode status,
                                  const ::android::hardware::hidl_vec<uint8_t>& /*value*/) {
        statusCode = status;
        return;
    };

    // Attempting to get a blob on a non-existent key should fail.
    statusCode = IKeystore::KeystoreStatusCode::SUCCESS;
    keystore->getBlob("", callback);
    EXPECT_EQ(IKeystore::KeystoreStatusCode::ERROR_UNKNOWN, statusCode);
}

TEST_P(WifiKeystoreHalTest, GetBlob_missing_key) {
    IKeystore::KeystoreStatusCode statusCode;

    auto callback = [&statusCode](IKeystore::KeystoreStatusCode status,
                                  const ::android::hardware::hidl_vec<uint8_t>& /*value*/) {
        statusCode = status;
        return;
    };

    // Attempting to get a blob on a non-existent key should fail.
    statusCode = IKeystore::KeystoreStatusCode::SUCCESS;
    keystore->getBlob(kTestKeyName, callback);
    EXPECT_EQ(IKeystore::KeystoreStatusCode::ERROR_UNKNOWN, statusCode);
}

TEST_P(WifiKeystoreHalTest, GetBlob_wrong_user) {
    if (!isDebuggableBuild()) {
        GTEST_SKIP() << "Device not running a debuggable build, cannot make test keys";
    }

    IKeystore::KeystoreStatusCode statusCode;

    auto callback = [&statusCode](IKeystore::KeystoreStatusCode status,
                                  const ::android::hardware::hidl_vec<uint8_t>& /*value*/) {
        statusCode = status;
        return;
    };

    // The HAL is expecting the key to belong to the wifi user.
    // If the key belongs to another user's space it should fail.
    bool result = generateKey(kTestKeyName, KeyPurpose::SIGNING, false);
    EXPECT_EQ(result, true);

    keystore->getBlob(std::string("USRCERT_") + kTestKeyName, callback);
    EXPECT_EQ(IKeystore::KeystoreStatusCode::ERROR_UNKNOWN, statusCode);

    result = deleteKey(kTestKeyName, false);
    EXPECT_EQ(result, true);
}

TEST_P(WifiKeystoreHalTest, GetBlob_success) {
    if (!isDebuggableBuild()) {
        GTEST_SKIP() << "Device not running a debuggable build, cannot make test keys";
    }

    IKeystore::KeystoreStatusCode statusCode;

    std::string cert;
    auto callback = [&statusCode, &cert](IKeystore::KeystoreStatusCode status,
                                         const ::android::hardware::hidl_vec<uint8_t>& value) {
        statusCode = status;
        cert = std::string(reinterpret_cast<const char*>(value.data()),
                           reinterpret_cast<const char*>(value.data()) + value.size());
        return;
    };

    // Accessing the key belonging to the wifi user should succeed.

    bool result = generateKey(kTestKeyName, KeyPurpose::SIGNING, true);
    EXPECT_EQ(result, true);

    keystore->getBlob(std::string("USRCERT_") + kTestKeyName, callback);
    EXPECT_EQ(IKeystore::KeystoreStatusCode::SUCCESS, statusCode);
    // Must return PEM encoded certificates.
    EXPECT_EQ(cert.rfind("-----BEGIN CERTIFICATE-----", 0), 0);

    result = deleteKey(kTestKeyName, true);
    EXPECT_EQ(result, true);
}

TEST_P(WifiKeystoreHalTest, GetPublicKey_nullptr_key_name) {
    IKeystore::KeystoreStatusCode statusCode;

    auto callback = [&statusCode](IKeystore::KeystoreStatusCode status,
                                  const ::android::hardware::hidl_vec<uint8_t>& /*value*/) {
        statusCode = status;
        return;
    };

    // Attempting to export a non-existent key should fail.
    statusCode = IKeystore::KeystoreStatusCode::SUCCESS;
    keystore->getPublicKey(nullptr, callback);
    EXPECT_EQ(IKeystore::KeystoreStatusCode::ERROR_UNKNOWN, statusCode);
}

TEST_P(WifiKeystoreHalTest, GetPublicKey_empty_key_name) {
    IKeystore::KeystoreStatusCode statusCode;

    auto callback = [&statusCode](IKeystore::KeystoreStatusCode status,
                                  const ::android::hardware::hidl_vec<uint8_t>& /*value*/) {
        statusCode = status;
        return;
    };

    // Attempting to export a non-existent key should fail.
    statusCode = IKeystore::KeystoreStatusCode::SUCCESS;
    keystore->getPublicKey("", callback);
    EXPECT_EQ(IKeystore::KeystoreStatusCode::ERROR_UNKNOWN, statusCode);
}

TEST_P(WifiKeystoreHalTest, GetPublicKey_wrong_key_name) {
    IKeystore::KeystoreStatusCode statusCode;

    auto callback = [&statusCode](IKeystore::KeystoreStatusCode status,
                                  const ::android::hardware::hidl_vec<uint8_t>& /*value*/) {
        statusCode = status;
        return;
    };

    // Attempting to export a non-existent key should fail.
    statusCode = IKeystore::KeystoreStatusCode::SUCCESS;
    keystore->getPublicKey(kTestKeyName, callback);
    EXPECT_EQ(IKeystore::KeystoreStatusCode::ERROR_UNKNOWN, statusCode);
}

TEST_P(WifiKeystoreHalTest, GetPublicKey_wrong_user) {
    if (!isDebuggableBuild()) {
        GTEST_SKIP() << "Device not running a debuggable build, cannot make test keys";
    }

    IKeystore::KeystoreStatusCode statusCode;

    auto callback = [&statusCode](IKeystore::KeystoreStatusCode status,
                                  const ::android::hardware::hidl_vec<uint8_t>& /*value*/) {
        statusCode = status;
        return;
    };

    // The HAL is expecting the key to belong to the wifi user.
    // If the key belongs to another user's space (e.g. root) it should
    // not be accessible and should fail.

    bool result = generateKey(kTestKeyName, KeyPurpose::SIGNING, false);
    EXPECT_EQ(result, true);

    keystore->getPublicKey(kTestKeyName, callback);
    EXPECT_EQ(IKeystore::KeystoreStatusCode::ERROR_UNKNOWN, statusCode);

    result = deleteKey(kTestKeyName, false);
    EXPECT_EQ(result, true);
}

TEST_P(WifiKeystoreHalTest, GetPublicKey_success) {
    if (!isDebuggableBuild()) {
        GTEST_SKIP() << "Device not running a debuggable build, cannot make test keys";
    }

    IKeystore::KeystoreStatusCode statusCode;

    auto callback = [&statusCode](IKeystore::KeystoreStatusCode status,
                                  const ::android::hardware::hidl_vec<uint8_t>& /*value*/) {
        statusCode = status;
        return;
    };

    // Accessing the key belonging to the wifi uid should succeed.

    bool result = generateKey(kTestKeyName, KeyPurpose::SIGNING, true);
    EXPECT_EQ(result, true);

    keystore->getPublicKey(kTestKeyName, callback);
    EXPECT_EQ(IKeystore::KeystoreStatusCode::SUCCESS, statusCode);

    result = deleteKey(kTestKeyName, true);
    EXPECT_EQ(result, true);
}

GTEST_ALLOW_UNINSTANTIATED_PARAMETERIZED_TEST(WifiKeystoreHalTest);
INSTANTIATE_TEST_SUITE_P(
    PerInstance, WifiKeystoreHalTest,
    testing::ValuesIn(android::hardware::getAllHalInstanceNames(IKeystore::descriptor)),
    android::hardware::PrintInstanceNameToString);

}  // namespace
