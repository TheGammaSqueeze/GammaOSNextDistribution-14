/*
 * Copyright (C) 2018 The Android Open Source Project
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

/**
 * This app tests the API in app/keymaster/secure_storage.h. To run this test,
 * include keymaster/storage_test in TRUSTY_ALL_USER_TASKS, and it will be start
 * once an RPMB proxy becomes available.
 *
 * Different application has different namespace, so this would not affect the
 * keymaster app's RPMB storage.
 */

#include <assert.h>
#include <malloc.h>
#include <stdio.h>
#ifndef __clang__
// We need to diable foritfy level for memset in gcc because we want to use
// memset unoptimized. This would falsely trigger __warn_memset_zero_len in
// /usr/include/bits/string3.h. The inline checking function is only supposed to
// work when the optimization level is at least 1. In android_keymaster_utils.h
// we would use memset unoptimized.
#pragma push_macro("__USE_FORTIFY_LEVEL")
#undef __USE_FORTIFY_LEVEL
#endif
#include <string.h>
#ifndef __clang__
#pragma pop_macro("__USE_FORTIFY_LEVEL")
#endif
#include <fstream>

#define typeof(x) __typeof__(x)
#include <trusty_unittest.h>

#include <keymaster/android_keymaster_utils.h>
#include "second_imei_attestation.h"
#include "secure_storage_manager.h"

#define DATA_SIZE 2048
#define CHAIN_LENGTH 3

#define TLOG_TAG "km_storage_test"

using keymaster::AttestationKeySlot;
using keymaster::calculate_luhn_checksum_digit;
using keymaster::CertificateChain;
using keymaster::kAttestationUuidSize;
using keymaster::KeymasterKeyBlob;
using keymaster::kProductIdSize;
using keymaster::SecureStorageManager;
using keymaster::validate_second_imei;

uint8_t* NewRandBuf(size_t size) {
    uint8_t* buf = new uint8_t[size];
    if (buf == nullptr) {
        return buf;
    }
    for (uint8_t* i = buf;
         reinterpret_cast<size_t>(i) < reinterpret_cast<size_t>(buf) + size;
         i++) {
        *i = static_cast<uint8_t>(rand() % UINT8_MAX);
    }
    return buf;
}

void TestKeyStorage(AttestationKeySlot key_slot) {
    keymaster_error_t error = KM_ERROR_OK;
    keymaster::UniquePtr<uint8_t[]> write_key;
    KeymasterKeyBlob key_blob;
    bool key_exists = false;

    SecureStorageManager* ss_manager = SecureStorageManager::get_instance();
    ASSERT_NE(nullptr, ss_manager);

    write_key.reset(NewRandBuf(DATA_SIZE));
    ASSERT_NE(nullptr, write_key.get());

    error = ss_manager->WriteKeyToStorage(key_slot, write_key.get(), DATA_SIZE);
    ASSERT_EQ(KM_ERROR_OK, error);

    key_blob = ss_manager->ReadKeyFromStorage(key_slot, &error);
    ASSERT_EQ(KM_ERROR_OK, error);
    ASSERT_EQ(DATA_SIZE, key_blob.key_material_size);
    ASSERT_NE(nullptr, write_key.get());
    ASSERT_EQ(0, memcmp(write_key.get(), key_blob.writable_data(), DATA_SIZE));

    error = ss_manager->AttestationKeyExists(key_slot, &key_exists);
    ASSERT_EQ(KM_ERROR_OK, error);
    ASSERT_EQ(true, key_exists);

test_abort:;
}

