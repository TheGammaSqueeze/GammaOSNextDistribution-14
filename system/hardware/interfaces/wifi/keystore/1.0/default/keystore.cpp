#include "include/wifikeystorehal/keystore.h"

#include <aidl/android/security/legacykeystore/ILegacyKeystore.h>
#include <aidl/android/system/keystore2/IKeystoreService.h>
#include <aidl/android/system/keystore2/ResponseCode.h>
#include <android-base/logging.h>
#include <android-base/strings.h>
#include <android/binder_manager.h>
#include <binder/IServiceManager.h>
#include <openssl/base.h>
#include <openssl/bio.h>
#include <openssl/pem.h>
#include <openssl/x509.h>
#include <private/android_filesystem_config.h>
#include <stdio.h>

#include <vector>

#include "wifikeystorehal_utils.h"

#define AT __func__ << ":" << __LINE__ << " "

namespace ks2 = ::aidl::android::system::keystore2;
namespace lks = ::aidl::android::security::legacykeystore;
namespace KMV1 = ::aidl::android::hardware::security::keymint;

namespace {

constexpr const int64_t KS2_NAMESPACE_WIFI = 102;

constexpr const char kKeystore2ServiceName[] = "android.system.keystore2.IKeystoreService/default";
constexpr const char kLegacyKeystoreServiceName[] = "android.security.legacykeystore";

const std::string keystore2_grant_id_prefix("ks2_keystore-engine_grant_id:");

ks2::KeyDescriptor mkKeyDescriptor(const std::string& alias) {
    // If the key_id starts with the grant id prefix, we parse the following string as numeric
    // grant id. We can then use the grant domain without alias to load the designated key.
    if (android::base::StartsWith(alias, keystore2_grant_id_prefix)) {
        std::stringstream s(alias.substr(keystore2_grant_id_prefix.size()));
        uint64_t tmp;
        s >> std::hex >> tmp;
        if (s.fail() || !s.eof()) {
            LOG(ERROR) << AT << "Couldn't parse grant name: " << alias;
        }
        return {
            .domain = ks2::Domain::GRANT,
            .nspace = static_cast<int64_t>(tmp),
            .alias = std::nullopt,
            .blob = std::nullopt,
        };
    } else {
        return {
            .domain = ks2::Domain::SELINUX,
            .nspace = KS2_NAMESPACE_WIFI,
            .alias = alias,
            .blob = std::nullopt,
        };
    }
}

using android::hardware::hidl_string;
using android::hardware::hidl_vec;

// Helper method to convert certs in DER format to PEM format required by
// openssl library used by supplicant. If boringssl cannot parse the input as one or more
// X509 certificates in DER encoding, this function returns the input as-is. The assumption in
// that case is that either the `cert_bytes` is already PEM encoded, or `cert_bytes` is something
// completely different that was intentionally installed by the Wi-Fi subsystem and it must not
// be changed here.
// If any error occurs during PEM encoding, this function returns std::nullopt and logs an error.
std::optional<hidl_vec<uint8_t>> convertDerCertToPemOrPassthrough(
    const std::vector<uint8_t>& cert_bytes) {
    // If cert_bytes is a DER encoded X509 certificate, it must be reencoded as PEM, because
    // wpa_supplicant only understand PEM. Otherwise the cert_bytes are returned as is.
    const uint8_t* cert_current = cert_bytes.data();
    const uint8_t* cert_end = cert_current + cert_bytes.size();
    bssl::UniquePtr<BIO> pem_bio(BIO_new(BIO_s_mem()));
    while (cert_current < cert_end) {
        auto cert =
            bssl::UniquePtr<X509>(d2i_X509(nullptr, &cert_current, cert_end - cert_current));
        // If part of the bytes cannot be parsed as X509 DER certificate, the original blob
        // shall be returned as-is.
        if (!cert) {
            LOG(WARNING) << AT
                         << "Could not parse DER X509 cert from buffer. Returning blob as is.";
            return cert_bytes;
        }

        if (!PEM_write_bio_X509(pem_bio.get(), cert.get())) {
            LOG(ERROR) << AT << "Could not convert cert to PEM format.";
            return std::nullopt;
        }
    }

    const uint8_t* pem_bytes;
    size_t pem_len;
    if (!BIO_mem_contents(pem_bio.get(), &pem_bytes, &pem_len)) {
        LOG(ERROR) << AT << "Could not extract pem_bytes from BIO.";
        return std::nullopt;
    }
    return {{pem_bytes, pem_bytes + pem_len}};
}

std::optional<std::vector<uint8_t>> keyStore2GetCert(const hidl_string& key) {
    ::ndk::SpAIBinder keystoreBinder(AServiceManager_checkService(kKeystore2ServiceName));
    auto keystore2 = ks2::IKeystoreService::fromBinder(keystoreBinder);

    if (!keystore2) {
        LOG(WARNING) << AT << "Unable to connect to Keystore 2.0.";
        return {};
    }

    bool ca_cert = false;
    std::string alias = key.c_str();
    if (android::base::StartsWith(alias, "CACERT_")) {
        alias = alias.substr(7);
        ca_cert = true;
    } else if (android::base::StartsWith(alias, "USRCERT_")) {
        alias = alias.substr(8);
    }

    ks2::KeyDescriptor descriptor = mkKeyDescriptor(alias);

    // If the key_id starts with the grant id prefix, we parse the following string as numeric
    // grant id. We can then use the grant domain without alias to load the designated key.
    if (android::base::StartsWith(alias, keystore2_grant_id_prefix)) {
        std::stringstream s(alias.substr(keystore2_grant_id_prefix.size()));
        uint64_t tmp;
        s >> std::hex >> tmp;
        if (s.fail() || !s.eof()) {
            LOG(ERROR) << AT << "Couldn't parse grant name: " << alias;
        }
        descriptor.nspace = static_cast<int64_t>(tmp);
        descriptor.domain = ks2::Domain::GRANT;
        descriptor.alias = std::nullopt;
    }

    ks2::KeyEntryResponse response;
    auto rc = keystore2->getKeyEntry(descriptor, &response);
    if (!rc.isOk()) {
        if (rc.getServiceSpecificError() != int32_t(ks2::ResponseCode::KEY_NOT_FOUND)) {
            LOG(WARNING) << AT
                         << "Entry not found in Keystore 2.0. Falling back to legacy keystore.";
        } else {
            LOG(ERROR) << AT << "Keystore 2.0 getKeyEntry failed error: " << rc.getDescription();
        }
        return {};
    }

    if (ca_cert && response.metadata.certificateChain) {
        return std::move(*response.metadata.certificateChain);
    } else if (!ca_cert && response.metadata.certificate) {
        return std::move(*response.metadata.certificate);
    } else {
        LOG(WARNING) << AT << "No " << (ca_cert ? "CA" : "client") << " certificate found. "
                     << "Falling back to legacy keystore.";
        return {};
    }
}

std::optional<std::vector<uint8_t>> keyStore2GetPubKey(const hidl_string& key) {
    ::ndk::SpAIBinder keystoreBinder(AServiceManager_checkService(kKeystore2ServiceName));
    auto keystore2 = ks2::IKeystoreService::fromBinder(keystoreBinder);

    if (!keystore2) {
        LOG(WARNING) << AT << "Unable to connect to Keystore 2.0.";
        return std::nullopt;
    }

    std::string alias = key.c_str();
    if (android::base::StartsWith(alias, "USRPKEY_")) {
        alias = alias.substr(8);
    }

    ks2::KeyDescriptor descriptor = mkKeyDescriptor(alias);

    ks2::KeyEntryResponse response;
    auto rc = keystore2->getKeyEntry(descriptor, &response);
    if (!rc.isOk()) {
        auto exception_code = rc.getExceptionCode();
        if (exception_code == EX_SERVICE_SPECIFIC) {
            LOG(ERROR) << AT << "Keystore getKeyEntry returned service specific error: "
                       << rc.getServiceSpecificError();
        } else {
            LOG(ERROR) << AT << "Communication with Keystore getKeyEntry failed error: "
                       << exception_code;
        }
        return std::nullopt;
    }

    if (!response.metadata.certificate) {
        LOG(ERROR) << AT << "No public key found.";
        return std::nullopt;
    }

    std::optional<std::vector<uint8_t>> pub_key(extractPubKey(*response.metadata.certificate));
    return pub_key;
}

std::optional<std::vector<uint8_t>> keyStore2Sign(const hidl_string& key,
                                                  const hidl_vec<uint8_t>& dataToSign) {
    ::ndk::SpAIBinder keystoreBinder(AServiceManager_checkService(kKeystore2ServiceName));
    auto keystore2 = ks2::IKeystoreService::fromBinder(keystoreBinder);

    if (!keystore2) {
        LOG(WARNING) << AT << "Unable to connect to Keystore 2.0.";
        return std::nullopt;
    }

    std::string alias = key.c_str();
    if (android::base::StartsWith(alias, "USRPKEY_")) {
        alias = alias.substr(8);
    }

    ks2::KeyDescriptor descriptor = mkKeyDescriptor(alias);

    ks2::KeyEntryResponse response;
    auto rc = keystore2->getKeyEntry(descriptor, &response);
    if (!rc.isOk()) {
        auto exception_code = rc.getExceptionCode();
        if (exception_code == EX_SERVICE_SPECIFIC) {
            LOG(ERROR) << AT << "Keystore getKeyEntry returned service specific error: "
                       << rc.getServiceSpecificError();
        } else {
            LOG(ERROR) << AT << "Communication with Keystore getKeyEntry failed error: "
                       << exception_code;
        }
        return std::nullopt;
    }

    std::optional<KMV1::Algorithm> algorithm;
    for (auto& element : response.metadata.authorizations) {
        if (element.keyParameter.tag == KMV1::Tag::ALGORITHM) {
            algorithm = element.keyParameter.value.get<KMV1::KeyParameterValue::algorithm>();
        }
    }

    if (!algorithm) {
        LOG(ERROR) << AT << "Could not find signing algorithm.";
        return std::nullopt;
    }

    auto sec_level = response.iSecurityLevel;

    std::vector<KMV1::KeyParameter> op_params(4);
    op_params[0] = KMV1::KeyParameter{
        .tag = KMV1::Tag::PURPOSE,
        .value = KMV1::KeyParameterValue::make<KMV1::KeyParameterValue::keyPurpose>(
            KMV1::KeyPurpose::SIGN)};
    op_params[1] = KMV1::KeyParameter{
        .tag = KMV1::Tag::ALGORITHM,
        .value = KMV1::KeyParameterValue::make<KMV1::KeyParameterValue::algorithm>(*algorithm)};
    op_params[2] = KMV1::KeyParameter{
        .tag = KMV1::Tag::PADDING,
        .value = KMV1::KeyParameterValue::make<KMV1::KeyParameterValue::paddingMode>(
            KMV1::PaddingMode::NONE)};
    op_params[3] = KMV1::KeyParameter{
        .tag = KMV1::Tag::DIGEST,
        .value =
            KMV1::KeyParameterValue::make<KMV1::KeyParameterValue::digest>(KMV1::Digest::NONE)};

    ks2::CreateOperationResponse op_response;

    rc = sec_level->createOperation(descriptor, op_params, false /* forced */, &op_response);
    if (!rc.isOk()) {
        auto exception_code = rc.getExceptionCode();
        if (exception_code == EX_SERVICE_SPECIFIC) {
            LOG(ERROR) << AT << "Keystore createOperation returned service specific error: "
                       << rc.getServiceSpecificError();
        } else {
            LOG(ERROR) << AT << "Communication with Keystore createOperation failed error: "
                       << exception_code;
        }
        return std::nullopt;
    }

    auto op = op_response.iOperation;
    std::optional<std::vector<uint8_t>> output = std::nullopt;

    rc = op->finish(dataToSign, {}, &output);
    if (!rc.isOk()) {
        auto exception_code = rc.getExceptionCode();
        if (exception_code == EX_SERVICE_SPECIFIC) {
            LOG(ERROR) << AT << "Keystore finish returned service specific error: "
                       << rc.getServiceSpecificError();
        } else {
            LOG(ERROR) << AT
                       << "Communication with Keystore finish failed error: " << exception_code;
        }
        return std::nullopt;
    }

    if (!output) {
        LOG(ERROR) << AT << "Could not get a signature from Keystore.";
    }

    return output;
}

std::optional<std::vector<uint8_t>> getLegacyKeystoreBlob(const hidl_string& key) {
    ::ndk::SpAIBinder keystoreBinder(AServiceManager_checkService(kLegacyKeystoreServiceName));
    auto legacyKeystore = lks::ILegacyKeystore::fromBinder(keystoreBinder);

    if (!legacyKeystore) {
        LOG(WARNING) << AT << "Unable to connect to LegacyKeystore";
        return std::nullopt;
    }

    std::optional<std::vector<uint8_t>> blob(std::vector<uint8_t>{});
    auto rc = legacyKeystore->get(key, AID_WIFI, &*blob);
    if (!rc.isOk()) {
        LOG(ERROR) << AT << "Failed to get legacy keystore entry for alias \"" << key
                   << "\": " << rc.getDescription();
        return std::nullopt;
    }
    return blob;
}

};  // namespace

