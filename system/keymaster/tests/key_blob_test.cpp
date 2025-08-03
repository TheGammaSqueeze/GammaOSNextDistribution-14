/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include <algorithm>
#include <utility>

#include <gtest/gtest.h>

#include <openssl/engine.h>
#include <openssl/rand.h>

#include <android-base/logging.h>

#include <keymaster/android_keymaster_utils.h>
#include <keymaster/authorization_set.h>
#include <keymaster/key_blob_utils/auth_encrypted_key_blob.h>
#include <keymaster/key_blob_utils/integrity_assured_key_blob.h>
#include <keymaster/keymaster_tags.h>
#include <keymaster/km_openssl/software_random_source.h>

#include "android_keymaster_test_utils.h"

namespace keymaster::test {

namespace {

const uint8_t master_key_data[16] = {};
const uint8_t key_data[5] = {21, 22, 23, 24, 25};

}  // namespace

class KeyBlobTest : public ::testing::TestWithParam<AuthEncryptedBlobFormat>,
                    public SoftwareRandomSource {
  protected:
    KeyBlobTest()
        : key_material_(key_data, array_length(key_data)),
          master_key_(master_key_data, array_length(master_key_data)),
          secure_deletion_data_(SecureDeletionData()) {
        hw_enforced_.push_back(TAG_ALGORITHM, KM_ALGORITHM_RSA);
        hw_enforced_.push_back(TAG_KEY_SIZE, 256);
        hw_enforced_.push_back(TAG_BLOB_USAGE_REQUIREMENTS, KM_BLOB_STANDALONE);
        hw_enforced_.push_back(TAG_MIN_SECONDS_BETWEEN_OPS, 10);
        hw_enforced_.push_back(TAG_ALL_USERS);
        hw_enforced_.push_back(TAG_NO_AUTH_REQUIRED);
        hw_enforced_.push_back(TAG_ORIGIN, KM_ORIGIN_GENERATED);

        sw_enforced_.push_back(TAG_ACTIVE_DATETIME, 10);
        sw_enforced_.push_back(TAG_ORIGINATION_EXPIRE_DATETIME, 100);
        sw_enforced_.push_back(TAG_CREATION_DATETIME, 10);

        secure_deletion_data_.factory_reset_secret.Reinitialize("Factory reset secret",
                                                                sizeof("Factory reset secret"));
        secure_deletion_data_.secure_deletion_secret.Reinitialize("Secure deletion secret",
                                                                  sizeof("Secure deletion secret"));

        hidden_.push_back(TAG_ROOT_OF_TRUST, "foo", 3);
        hidden_.push_back(TAG_APPLICATION_ID, "my_app", 6);
    }

    keymaster_error_t Encrypt(AuthEncryptedBlobFormat format) {
        auto result = EncryptKey(key_material_, format, hw_enforced_, sw_enforced_, hidden_,
                                 secure_deletion_data_, master_key_, *this);
        if (!result) return result.error();
        encrypted_key_ = std::move(*result);
        return KM_ERROR_OK;
    }

    keymaster_error_t Decrypt() {
        auto result =
            DecryptKey(std::move(deserialized_key_), hidden_, secure_deletion_data_, master_key_);
        if (!result) return result.error();
        decrypted_plaintext_ = std::move(*result);
        return KM_ERROR_OK;
    }

    keymaster_error_t Serialize(uint32_t secure_deletion_key_slot = 0) {
        auto result = SerializeAuthEncryptedBlob(encrypted_key_, hw_enforced_, sw_enforced_,
                                                 secure_deletion_key_slot);
        if (!result) return result.error();
        serialized_blob_ = std::move(*result);
        return KM_ERROR_OK;
    }

    keymaster_error_t Deserialize() {
        auto result = DeserializeAuthEncryptedBlob(serialized_blob_);
        if (!result) return result.error();
        deserialized_key_ = std::move(*result);
        return KM_ERROR_OK;
    }

    // Encryption inputs
    AuthorizationSet hw_enforced_;
    AuthorizationSet sw_enforced_;
    AuthorizationSet hidden_;
    KeymasterKeyBlob key_material_;
    KeymasterKeyBlob master_key_;
    SecureDeletionData secure_deletion_data_;

    // Encryption output
    EncryptedKey encrypted_key_;

    // Serialization output
    KeymasterKeyBlob serialized_blob_;

    // Deserialization output
    DeserializedKey deserialized_key_;

    // Decryption output.
    KeymasterKeyBlob decrypted_plaintext_;
};

TEST_P(KeyBlobTest, EncryptDecrypt) {
    uint32_t key_slot = static_cast<uint32_t>(rand());

    ASSERT_EQ(KM_ERROR_OK, Encrypt(GetParam()));
    ASSERT_EQ(KM_ERROR_OK, Serialize(key_slot));

    // key_data shouldn't be anywhere in the blob, ciphertext should.
    EXPECT_EQ(serialized_blob_.end(), std::search(serialized_blob_.begin(), serialized_blob_.end(),
                                                  key_material_.begin(), key_material_.end()));
    EXPECT_NE(serialized_blob_.end(),
              std::search(serialized_blob_.begin(), serialized_blob_.end(),
                          encrypted_key_.ciphertext.begin(), encrypted_key_.ciphertext.end()));

    KmErrorOr<DeserializedKey> deserialized = DeserializeAuthEncryptedBlob(serialized_blob_);
    ASSERT_TRUE(deserialized.isOk());
    EXPECT_EQ(hw_enforced_, deserialized->hw_enforced);
    EXPECT_EQ(sw_enforced_, deserialized->sw_enforced);
    if (GetParam() == AES_GCM_WITH_SECURE_DELETION ||
        GetParam() == AES_GCM_WITH_SECURE_DELETION_VERSIONED) {
        EXPECT_EQ(key_slot, deserialized->key_slot);
    } else {
        EXPECT_EQ(0U, deserialized->key_slot);
    }

    KmErrorOr<KeymasterKeyBlob> plaintext =
        DecryptKey(*deserialized, hidden_, secure_deletion_data_, master_key_);
    ASSERT_TRUE(plaintext.isOk());
    EXPECT_TRUE(std::equal(key_material_.begin(), key_material_.end(),  //
                           plaintext->begin(), plaintext->end()));
}

TEST_P(KeyBlobTest, WrongKeyLength) {
    ASSERT_EQ(KM_ERROR_OK, Encrypt(GetParam()));
    ASSERT_EQ(KM_ERROR_OK, Serialize());

    // Modify the key length, shouldn't be able to parse.
    serialized_blob_.writable_data()[1 /* version */ + 4 /* nonce len */ + 12 /* nonce */ + 3]++;

    ASSERT_EQ(KM_ERROR_INVALID_KEY_BLOB, Deserialize());
}

TEST_P(KeyBlobTest, WrongNonce) {
    ASSERT_EQ(KM_ERROR_OK, Encrypt(GetParam()));
    ASSERT_EQ(KM_ERROR_OK, Serialize());

    // Find the nonce, then modify it.
    auto nonce_ptr = std::search(serialized_blob_.begin(), serialized_blob_.end(),
                                 encrypted_key_.nonce.begin(), encrypted_key_.nonce.end());
    ASSERT_NE(nonce_ptr, serialized_blob_.end());
    (*const_cast<uint8_t*>(nonce_ptr))++;

    // Deserialization shouldn't be affected, but decryption should fail.
    ASSERT_EQ(KM_ERROR_OK, Deserialize());
    ASSERT_EQ(KM_ERROR_INVALID_KEY_BLOB, Decrypt());
}

TEST_P(KeyBlobTest, WrongTag) {
    ASSERT_EQ(KM_ERROR_OK, Encrypt(GetParam()));
    ASSERT_EQ(KM_ERROR_OK, Serialize());

    // Find the tag, then modify it.
    auto tag_ptr = std::search(serialized_blob_.begin(), serialized_blob_.end(),
                               encrypted_key_.tag.begin(), encrypted_key_.tag.end());
    ASSERT_NE(tag_ptr, serialized_blob_.end());
    (*const_cast<uint8_t*>(tag_ptr))++;

    // Deserialization shouldn't be affected, but decryption should fail.
    ASSERT_EQ(KM_ERROR_OK, Deserialize());
    ASSERT_EQ(KM_ERROR_INVALID_KEY_BLOB, Decrypt());
}

TEST_P(KeyBlobTest, WrongCiphertext) {
    ASSERT_EQ(KM_ERROR_OK, Encrypt(GetParam()));
    ASSERT_EQ(KM_ERROR_OK, Serialize());

    // Find the ciphertext, then modify it.
    auto ciphertext_ptr =
        std::search(serialized_blob_.begin(), serialized_blob_.end(),
                    encrypted_key_.ciphertext.begin(), encrypted_key_.ciphertext.end());
    ASSERT_NE(ciphertext_ptr, serialized_blob_.end());
    (*const_cast<uint8_t*>(ciphertext_ptr))++;

    // Deserialization shouldn't be affected, but decryption should fail.
    ASSERT_EQ(KM_ERROR_OK, Deserialize());
    ASSERT_EQ(KM_ERROR_INVALID_KEY_BLOB, Decrypt());
}

TEST_P(KeyBlobTest, WrongMasterKey) {
    ASSERT_EQ(KM_ERROR_OK, Encrypt(GetParam()));
    ASSERT_EQ(KM_ERROR_OK, Serialize());

    uint8_t wrong_master_data[] = {1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    KeymasterKeyBlob wrong_master(wrong_master_data, array_length(wrong_master_data));

    // Decrypting with wrong master key should fail.
    ASSERT_EQ(KM_ERROR_OK, Deserialize());
    auto result = DecryptKey(deserialized_key_, hidden_, secure_deletion_data_, wrong_master);
    ASSERT_FALSE(result.isOk());
    ASSERT_EQ(KM_ERROR_INVALID_KEY_BLOB, result.error());
}

TEST_P(KeyBlobTest, WrongHwEnforced) {
    ASSERT_EQ(KM_ERROR_OK, Encrypt(GetParam()));
    ASSERT_EQ(KM_ERROR_OK, Serialize());

    // Find enforced serialization data and modify it.
    size_t hw_enforced_size = hw_enforced_.SerializedSize();
    UniquePtr<uint8_t[]> hw_enforced_data(new (std::nothrow) uint8_t[hw_enforced_size]);
    hw_enforced_.Serialize(hw_enforced_data.get(), hw_enforced_data.get() + hw_enforced_size);

    auto hw_enforced_ptr =
        std::search(serialized_blob_.begin(), serialized_blob_.end(), hw_enforced_data.get(),
                    hw_enforced_data.get() + hw_enforced_size);
    ASSERT_NE(serialized_blob_.end(), hw_enforced_ptr);
    (*(const_cast<uint8_t*>(hw_enforced_ptr) + hw_enforced_size - 1))++;

    // Deserialization shouldn't be affected, but decryption should fail.
    ASSERT_EQ(KM_ERROR_OK, Deserialize());
    ASSERT_EQ(KM_ERROR_INVALID_KEY_BLOB, Decrypt());
}

TEST_P(KeyBlobTest, WrongSwEnforced) {
    ASSERT_EQ(KM_ERROR_OK, Encrypt(GetParam()));
    ASSERT_EQ(KM_ERROR_OK, Serialize());

    // Find enforced serialization data and modify it.
    size_t sw_enforced_size = sw_enforced_.SerializedSize();
    UniquePtr<uint8_t[]> sw_enforced_data(new uint8_t[sw_enforced_size]);
    sw_enforced_.Serialize(sw_enforced_data.get(), sw_enforced_data.get() + sw_enforced_size);

    auto sw_enforced_ptr =
        std::search(serialized_blob_.begin(), serialized_blob_.end(), sw_enforced_data.get(),
                    sw_enforced_data.get() + sw_enforced_size);
    ASSERT_NE(serialized_blob_.end(), sw_enforced_ptr);
    (*(const_cast<uint8_t*>(sw_enforced_ptr) + sw_enforced_size - 1))++;

    // Deserialization shouldn't be affected, but decryption should fail.
    ASSERT_EQ(KM_ERROR_OK, Deserialize());
    ASSERT_EQ(KM_ERROR_INVALID_KEY_BLOB, Decrypt());
}

TEST_P(KeyBlobTest, EmptyHidden) {
    ASSERT_EQ(KM_ERROR_OK, Encrypt(GetParam()));
    ASSERT_EQ(KM_ERROR_OK, Serialize());

    AuthorizationSet wrong_hidden;

    // Deserialization shouldn't be affected, but decryption should fail.
    ASSERT_EQ(KM_ERROR_OK, Deserialize());
    auto result = DecryptKey(deserialized_key_, wrong_hidden, secure_deletion_data_, master_key_);
    EXPECT_FALSE(result.isOk());
    EXPECT_EQ(KM_ERROR_INVALID_KEY_BLOB, result.error());
}

TEST_P(KeyBlobTest, WrongRootOfTrust) {
    ASSERT_EQ(KM_ERROR_OK, Encrypt(GetParam()));
    ASSERT_EQ(KM_ERROR_OK, Serialize());

    AuthorizationSet wrong_hidden;
    wrong_hidden.push_back(TAG_ROOT_OF_TRUST, "bar", 2);
    wrong_hidden.push_back(TAG_APPLICATION_ID, "my_app", 6);

    // Deserialization shouldn't be affected, but decryption should fail.
    ASSERT_EQ(KM_ERROR_OK, Deserialize());
    auto result = DecryptKey(deserialized_key_, wrong_hidden, secure_deletion_data_, master_key_);
    EXPECT_FALSE(result.isOk());
    EXPECT_EQ(KM_ERROR_INVALID_KEY_BLOB, result.error());
}

TEST_P(KeyBlobTest, WrongAppId) {
    ASSERT_EQ(KM_ERROR_OK, Encrypt(GetParam()));
    ASSERT_EQ(KM_ERROR_OK, Serialize());

    AuthorizationSet wrong_hidden;
    wrong_hidden.push_back(TAG_ROOT_OF_TRUST, "foo", 3);
    wrong_hidden.push_back(TAG_APPLICATION_ID, "your_app", 7);

    // Deserialization shouldn't be affected, but decryption should fail.
    ASSERT_EQ(KM_ERROR_OK, Deserialize());
    auto result = DecryptKey(deserialized_key_, wrong_hidden, secure_deletion_data_, master_key_);
    EXPECT_FALSE(result.isOk());
    EXPECT_EQ(KM_ERROR_INVALID_KEY_BLOB, result.error());
}

// This test is especially useful when compiled for 32-bit mode and run under valgrind.
TEST_P(KeyBlobTest, FuzzTest) {
    time_t now = time(NULL);
    std::cout << "Seeding rand() with " << now << " for fuzz test." << std::endl;
    srand(now);

    // Fill large buffer with random bytes.
    const int kBufSize = 10000;
    UniquePtr<uint8_t[]> buf(new uint8_t[kBufSize]);
    for (size_t i = 0; i < kBufSize; ++i)
        buf[i] = static_cast<uint8_t>(rand());

    // Try to deserialize every offset with multiple methods.
    size_t deserialize_auth_encrypted_success = 0;
    for (size_t i = 0; i < kBufSize; ++i) {
        keymaster_key_blob_t blob = {buf.get() + i, kBufSize - i};
        KeymasterKeyBlob key_blob(blob);

        // Integrity-assured blob.
        ASSERT_EQ(KM_ERROR_INVALID_KEY_BLOB,
                  DeserializeIntegrityAssuredBlob(key_blob, hidden_, &key_material_, &hw_enforced_,
                                                  &sw_enforced_));

        // Auth-encrypted blob.
        auto deserialized = DeserializeAuthEncryptedBlob(key_blob);
        if (deserialized.isOk()) {
            // It's possible (though unlikely) to deserialize successfully.  Decryption should
            // always fail, though.
            ++deserialize_auth_encrypted_success;
            auto decrypted = DecryptKey(*deserialized, hidden_, secure_deletion_data_, master_key_);
            ASSERT_FALSE(decrypted.isOk());
            ASSERT_EQ(KM_ERROR_INVALID_KEY_BLOB, decrypted.error())
                << "Somehow successfully parsed and decrypted a blob with seed " << now
                << " at offset " << i;
        } else {
            ASSERT_EQ(KM_ERROR_INVALID_KEY_BLOB, deserialized.error());
        }
    }
}

TEST_P(KeyBlobTest, UnderflowTest) {
    uint8_t buf[0];
    keymaster_key_blob_t blob = {buf, 0};
    KeymasterKeyBlob key_blob(blob);
    EXPECT_NE(nullptr, key_blob.key_material);
    EXPECT_EQ(0U, key_blob.key_material_size);

    EXPECT_EQ(KM_ERROR_INVALID_KEY_BLOB,
              DeserializeIntegrityAssuredBlob(key_blob, hidden_, &key_material_, &hw_enforced_,
                                              &sw_enforced_));

    auto deserialized = DeserializeAuthEncryptedBlob(key_blob);
    EXPECT_FALSE(deserialized.isOk());
    EXPECT_EQ(KM_ERROR_INVALID_KEY_BLOB, deserialized.error());
}

TEST_P(KeyBlobTest, DupBufferToolarge) {
    uint8_t buf[0];
    keymaster_key_blob_t blob = {buf, 0};
    blob.key_material_size = 16 * 1024 * 1024 + 1;
    KeymasterKeyBlob key_blob(blob);
    EXPECT_EQ(nullptr, key_blob.key_material);
    EXPECT_EQ(0U, key_blob.key_material_size);

    ASSERT_EQ(KM_ERROR_INVALID_KEY_BLOB,
              DeserializeIntegrityAssuredBlob(key_blob, hidden_, &key_material_, &hw_enforced_,
                                              &sw_enforced_));

    auto deserialized = DeserializeAuthEncryptedBlob(key_blob);
    EXPECT_FALSE(deserialized.isOk());
    EXPECT_EQ(KM_ERROR_INVALID_KEY_BLOB, deserialized.error());
}

INSTANTIATE_TEST_SUITE_P(AllFormats, KeyBlobTest,
                         ::testing::Values(AES_OCB, AES_GCM_WITH_SW_ENFORCED,
                                           AES_GCM_WITH_SECURE_DELETION,
                                           AES_GCM_WITH_SW_ENFORCED_VERSIONED,
                                           AES_GCM_WITH_SECURE_DELETION_VERSIONED),
                         [](const ::testing::TestParamInfo<KeyBlobTest::ParamType>& info) {
                             switch (info.param) {
                             case AES_OCB:
                                 return "AES_OCB";
                             case AES_GCM_WITH_SW_ENFORCED:
                                 return "AES_GCM_WITH_SW_ENFORCED";
                             case AES_GCM_WITH_SECURE_DELETION:
                                 return "AES_GCM_WITH_SECURE_DELETION";
                             case AES_GCM_WITH_SW_ENFORCED_VERSIONED:
                                 return "AES_GCM_WITH_SW_ENFORCED_VERSIONED";
                             case AES_GCM_WITH_SECURE_DELETION_VERSIONED:
                                 return "AES_GCM_WITH_SECURE_DELETION_VERSIONED";
                             }
                             CHECK(false) << "Shouldn't be able to get here";
                             return "Unexpected";
                         });

using SecureDeletionTest = KeyBlobTest;

INSTANTIATE_TEST_SUITE_P(SecureDeletionFormats, SecureDeletionTest,
                         ::testing::Values(AES_GCM_WITH_SECURE_DELETION,
                                           AES_GCM_WITH_SECURE_DELETION_VERSIONED),
                         [](const ::testing::TestParamInfo<KeyBlobTest::ParamType>& info) {
                             switch (info.param) {
                             case AES_OCB:
                                 return "AES_OCB";
                             case AES_GCM_WITH_SW_ENFORCED:
                                 return "AES_GCM_WITH_SW_ENFORCED";
                             case AES_GCM_WITH_SECURE_DELETION:
                                 return "AES_GCM_WITH_SECURE_DELETION";
                             case AES_GCM_WITH_SW_ENFORCED_VERSIONED:
                                 return "AES_GCM_WITH_SW_ENFORCED_VERSIONED";
                             case AES_GCM_WITH_SECURE_DELETION_VERSIONED:
                                 return "AES_GCM_WITH_SECURE_DELETION_VERSIONED";
                             }
                             CHECK(false) << "Shouldn't be able to get here";
                             return "Unexpected";
                         });

TEST_P(SecureDeletionTest, WrongFactoryResetSecret) {
    ASSERT_EQ(KM_ERROR_OK, Encrypt(GetParam()));
    ASSERT_EQ(KM_ERROR_OK, Serialize());

    SecureDeletionData wrong_secure_deletion(std::move(secure_deletion_data_));
    wrong_secure_deletion.factory_reset_secret.Reinitialize("Wrong", sizeof("Wrong"));

    // Deserialization shouldn't be affected, but decryption should fail.
    ASSERT_EQ(KM_ERROR_OK, Deserialize());
    auto result = DecryptKey(deserialized_key_, hidden_, wrong_secure_deletion, master_key_);
    EXPECT_FALSE(result.isOk());
    EXPECT_EQ(KM_ERROR_INVALID_KEY_BLOB, result.error());
}

TEST_P(SecureDeletionTest, WrongSecureDeletionSecret) {
    ASSERT_EQ(KM_ERROR_OK, Encrypt(GetParam()));
    ASSERT_EQ(KM_ERROR_OK, Serialize());

    SecureDeletionData wrong_secure_deletion(std::move(secure_deletion_data_));
    wrong_secure_deletion.secure_deletion_secret.Reinitialize("Wrong", sizeof("Wrong"));

    // Deserialization shouldn't be affected, but decryption should fail.
    ASSERT_EQ(KM_ERROR_OK, Deserialize());
    auto result = DecryptKey(deserialized_key_, hidden_, wrong_secure_deletion, master_key_);
    EXPECT_FALSE(result.isOk());
    EXPECT_EQ(KM_ERROR_INVALID_KEY_BLOB, result.error());
}

TEST_P(SecureDeletionTest, WrongSecureDeletionKeySlot) {
    ASSERT_EQ(KM_ERROR_OK, Encrypt(GetParam()));
    ASSERT_EQ(KM_ERROR_OK, Serialize());

    SecureDeletionData wrong_secure_deletion(std::move(secure_deletion_data_));
    ++wrong_secure_deletion.key_slot;

    // Deserialization shouldn't be affected, but decryption should fail.
    ASSERT_EQ(KM_ERROR_OK, Deserialize());
    auto result = DecryptKey(deserialized_key_, hidden_, wrong_secure_deletion, master_key_);
    EXPECT_FALSE(result.isOk());
    EXPECT_EQ(KM_ERROR_INVALID_KEY_BLOB, result.error());
}

TEST(KmErrorOrDeathTest, UncheckedError) {
    ASSERT_DEATH({ KmErrorOr<int> kmError(KM_ERROR_UNKNOWN_ERROR); }, "");
}

TEST(KmErrorOrDeathTest, UseValueWithoutChecking) {
    ASSERT_DEATH(
        {
            KmErrorOr<int> kmError(KM_ERROR_UNKNOWN_ERROR);
            kmError.value();
            kmError.isOk();  // Check here so dtor won't abort().
        },
        "");
}

TEST(KmErrorOrDeathTest, CheckAfterReturn) {
    auto func = []() -> KmErrorOr<int> {
        // This instance will have its content moved and then be destroyed.  It
        // shouldn't abort()
        return KmErrorOr<int>(KM_ERROR_UNEXPECTED_NULL_POINTER);
    };

    {
        auto err = func();
        ASSERT_FALSE(err.isOk());  // Check here, so it isn't destroyed.
    }

    ASSERT_DEATH({ auto err = func(); }, "");
}

TEST(KmErrorOrDeathTest, CheckAfterMoveAssign) {
    ASSERT_DEATH(
        {
            KmErrorOr<int> err(KM_ERROR_UNEXPECTED_NULL_POINTER);
            KmErrorOr<int> err2(4);

            err2 = std::move(err);  // This swaps err and err2

            // Checking only one isn't enough.  Both were unchecked.
            EXPECT_FALSE(err2.isOk());
        },
        "");

    ASSERT_DEATH(
        {
            KmErrorOr<int> err(KM_ERROR_UNEXPECTED_NULL_POINTER);
            KmErrorOr<int> err2(4);

            err2 = std::move(err);  // This swaps err and err2

            // Checking only one isn't enough.  Both were unchecked.
            EXPECT_TRUE(err.isOk());
        },
        "");

    {
        KmErrorOr<int> err(KM_ERROR_UNEXPECTED_NULL_POINTER);
        KmErrorOr<int> err2(4);
        err2 = std::move(err);  // This swaps err and err2

        // Must check both to avoid abort().
        EXPECT_TRUE(err.isOk());
        EXPECT_FALSE(err2.isOk());
    }

    ASSERT_DEATH(
        {
            KmErrorOr<int> err(KM_ERROR_UNEXPECTED_NULL_POINTER);
            KmErrorOr<int> err2(4);

            err.isOk();             // Check err before swap
            err2 = std::move(err);  // This swaps err and err2
        },
        "");

    {
        KmErrorOr<int> err(KM_ERROR_UNEXPECTED_NULL_POINTER);
        KmErrorOr<int> err2(4);

        err.isOk();             // Check err before swap
        err2 = std::move(err);  // This swaps err and err2

        // err2 is checked, check err
        EXPECT_TRUE(err.isOk());
    }
}

TEST(KmErrorOr, CheckAfterMove) {
    KmErrorOr<int> err(KM_ERROR_UNEXPECTED_NULL_POINTER);

    KmErrorOr<int> err2(std::move(err));  // err won't abort
    EXPECT_FALSE(err2.isOk());            // err2 won't abort
    EXPECT_EQ(err2.error(), KM_ERROR_UNEXPECTED_NULL_POINTER);
}

TEST(KmErrorOrTest, UseErrorWithoutChecking) {
    KmErrorOr<int> kmError(99);
    // Checking error before using isOk() always returns KM_ERROR_UNKNOWN_ERROR.
    ASSERT_EQ(KM_ERROR_UNKNOWN_ERROR, kmError.error());
    ASSERT_TRUE(kmError.isOk());
    ASSERT_EQ(KM_ERROR_OK, kmError.error());
    ASSERT_EQ(99, *kmError);
}

TEST(KmErrorTest, DefaultCtor) {
    KmErrorOr<int> err;
    // Default-constructed objects don't need to be tested.  Should not crash.
}

}  // namespace keymaster::test