void TestCertChainStorage(AttestationKeySlot key_slot, bool chain_exists) {
    keymaster_error_t error = KM_ERROR_OK;
    keymaster::UniquePtr<uint8_t[]> write_cert[CHAIN_LENGTH];
    unsigned int i = 0;
    uint32_t cert_chain_length;
    CertificateChain chain;

    SecureStorageManager* ss_manager = SecureStorageManager::get_instance();
    ASSERT_NE(nullptr, ss_manager);

    for (i = 0; i < CHAIN_LENGTH; ++i) {
        write_cert[i].reset(NewRandBuf(DATA_SIZE));
        ASSERT_NE(nullptr, write_cert[i].get());

        error = ss_manager->WriteCertToStorage(key_slot, write_cert[i].get(),
                                               DATA_SIZE, i);
        ASSERT_EQ(KM_ERROR_OK, error);

        error = ss_manager->ReadCertChainLength(key_slot, &cert_chain_length);
        ASSERT_EQ(KM_ERROR_OK, error);
        if (chain_exists) {
            ASSERT_EQ(3, cert_chain_length);
        } else {
            ASSERT_EQ(i + 1, cert_chain_length);
        }
    }

    error = ss_manager->ReadCertChainFromStorage(key_slot, &chain);
    ASSERT_EQ(KM_ERROR_OK, error);
    ASSERT_EQ(CHAIN_LENGTH, chain.entry_count);
    for (i = 0; i < CHAIN_LENGTH; ++i) {
        ASSERT_EQ(DATA_SIZE, chain.entries[i].data_length);
        ASSERT_EQ(0, memcmp(write_cert[i].get(), chain.entries[i].data,
                            DATA_SIZE));
    }

    error = ss_manager->DeleteCertChainFromStorage(key_slot);
    ASSERT_EQ(KM_ERROR_OK, error);
    chain.Clear();
    error = ss_manager->ReadCertChainFromStorage(key_slot, &chain);
    ASSERT_EQ(KM_ERROR_OK, error);
    ASSERT_EQ(0, chain.entry_count);

test_abort:;
}

void TestCertStorageInvalid(AttestationKeySlot key_slot) {
    keymaster_error_t error = KM_ERROR_OK;
    keymaster::UniquePtr<uint8_t[]> write_cert;
    uint32_t cert_chain_length;

    SecureStorageManager* ss_manager = SecureStorageManager::get_instance();
    ASSERT_NE(nullptr, ss_manager);

    // Clear existing certificate chain
    error = ss_manager->DeleteKey(key_slot, true);
    ASSERT_EQ(KM_ERROR_OK, error);
    error = ss_manager->ReadCertChainLength(key_slot, &cert_chain_length);
    ASSERT_EQ(KM_ERROR_OK, error);
    ASSERT_EQ(0, cert_chain_length);

    // Try to write to index (chain_length + 1)
    write_cert.reset(NewRandBuf(DATA_SIZE));
    ASSERT_NE(nullptr, write_cert.get());
    error = ss_manager->WriteCertToStorage(key_slot, write_cert.get(),
                                           DATA_SIZE, 1);
    ASSERT_EQ(KM_ERROR_INVALID_ARGUMENT, error);

    // Verify that cert chain length didn't change
    error = ss_manager->ReadCertChainLength(key_slot, &cert_chain_length);
    ASSERT_EQ(KM_ERROR_OK, error);
    ASSERT_EQ(0, cert_chain_length);

test_abort:;
}

void DeleteAttestationData() {
    keymaster_error_t error = KM_ERROR_OK;
    uint32_t cert_chain_length;
    bool key_exists;

    SecureStorageManager* ss_manager = SecureStorageManager::get_instance();
    ASSERT_NE(nullptr, ss_manager);

    error = ss_manager->DeleteAllAttestationData();
    ASSERT_EQ(KM_ERROR_OK, error);

    error = ss_manager->ReadCertChainLength(AttestationKeySlot::kRsa,
                                            &cert_chain_length);
    ASSERT_EQ(KM_ERROR_OK, error);
    ASSERT_EQ(0, cert_chain_length);
    error = ss_manager->ReadCertChainLength(AttestationKeySlot::kEcdsa,
                                            &cert_chain_length);
    ASSERT_EQ(KM_ERROR_OK, error);
    ASSERT_EQ(0, cert_chain_length);

    error = ss_manager->AttestationKeyExists(AttestationKeySlot::kRsa,
                                             &key_exists);
    ASSERT_EQ(KM_ERROR_OK, error);
    ASSERT_EQ(false, key_exists);
    error = ss_manager->AttestationKeyExists(AttestationKeySlot::kEcdsa,
                                             &key_exists);
    ASSERT_EQ(KM_ERROR_OK, error);
    ASSERT_EQ(false, key_exists);

test_abort:;
}