namespace android {
namespace system {
namespace wifi {
namespace keystore {
namespace V1_0 {
namespace implementation {
// Methods from ::android::hardware::wifi::keystore::V1_0::IKeystore follow.
Return<void> Keystore::getBlob(const hidl_string& key, getBlob_cb _hidl_cb) {
    std::vector<uint8_t> result_cert;
    if (auto ks2_cert = keyStore2GetCert(key)) {
        result_cert = std::move(*ks2_cert);
    } else if (auto blob = getLegacyKeystoreBlob(key)) {
        result_cert = std::move(*blob);
    } else {
        LOG(ERROR) << AT << "Failed to get certificate.";
        _hidl_cb(KeystoreStatusCode::ERROR_UNKNOWN, {});
        return Void();
    }

    if (auto result_cert_hidl = convertDerCertToPemOrPassthrough(result_cert)) {
        _hidl_cb(KeystoreStatusCode::SUCCESS, *result_cert_hidl);
    } else {
        LOG(ERROR) << AT << "Conversion to PEM failed.";
        _hidl_cb(KeystoreStatusCode::ERROR_UNKNOWN, {});
    }
    return Void();
}

Return<void> Keystore::getPublicKey(const hidl_string& keyId, getPublicKey_cb _hidl_cb) {
    if (auto ks2_pubkey = keyStore2GetPubKey(keyId)) {
        _hidl_cb(KeystoreStatusCode::SUCCESS, std::move(*ks2_pubkey));
    } else {
        LOG(ERROR) << AT << "Failed to get public key.";
        _hidl_cb(KeystoreStatusCode::ERROR_UNKNOWN, {});
    }
    return Void();
}

Return<void> Keystore::sign(const hidl_string& keyId, const hidl_vec<uint8_t>& dataToSign,
                            sign_cb _hidl_cb) {
    if (auto ks2_result = keyStore2Sign(keyId, dataToSign)) {
        _hidl_cb(KeystoreStatusCode::SUCCESS, std::move(*ks2_result));
    } else {
        LOG(ERROR) << AT << "Failed to sign.";
        _hidl_cb(KeystoreStatusCode::ERROR_UNKNOWN, {});
    }
    return Void();
}

IKeystore* HIDL_FETCH_IKeystore(const char* /* name */) {
    return new Keystore();
}
}  // namespace implementation
}  // namespace V1_0
}  // namespace keystore
}  // namespace wifi
}  // namespace system
}  // namespace android
