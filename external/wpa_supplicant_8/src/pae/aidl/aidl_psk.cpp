/*
 * WPA Supplicant - Aidl interface to access macsec PSK
 * Copyright (c) 2023, Google Inc. All rights reserved.
 *
 * This software may be distributed under the terms of the BSD license.
 * See README for more details.
 */

#include <aidl/android/hardware/macsec/IMacsecPskPlugin.h>
#include <android/binder_manager.h>

extern "C"
{
#include "utils/common.h"
#include "utils/eloop.h"
#include "utils/includes.h"

#include "aidl_psk.h"
}

using aidl::android::hardware::macsec::IMacsecPskPlugin;

static std::shared_ptr<IMacsecPskPlugin> pskPlugin;

int aidl_psk_init()
{
	if (pskPlugin != NULL) {
		wpa_printf(MSG_ERROR, "Already connected to Macsec plugin");
		return 0;
	}
	std::string instanceName = std::string(IMacsecPskPlugin::descriptor) + "/default";
	pskPlugin = IMacsecPskPlugin::fromBinder(
		ndk::SpAIBinder(AServiceManager_waitForService(instanceName.c_str())));

	if (pskPlugin == NULL) {
		wpa_printf(MSG_ERROR, "Cannot get Macsec PSK plugin service");
		return -ENODEV;
	}

	return 0;
}

int aidl_psk_aes_wrap(const u8 *kek, size_t kek_len, int n, const u8 *plain,
		u8 *cipher)
{
	if (pskPlugin == NULL)
		return -ENODEV;

	n = n * 8;

	const std::vector<u8> key_id(kek, kek + kek_len);
	const std::vector<u8> sak(plain, plain + n);
	std::vector<u8> out(n + 8);

	auto aidlStatus = pskPlugin->wrapSak(key_id, sak, &out);
	if (!aidlStatus.isOk()) {
		wpa_printf(MSG_ERROR, "wrapSak return error: %s", aidlStatus.getMessage());
		return -ENODEV;
	}

	if (out.size() != (n + 8)) {
		wpa_printf(MSG_ERROR, "wrapSak return size not n + 8");
		return -ENODEV;
	}

	memcpy(cipher, out.data(), n + 8);

	return 0;
}

int aidl_psk_aes_unwrap(const u8 *kek, size_t kek_len, int n,
		const u8 *cipher, u8 *plain)
{
	if (pskPlugin == NULL)
		return -ENODEV;

        n++;
	n = n * 8;
	if (n < 8)
		return -ENODEV;

	const std::vector<u8> key_id(kek, kek + kek_len);
	const std::vector<u8> sak(cipher, cipher + n);
	std::vector<u8> out(n - 8);

	auto aidlStatus = pskPlugin->unwrapSak(key_id, sak, &out);
	if (!aidlStatus.isOk()) {
		return -ENODEV;
	}

	if (out.size() != (n - 8)) {
		return -ENODEV;
	}

	memcpy(plain, out.data(), n - 8);

	return 0;
}

int aidl_psk_icv_hash(const u8 *ick, size_t ick_bytes, const u8 *msg,
		size_t msg_bytes, u8 *icv)
{
	if (pskPlugin == NULL) {
		wpa_printf(MSG_ERROR, "pskPlugin not init");
		return -ENODEV;
	}

	const std::vector<u8> key_id(ick, ick + ick_bytes);
	const std::vector<u8> data(msg, msg + msg_bytes);
	std::vector<u8> out(16);

	auto aidlStatus = pskPlugin->calcIcv(key_id, data, &out);
	if (!aidlStatus.isOk()) {
		wpa_printf(MSG_ERROR, "calcIcv return error: %s", aidlStatus.getMessage());
		return -ENODEV;
	}

	if (out.size() != 16) {
		wpa_printf(MSG_ERROR, "calcIcv out size not 16 bytes");
		return -ENODEV;
	}

	memcpy(icv, out.data(), 16);

	return 0;
}

int aidl_psk_sak_aes_cmac(const u8 *cak, size_t cak_bytes, const u8 *ctx,
		size_t ctx_bytes, u8 *sak, size_t sak_bytes)
{
	if (pskPlugin == NULL)
		return -ENODEV;

	const std::vector<u8> key_id(cak, cak + cak_bytes);
	const std::vector<u8> data(ctx, ctx + ctx_bytes);
	std::vector<u8> out(sak_bytes);

	auto aidlStatus = pskPlugin->generateSak(key_id, data, sak_bytes, &out);
	if (!aidlStatus.isOk()) {
		return -ENODEV;
	}

	if (out.size() != sak_bytes) {
		return -ENODEV;
	}

	memcpy(sak, out.data(), sak_bytes);

	return 0;
}