void TestUuidStorage() {
    keymaster_error_t error = KM_ERROR_OK;
    keymaster::UniquePtr<uint8_t[]> write_uuid;
    keymaster::UniquePtr<uint8_t[]> read_uuid(
            new uint8_t[kAttestationUuidSize]);

    SecureStorageManager* ss_manager = SecureStorageManager::get_instance();
    ASSERT_NE(nullptr, ss_manager);

    error = ss_manager->DeleteAttestationUuid();
    ASSERT_EQ(KM_ERROR_OK, error);

    write_uuid.reset(NewRandBuf(kAttestationUuidSize));
    ASSERT_NE(nullptr, write_uuid.get());

    error = ss_manager->WriteAttestationUuid((const uint8_t*)write_uuid.get());
    ASSERT_EQ(KM_ERROR_OK, error);

    error = ss_manager->ReadAttestationUuid(read_uuid.get());
    ASSERT_EQ(KM_ERROR_OK, error);
    ASSERT_NE(nullptr, read_uuid.get());
    ASSERT_EQ(0, memcmp(write_uuid.get(), read_uuid.get(), kProductIdSize));

    error = ss_manager->DeleteAttestationUuid();
    ASSERT_EQ(KM_ERROR_OK, error);

test_abort:;
}

void TestProductIdStorage() {
    keymaster_error_t error = KM_ERROR_OK;
    keymaster::UniquePtr<uint8_t[]> write_productid;
    keymaster::UniquePtr<uint8_t[]> read_productid(new uint8_t[kProductIdSize]);

    SecureStorageManager* ss_manager = SecureStorageManager::get_instance();
    ASSERT_NE(nullptr, ss_manager);

    error = ss_manager->DeleteProductId();
    ASSERT_EQ(KM_ERROR_OK, error);

    write_productid.reset(NewRandBuf(kProductIdSize));
    ASSERT_NE(nullptr, write_productid.get());

    error = ss_manager->SetProductId((const uint8_t*)write_productid.get());
    ASSERT_EQ(KM_ERROR_OK, error);

    error = ss_manager->ReadProductId(read_productid.get());
    ASSERT_EQ(KM_ERROR_OK, error);
    ASSERT_NE(nullptr, read_productid.get());
    ASSERT_EQ(0, memcmp(write_productid.get(), read_productid.get(),
                        kProductIdSize));

    error = ss_manager->DeleteProductId();
    ASSERT_EQ(KM_ERROR_OK, error);

test_abort:;
}

void TestProductIdStoragePreventOverwrite() {
    keymaster_error_t error = KM_ERROR_OK;
    keymaster::UniquePtr<uint8_t[]> write_productid;
    keymaster::UniquePtr<uint8_t[]> overwrite_productid;
    keymaster::UniquePtr<uint8_t[]> read_productid(new uint8_t[kProductIdSize]);

    SecureStorageManager* ss_manager = SecureStorageManager::get_instance();
    ASSERT_NE(nullptr, ss_manager);

    error = ss_manager->DeleteProductId();
    ASSERT_EQ(KM_ERROR_OK, error);

    write_productid.reset(NewRandBuf(kProductIdSize));
    ASSERT_NE(nullptr, write_productid.get());

    error = ss_manager->SetProductId((const uint8_t*)write_productid.get());
    ASSERT_EQ(KM_ERROR_OK, error);

    overwrite_productid.reset(NewRandBuf(kProductIdSize));
    error = ss_manager->SetProductId((const uint8_t*)write_productid.get());
    ASSERT_EQ(KM_ERROR_INVALID_ARGUMENT, error);

    error = ss_manager->ReadProductId(read_productid.get());
    ASSERT_EQ(KM_ERROR_OK, error);
    ASSERT_NE(nullptr, read_productid.get());
    ASSERT_EQ(0, memcmp(write_productid.get(), read_productid.get(),
                        kProductIdSize));

    error = ss_manager->DeleteProductId();
    ASSERT_EQ(KM_ERROR_OK, error);

test_abort:;
}

