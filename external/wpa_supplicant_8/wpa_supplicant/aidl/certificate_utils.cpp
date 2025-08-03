/*
 * WPA Supplicant - Certificate utils
 * Copyright (c) 2022, Google Inc. All rights reserved.
 *
 * This software may be distributed under the terms of the BSD license.
 * See README for more details.
 */

#include "certificate_utils.h"

#define AT __func__ << ":" << __LINE__ << " "

namespace ks2 = aidl::android::system::keystore2;
namespace KMV1 = aidl::android::hardware::security::keymint;

using aidl::android::hardware::wifi::supplicant::INonStandardCertCallback;

namespace {

constexpr const int64_t KS2_NAMESPACE_WIFI = 102;

constexpr const char kKeystore2ServiceName[] = "android.system.keystore2.IKeystoreService/default";

const std::string keystore2_grant_id_prefix("ks2_keystore-engine_grant_id:");

ks2::KeyDescriptor mkKeyDescriptor(const std::string& alias) {
	// If the key_id starts with the grant id prefix, we parse the following string as numeric
	// grant id. We can then use the grant domain without alias to load the designated key.
	if (::android::base::StartsWith(alias, keystore2_grant_id_prefix)) {
		std::stringstream s(alias.substr(keystore2_grant_id_prefix.size()));
		uint64_t tmp;
		s >> std::hex >> tmp;
		if (s.fail() || !s.eof()) {
			wpa_printf(MSG_ERROR, "Couldn't parse grant name: %s", alias.c_str());
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

// Helper method to convert certs in DER format to PEM format required by
// openssl library used by supplicant. If boringssl cannot parse the input as one or more
// X509 certificates in DER encoding, this function returns the input as-is. The assumption in
// that case is that either the `cert_bytes` is already PEM encoded, or `cert_bytes` is something
// completely different that was intentionally installed by the Wi-Fi subsystem and it must not
// be changed here.
// If any error occurs during PEM encoding, this function returns std::nullopt and logs an error.
std::optional<std::vector<uint8_t>> convertDerCertToPemOrPassthrough(
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
			wpa_printf(MSG_WARNING, "Could not parse DER X509 cert from buffer. Returning blob as is.");
			return cert_bytes;
		}

		if (!PEM_write_bio_X509(pem_bio.get(), cert.get())) {
			wpa_printf(MSG_ERROR, "Could not convert cert to PEM format.");
			return std::nullopt;
		}
	}

	const uint8_t* pem_bytes;
	size_t pem_len;
	if (!BIO_mem_contents(pem_bio.get(), &pem_bytes, &pem_len)) {
		wpa_printf(MSG_ERROR, "Could not extract pem_bytes from BIO.");
		return std::nullopt;
	}
	return {{pem_bytes, pem_bytes + pem_len}};
}

std::optional<std::vector<uint8_t>> getKeystore2Cert(const std::string& key) {
	::ndk::SpAIBinder keystoreBinder(AServiceManager_checkService(kKeystore2ServiceName));
	auto keystore2 = ks2::IKeystoreService::fromBinder(keystoreBinder);

	if (!keystore2) {
		wpa_printf(MSG_WARNING, "Unable to connect to Keystore 2.");
		return {};
	}

	bool ca_cert = false;
	std::string alias = key.c_str();
	if (::android::base::StartsWith(alias, "CACERT_")) {
		alias = alias.substr(7);
		ca_cert = true;
	} else if (::android::base::StartsWith(alias, "USRCERT_")) {
		alias = alias.substr(8);
	}

	ks2::KeyDescriptor descriptor = mkKeyDescriptor(alias);

	// If the key_id starts with the grant id prefix, we parse the following string as numeric
	// grant id. We can then use the grant domain without alias to load the designated key.
	if (::android::base::StartsWith(alias, keystore2_grant_id_prefix)) {
		std::stringstream s(alias.substr(keystore2_grant_id_prefix.size()));
		uint64_t tmp;
		s >> std::hex >> tmp;
		if (s.fail() || !s.eof()) {
			wpa_printf(MSG_ERROR, "Couldn't parse grant name: %s", alias.c_str());
		}
		descriptor.nspace = static_cast<int64_t>(tmp);
		descriptor.domain = ks2::Domain::GRANT;
		descriptor.alias = std::nullopt;
	}

	ks2::KeyEntryResponse response;
	auto rc = keystore2->getKeyEntry(descriptor, &response);
	if (!rc.isOk()) {
		if (rc.getServiceSpecificError() != int32_t(ks2::ResponseCode::KEY_NOT_FOUND)) {
			wpa_printf(MSG_WARNING, "Entry not found in Keystore 2.");
		} else {
			wpa_printf(MSG_WARNING, "Keystore 2 getKeyEntry failed error: %s", rc.getDescription().c_str());
		}
		return {};
	}

	if (ca_cert && response.metadata.certificateChain) {
		return std::move(*response.metadata.certificateChain);
	} else if (!ca_cert && response.metadata.certificate) {
		return std::move(*response.metadata.certificate);
	} else {
		wpa_printf(MSG_WARNING, "No %s certificate found.", (ca_cert ? "CA" : "client"));
		return {};
	}
}

std::optional<std::vector<uint8_t>> getNonStandardCert(const std::string& alias,
		const std::shared_ptr<INonStandardCertCallback> &non_standard_callback) {
	if (non_standard_callback == nullptr) {
		wpa_printf(MSG_ERROR, "Non-standard cert callback is not available");
		return std::nullopt;
	}
	std::vector<uint8_t> blob;
	const auto& status = non_standard_callback->getBlob(alias, &blob);
	if (!status.isOk()) {
		wpa_printf(MSG_ERROR, "Cert callback error, code=%d",
			status.getServiceSpecificError());
		return std::nullopt;
	}
	return blob;
}

}  // namespace

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
namespace certificate_utils {

std::optional<std::vector<uint8_t>> getCertificate(const std::string& alias,
		const std::shared_ptr<INonStandardCertCallback> &non_standard_callback) {
	std::vector<uint8_t> cert;
	if (auto ks2_cert = getKeystore2Cert(alias)) {
		cert = std::move(*ks2_cert);
	} else if (auto blob = getNonStandardCert(alias, non_standard_callback)) {
		cert = std::move(*blob);
	} else {
		wpa_printf(MSG_ERROR, "Failed to get certificate.");
		return std::nullopt;
	}

	if (auto result_cert = convertDerCertToPemOrPassthrough(cert)) {
		return result_cert;
	} else {
		wpa_printf(MSG_ERROR, "Conversion to PEM failed.");
		return std::nullopt;
	}
}

std::optional<std::vector<std::string>> listAliases(const std::string& prefix,
		const std::shared_ptr<INonStandardCertCallback> &non_standard_callback) {
	if (non_standard_callback == nullptr) {
		wpa_printf(MSG_ERROR, "Non-standard cert callback is not available");
		return std::nullopt;
	}
	std::vector<std::string> aliases;
	const auto& status = non_standard_callback->listAliases(prefix, &aliases);
	if (!status.isOk()) {
		wpa_printf(MSG_ERROR, "Unable to retrieve aliases");
		return std::nullopt;
	}
	return aliases;
}

}  // namespace certificate_utils
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
