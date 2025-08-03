/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <android-base/file.h>
#include <gtest/gtest.h>

#include "CertUtils.h"
#include "VerityUtils.h"

// These files were created using the following commands:
// openssl genrsa -out SigningUtils.pem 4096
// openssl req -new -x509 -key SigningUtils.pem -out SigningUtils.cert.pem
// openssl x509 -in SigningUtils.cert.pem -out SigningUtils.cert.der -outform DER
// head -c 4096 </dev/urandom >test_file
// openssl dgst -sign SigningUtils.pem -keyform PEM -sha256 -out test_file.sig -binary test_file
const std::string kTestCert = "SigningUtils.cert.der";
const std::string kTestFile = "test_file";
const std::string kTestFileSignature = "test_file.sig";

TEST(SigningUtilsTest, CheckVerifySignature) {
    std::string signature;
    std::string sigFile = android::base::GetExecutableDirectory() + "/" + kTestFileSignature;
    ASSERT_TRUE(android::base::ReadFileToString(sigFile, &signature));

    std::string data;
    std::string testFile = android::base::GetExecutableDirectory() + "/" + kTestFile;
    ASSERT_TRUE(android::base::ReadFileToString(testFile, &data));

    std::string testCert = android::base::GetExecutableDirectory() + "/" + kTestCert;
    auto trustedKey = extractPublicKeyFromX509(testCert.c_str());
    ASSERT_TRUE(trustedKey.ok());

    auto result = verifySignature(data, signature, *trustedKey);
    ASSERT_TRUE(result.ok());
}
