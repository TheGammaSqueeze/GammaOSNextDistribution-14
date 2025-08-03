#pragma once

#include "ffi_test_utils.rs.h"
#include "rust/cxx.h"

bool validateCertChain(rust::Vec<rust::u8> cert_buf, uint32_t cert_len, bool strict_issuer_check);
CxxResult createWrappedKey(rust::Vec<rust::u8> encrypted_secure_key,
                           rust::Vec<rust::u8> encrypted_transport_key, rust::Vec<rust::u8> iv,
                           rust::Vec<rust::u8> tag);
CxxResult buildAsn1DerEncodedWrappedKeyDescription();
bool performCryptoOpUsingKeystoreEngine(int64_t grant_id);
CxxResult getValueFromAttestRecord(rust::Vec<rust::u8> cert_buf, int32_t tag,
                                   int32_t expected_sec_level);
uint32_t getOsVersion();
uint32_t getOsPatchlevel();
uint32_t getVendorPatchlevel();
