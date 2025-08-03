/*
 * WPA Supplicant - Aidl interface to access macsec PSK
 * Copyright (c) 2023, Google Inc. All rights reserved.
 *
 * This software may be distributed under the terms of the BSD license.
 * See README for more details.
 */

#ifndef WPA_SUPPLICANT_PAE_AIDL_PSK_H
#define WPA_SUPPLICANT_PAE_AIDL_PSK_H

#ifdef _cplusplus
extern "C"
{
#endif  // _cplusplus

	/* cak, kek, ick are all reference index only for HAL, not real key, the
	 * HAL will use the actual key */
	int aidl_psk_init();
	int __must_check aidl_psk_aes_wrap(const u8 *kek, size_t kek_len, int n, const u8 *plain,
			  u8 *cipher);
	int __must_check aidl_psk_aes_unwrap(const u8 *kek, size_t kek_len, int n,
			    const u8 *cipher, u8 *plain);
	int aidl_psk_icv_hash(const u8 *ick, size_t ick_bytes, const u8 *msg,
			    size_t msg_bytes, u8 *icv);
	int aidl_psk_sak_aes_cmac(const u8 *cak, size_t cak_bytes, const u8 *ctx,
			    size_t ctx_bytes, u8 *sak, size_t sak_bytes);

#ifdef _cplusplus
}
#endif  // _cplusplus

#endif  // WPA_SUPPLICANT_PAE_AIDL_PSK_H