#if defined(KEYMASTER_LEGACY_FORMAT)
// Test to verify backward compatibility.
void TestFormatChange() {
    keymaster_error_t error = KM_ERROR_OK;
    keymaster::UniquePtr<uint8_t[]> write_key[2];
    KeymasterKeyBlob key_blob;
    bool key_exists = false;
    AttestationKeySlot key_slots[] = {AttestationKeySlot::kRsa,
                                      AttestationKeySlot::kEcdsa};
    keymaster::UniquePtr<uint8_t[]> write_cert[2][CHAIN_LENGTH];
    CertificateChain chain;

    SecureStorageManager* ss_manager =
            SecureStorageManager::get_instance(false);
    ASSERT_NE(nullptr, ss_manager);

    // Write the key and cert in old format.
    for (size_t i = 0; i < 2; i++) {
        AttestationKeySlot key_slot = key_slots[i];
        write_key[i].reset(NewRandBuf(DATA_SIZE));
        ASSERT_NE(nullptr, write_key[i].get());
        error = ss_manager->LegacyWriteKeyToStorage(
                key_slot, write_key[i].get(), DATA_SIZE);
        ASSERT_EQ(KM_ERROR_OK, error);

        for (int index = 0; index < CHAIN_LENGTH; index++) {
            write_cert[i][index].reset(NewRandBuf(DATA_SIZE));
            ASSERT_NE(nullptr, write_cert[i][index].get());

            error = ss_manager->LegacyWriteCertToStorage(
                    key_slot, write_cert[i][index].get(), DATA_SIZE, index);
            ASSERT_EQ(KM_ERROR_OK, error);
        }
    }

    // Try to translate the format.
    ss_manager = SecureStorageManager::get_instance();
    ASSERT_NE(nullptr, ss_manager);

    // Read key and cert out using new format.
    for (size_t i = 0; i < 2; i++) {
        AttestationKeySlot key_slot = key_slots[i];
        key_blob = ss_manager->ReadKeyFromStorage(key_slot, &error);
        ASSERT_EQ(KM_ERROR_OK, error);
        ASSERT_EQ(DATA_SIZE, key_blob.key_material_size);
        ASSERT_NE(nullptr, write_key[i].get());
        ASSERT_EQ(0, memcmp(write_key[i].get(), key_blob.writable_data(),
                            DATA_SIZE));

        error = ss_manager->AttestationKeyExists(key_slot, &key_exists);
        ASSERT_EQ(KM_ERROR_OK, error);
        ASSERT_EQ(true, key_exists);
        uint32_t cert_chain_length;
        error = ss_manager->ReadCertChainLength(key_slot, &cert_chain_length);
        ASSERT_EQ(KM_ERROR_OK, error);
        ASSERT_EQ(CHAIN_LENGTH, cert_chain_length);

        chain.Clear();
        error = ss_manager->ReadCertChainFromStorage(key_slot, &chain);
        ASSERT_EQ(KM_ERROR_OK, error);
        ASSERT_EQ(CHAIN_LENGTH, chain.entry_count);

        for (int index = 0; index < CHAIN_LENGTH; index++) {
            ASSERT_EQ(DATA_SIZE, chain.entries[i].data_length);
            ASSERT_EQ(0, memcmp(write_cert[i][index].get(),
                                chain.entries[index].data, DATA_SIZE));
        }
    }

    DeleteAttestationData();
    ss_manager->DeleteProductId();
    ss_manager->DeleteAttestationUuid();

test_abort:;
}
#endif

#if defined(KEYMASTER_LEGACY_FORMAT)
TEST(KeymasterFormatChangeTest, TestFormatChange) {
    TestFormatChange();
}
#endif

void TestLuhnChecksumCalculation() {
    ASSERT_EQ(0u, calculate_luhn_checksum_digit(0));
    ASSERT_EQ(3, calculate_luhn_checksum_digit(7992739871ul));
    ASSERT_EQ(3, calculate_luhn_checksum_digit(7992739871ul));
    ASSERT_EQ(6, calculate_luhn_checksum_digit(735423462345ul));
    ASSERT_EQ(4, calculate_luhn_checksum_digit(721367498765427ul));
test_abort:;
}

keymaster_blob_t toBlob(const char* str) {
    return {reinterpret_cast<const uint8_t*>(str), strlen(str)};
}

void TestSecondImeiValidation() {
    // First IMEI used throughout the test
    const uint64_t first_imei = 721367498765404;
    // Sequential IMEI
    const char* second_imei = "721367498765412";
    // Valid IMEI, but not a sequential one
    const char* second_imei_not_seq = "721367498765420";
    // Invalid IMEI, not a sequential one.
    const char* invalid_imei = "721367498765437";
    // Invalid IMEI: Sequential to first_imei but the checksum digit is wrong.
    const char* invalid_seq_imei = "721367498765415";
    ASSERT_EQ(true, validate_second_imei(toBlob(second_imei), first_imei));

    ASSERT_EQ(false,
              validate_second_imei(toBlob(second_imei_not_seq), first_imei));
    ASSERT_EQ(false, validate_second_imei(toBlob(invalid_imei), first_imei));
    ASSERT_EQ(false,
              validate_second_imei(toBlob(invalid_seq_imei), first_imei));
test_abort:;
}

typedef struct {
} KeymasterTest_t;

static void KeymasterTest_SetUp(KeymasterTest_t* state) {
    DeleteAttestationData();
}

static void KeymasterTest_TearDown(KeymasterTest_t* state) {
    DeleteAttestationData();
}

TEST_F(KeymasterTest, TestKeyStorageRsa) {
    TestKeyStorage(AttestationKeySlot::kRsa);
}

TEST_F(KeymasterTest, TestKeyStorageEcdsa) {
    TestKeyStorage(AttestationKeySlot::kEcdsa);
}

TEST_F(KeymasterTest, TestCertChainStorageRsa) {
    TestCertChainStorage(AttestationKeySlot::kRsa, false);
}

TEST_F(KeymasterTest, TestCertChainStorageEcdsa) {
    TestCertChainStorage(AttestationKeySlot::kEcdsa, false);
}

TEST_F(KeymasterTest, TestRewriteKey) {
    TestKeyStorage(AttestationKeySlot::kRsa);
    // Rewriting keys should work
    TestKeyStorage(AttestationKeySlot::kRsa);
}

TEST_F(KeymasterTest, TestRewriteChain) {
    TestCertChainStorage(AttestationKeySlot::kRsa, false);
    TestCertChainStorage(AttestationKeySlot::kRsa, false);
}

TEST_F(KeymasterTest, TestCertStorageInvalid) {
    TestCertStorageInvalid(AttestationKeySlot::kRsa);
}

TEST_F(KeymasterTest, TestUuidStorage) {
    TestUuidStorage();
}

TEST_F(KeymasterTest, TestProductIdStorage) {
    TestProductIdStorage();
}

TEST_F(KeymasterTest, TestLuhnChecksumCalculation) {
    TestLuhnChecksumCalculation();
}

TEST_F(KeymasterTest, TestSecondImeiValidation) {
    TestSecondImeiValidation();
}

#ifndef KEYMASTER_DEBUG
TEST_F(KeymasterTest, TestProductIdStoragePreventOverwrite) {
    TestProductIdStoragePreventOverwrite();
}
#endif

int main(void) {
    bool passed1 = RUN_ALL_SUITE_TESTS("KeymasterFormatChangeTest");
    bool passed2 = RUN_ALL_SUITE_TESTS("KeymasterTest");
    return (passed1 && passed2) ? 0 : 1;
}
