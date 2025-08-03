// SPDX-License-Identifier: MIT
/*
 * Implementation of libfsverity_sign_digest().
 *
 * Copyright 2018 Google LLC
 * Copyright (C) 2020 Facebook
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */

#include "lib_private.h"

#include <limits.h>
#include <openssl/bio.h>
#include <openssl/err.h>
#include <openssl/pem.h>
#include <openssl/pkcs7.h>
#include <string.h>

#ifndef OPENSSL_IS_BORINGSSL
#include <openssl/engine.h>
#endif

static int print_openssl_err_cb(const char *str,
				size_t len __attribute__((unused)),
				void *u __attribute__((unused)))
{
	libfsverity_error_msg("%s", str);
	return 1;
}

static void __printf(1, 2) __cold
error_msg_openssl(const char *format, ...)
{
	int saved_errno = errno;
	va_list va;

	va_start(va, format);
	libfsverity_do_error_msg(format, va);
	va_end(va);

	if (ERR_peek_error() == 0)
		return;

	libfsverity_error_msg("OpenSSL library errors:");
	ERR_print_errors_cb(print_openssl_err_cb, NULL);
	errno = saved_errno;
}

/* Read a PEM PKCS#8 formatted private key */
static int read_private_key(const char *keyfile, EVP_PKEY **pkey_ret)
{
	BIO *bio;
	EVP_PKEY *pkey;
	int err;

	errno = 0;
	bio = BIO_new_file(keyfile, "r");
	if (!bio) {
		error_msg_openssl("can't open '%s' for reading", keyfile);
		return errno ? -errno : -EIO;
	}

	pkey = PEM_read_bio_PrivateKey(bio, NULL, NULL, NULL);
	if (!pkey) {
		error_msg_openssl("Failed to parse private key file '%s'.\n"
				  "       Note: it must be in PEM PKCS#8 format.",
				  keyfile);
		err = -EBADMSG;
		goto out;
	}
	*pkey_ret = pkey;
	err = 0;
out:
	BIO_free(bio);
	return err;
}

/* Read a PEM X.509 formatted certificate */
static int read_certificate(const char *certfile, X509 **cert_ret)
{
	BIO *bio;
	X509 *cert;
	int err;

	if (!certfile) {
		libfsverity_error_msg("no certificate specified");
		return -EINVAL;
	}

	errno = 0;
	bio = BIO_new_file(certfile, "r");
	if (!bio) {
		error_msg_openssl("can't open '%s' for reading", certfile);
		return errno ? -errno : -EIO;
	}
	cert = PEM_read_bio_X509(bio, NULL, NULL, NULL);
	if (!cert) {
		error_msg_openssl("Failed to parse X.509 certificate file '%s'.\n"
				  "       Note: it must be in PEM format.",
				  certfile);
		err = -EBADMSG;
		goto out;
	}
	*cert_ret = cert;
	err = 0;
out:
	BIO_free(bio);
	return err;
}

#ifdef OPENSSL_IS_BORINGSSL

static int sign_pkcs7(const void *data_to_sign, size_t data_size,
		      EVP_PKEY *pkey, X509 *cert, const EVP_MD *md,
		      u8 **sig_ret, size_t *sig_size_ret)
{
	BIGNUM *serial;
	CBB out, outer_seq, wrapped_seq, seq, digest_algos_set, digest_algo,
		null, content_info, issuer_and_serial, signer_infos,
		signer_info, sign_algo, signature;
	EVP_MD_CTX md_ctx;
	u8 *name_der = NULL, *sig = NULL, *pkcs7_data = NULL;
	size_t pkcs7_data_len, sig_len;
	int name_der_len, sig_nid;
	int err;

	EVP_MD_CTX_init(&md_ctx);
	serial = ASN1_INTEGER_to_BN(X509_get_serialNumber(cert), NULL);

	if (!CBB_init(&out, 1024)) {
		error_msg_openssl("out of memory");
		err = -ENOMEM;
		goto out;
	}

	name_der_len = i2d_X509_NAME(X509_get_subject_name(cert), &name_der);
	if (name_der_len < 0) {
		error_msg_openssl("i2d_X509_NAME failed");
		err = -EINVAL;
		goto out;
	}

	if (!EVP_DigestSignInit(&md_ctx, NULL, md, NULL, pkey)) {
		error_msg_openssl("EVP_DigestSignInit failed");
		err = -EINVAL;
		goto out;
	}

	sig_len = EVP_PKEY_size(pkey);
	sig = libfsverity_zalloc(sig_len);
	if (!sig) {
		err = -ENOMEM;
		goto out;
	}
	if (!EVP_DigestSign(&md_ctx, sig, &sig_len, data_to_sign, data_size)) {
		error_msg_openssl("EVP_DigestSign failed");
		err = -EINVAL;
		goto out;
	}

	sig_nid = EVP_PKEY_id(pkey);
	/* To mirror OpenSSL behaviour, always use |NID_rsaEncryption| with RSA
	 * rather than the combined hash+pkey NID. */
	if (sig_nid != NID_rsaEncryption) {
		OBJ_find_sigid_by_algs(&sig_nid, EVP_MD_type(md),
				       EVP_PKEY_id(pkey));
	}

	// See https://tools.ietf.org/html/rfc2315#section-7
	if (!CBB_add_asn1(&out, &outer_seq, CBS_ASN1_SEQUENCE) ||
	    !OBJ_nid2cbb(&outer_seq, NID_pkcs7_signed) ||
	    !CBB_add_asn1(&outer_seq, &wrapped_seq, CBS_ASN1_CONTEXT_SPECIFIC |
			  CBS_ASN1_CONSTRUCTED | 0) ||
	    // See https://tools.ietf.org/html/rfc2315#section-9.1
	    !CBB_add_asn1(&wrapped_seq, &seq, CBS_ASN1_SEQUENCE) ||
	    !CBB_add_asn1_uint64(&seq, 1 /* version */) ||
	    !CBB_add_asn1(&seq, &digest_algos_set, CBS_ASN1_SET) ||
	    !CBB_add_asn1(&digest_algos_set, &digest_algo, CBS_ASN1_SEQUENCE) ||
	    !OBJ_nid2cbb(&digest_algo, EVP_MD_type(md)) ||
	    !CBB_add_asn1(&digest_algo, &null, CBS_ASN1_NULL) ||
	    !CBB_add_asn1(&seq, &content_info, CBS_ASN1_SEQUENCE) ||
	    !OBJ_nid2cbb(&content_info, NID_pkcs7_data) ||
	    !CBB_add_asn1(&seq, &signer_infos, CBS_ASN1_SET) ||
	    !CBB_add_asn1(&signer_infos, &signer_info, CBS_ASN1_SEQUENCE) ||
	    !CBB_add_asn1_uint64(&signer_info, 1 /* version */) ||
	    !CBB_add_asn1(&signer_info, &issuer_and_serial,
			  CBS_ASN1_SEQUENCE) ||
	    !CBB_add_bytes(&issuer_and_serial, name_der, name_der_len) ||
	    !BN_marshal_asn1(&issuer_and_serial, serial) ||
	    !CBB_add_asn1(&signer_info, &digest_algo, CBS_ASN1_SEQUENCE) ||
	    !OBJ_nid2cbb(&digest_algo, EVP_MD_type(md)) ||
	    !CBB_add_asn1(&digest_algo, &null, CBS_ASN1_NULL) ||
	    !CBB_add_asn1(&signer_info, &sign_algo, CBS_ASN1_SEQUENCE) ||
	    !OBJ_nid2cbb(&sign_algo, sig_nid) ||
	    !CBB_add_asn1(&sign_algo, &null, CBS_ASN1_NULL) ||
	    !CBB_add_asn1(&signer_info, &signature, CBS_ASN1_OCTETSTRING) ||
	    !CBB_add_bytes(&signature, sig, sig_len) ||
	    !CBB_finish(&out, &pkcs7_data, &pkcs7_data_len)) {
		error_msg_openssl("failed to construct PKCS#7 data");
		err = -EINVAL;
		goto out;
	}

	*sig_ret = libfsverity_memdup(pkcs7_data, pkcs7_data_len);
	if (!*sig_ret) {
		err = -ENOMEM;
		goto out;
	}
	*sig_size_ret = pkcs7_data_len;
	err = 0;
out:
	BN_free(serial);
	EVP_MD_CTX_cleanup(&md_ctx);
	CBB_cleanup(&out);
	free(sig);
	OPENSSL_free(name_der);
	OPENSSL_free(pkcs7_data);
	return err;
}

static int
load_pkcs11_private_key(const struct libfsverity_signature_params *sig_params
			__attribute__((unused)),
			EVP_PKEY **pkey_ret __attribute__((unused)))
{
	libfsverity_error_msg("BoringSSL doesn't support PKCS#11 tokens");
	return -EINVAL;
}

#else /* OPENSSL_IS_BORINGSSL */

static BIO *new_mem_buf(const void *buf, size_t size)
{
	BIO *bio;

	if (WARN_ON(size > INT_MAX))
		return NULL;

	/*
	 * Prior to OpenSSL 1.1.0, BIO_new_mem_buf() took a non-const pointer,
	 * despite still marking the resulting bio as read-only.  So cast away
	 * the const to avoid a compiler warning with older OpenSSL versions.
	 */
	bio = BIO_new_mem_buf((void *)buf, size);
	if (!bio)
		error_msg_openssl("out of memory");
	return bio;
}

static int sign_pkcs7(const void *data_to_sign, size_t data_size,
		      EVP_PKEY *pkey, X509 *cert, const EVP_MD *md,
		      u8 **sig_ret, size_t *sig_size_ret)
{
	/*
	 * PKCS#7 signing flags:
	 *
	 * - PKCS7_BINARY	signing binary data, so skip MIME translation
	 *
	 * - PKCS7_DETACHED	omit the signed data (include signature only)
	 *
	 * - PKCS7_NOATTR	omit extra authenticated attributes, such as
	 *			SMIMECapabilities
	 *
	 * - PKCS7_NOCERTS	omit the signer's certificate
	 *
	 * - PKCS7_PARTIAL	PKCS7_sign() creates a handle only, then
	 *			PKCS7_sign_add_signer() can add a signer later.
	 *			This is necessary to change the message digest
	 *			algorithm from the default of SHA-1.  Requires
	 *			OpenSSL 1.0.0 or later.
	 */
	int pkcs7_flags = PKCS7_BINARY | PKCS7_DETACHED | PKCS7_NOATTR |
			  PKCS7_NOCERTS | PKCS7_PARTIAL;
	u8 *sig;
	u32 sig_size;
	BIO *bio = NULL;
	PKCS7 *p7 = NULL;
	int err;

	bio = new_mem_buf(data_to_sign, data_size);
	if (!bio) {
		err = -ENOMEM;
		goto out;
	}

	p7 = PKCS7_sign(NULL, NULL, NULL, bio, pkcs7_flags);
	if (!p7) {
		error_msg_openssl("failed to initialize PKCS#7 signature object");
		err = -EINVAL;
		goto out;
	}

	if (!PKCS7_sign_add_signer(p7, cert, pkey, md, pkcs7_flags)) {
		error_msg_openssl("failed to add signer to PKCS#7 signature object");
		err = -EINVAL;
		goto out;
	}

	if (PKCS7_final(p7, bio, pkcs7_flags) != 1) {
		error_msg_openssl("failed to finalize PKCS#7 signature");
		err = -EINVAL;
		goto out;
	}

	BIO_free(bio);
	bio = BIO_new(BIO_s_mem());
	if (!bio) {
		error_msg_openssl("out of memory");
		err = -ENOMEM;
		goto out;
	}

	if (i2d_PKCS7_bio(bio, p7) != 1) {
		error_msg_openssl("failed to DER-encode PKCS#7 signature object");
		err = -EINVAL;
		goto out;
	}

	sig_size = BIO_get_mem_data(bio, &sig);
	*sig_ret = libfsverity_memdup(sig, sig_size);
	if (!*sig_ret) {
		err = -ENOMEM;
		goto out;
	}
	*sig_size_ret = sig_size;
	err = 0;
out:
	PKCS7_free(p7);
	BIO_free(bio);
	return err;
}

static int
load_pkcs11_private_key(const struct libfsverity_signature_params *sig_params,
			EVP_PKEY **pkey_ret)
{
	ENGINE *engine;

	if (!sig_params->pkcs11_engine) {
		libfsverity_error_msg("no PKCS#11 engine specified");
		return -EINVAL;
	}
	if (!sig_params->pkcs11_module) {
		libfsverity_error_msg("no PKCS#11 module specified");
		return -EINVAL;
	}
	ENGINE_load_dynamic();
	engine = ENGINE_by_id("dynamic");
	if (!engine) {
		error_msg_openssl("failed to initialize OpenSSL PKCS#11 engine");
		return -EINVAL;
	}
	if (!ENGINE_ctrl_cmd_string(engine, "SO_PATH",
				    sig_params->pkcs11_engine, 0) ||
	    !ENGINE_ctrl_cmd_string(engine, "ID", "pkcs11", 0) ||
	    !ENGINE_ctrl_cmd_string(engine, "LIST_ADD", "1", 0) ||
	    !ENGINE_ctrl_cmd_string(engine, "LOAD", NULL, 0) ||
	    !ENGINE_ctrl_cmd_string(engine, "MODULE_PATH",
				    sig_params->pkcs11_module, 0) ||
	    !ENGINE_init(engine)) {
		error_msg_openssl("failed to initialize OpenSSL PKCS#11 engine");
		ENGINE_free(engine);
		return -EINVAL;
	}
	*pkey_ret = ENGINE_load_private_key(engine, sig_params->pkcs11_keyid,
					    NULL, NULL);
	ENGINE_finish(engine);
	ENGINE_free(engine);
	if (!*pkey_ret) {
		error_msg_openssl("failed to load private key from PKCS#11 token");
		return -EINVAL;
	}
	return 0;
}

#endif /* !OPENSSL_IS_BORINGSSL */

/* Get a private key, either from disk or from a PKCS#11 token. */
static int
get_private_key(const struct libfsverity_signature_params *sig_params,
		EVP_PKEY **pkey_ret)
{
	if (sig_params->pkcs11_engine || sig_params->pkcs11_module ||
	    sig_params->pkcs11_keyid) {
		if (sig_params->keyfile) {
			libfsverity_error_msg("private key must be specified either by file or by PKCS#11 token, not both");
			return -EINVAL;
		}
		return load_pkcs11_private_key(sig_params, pkey_ret);
	}
	if (!sig_params->keyfile) {
		libfsverity_error_msg("no private key specified");
		return -EINVAL;
	}
	return read_private_key(sig_params->keyfile, pkey_ret);
}

LIBEXPORT int
libfsverity_sign_digest(const struct libfsverity_digest *digest,
			const struct libfsverity_signature_params *sig_params,
			u8 **sig_ret, size_t *sig_size_ret)
{
	const struct fsverity_hash_alg *hash_alg;
	X509 *cert = NULL;
	EVP_PKEY *pkey = NULL;
	const EVP_MD *md;
	struct fsverity_formatted_digest *d = NULL;
	int err;

	if (!digest || !sig_params || !sig_ret || !sig_size_ret)  {
		libfsverity_error_msg("missing required parameters for sign_digest");
		return -EINVAL;
	}

	if (!libfsverity_mem_is_zeroed(sig_params->reserved1,
				       sizeof(sig_params->reserved1)) ||
	    !libfsverity_mem_is_zeroed(sig_params->reserved2,
				       sizeof(sig_params->reserved2))) {
		libfsverity_error_msg("reserved bits set in signature_params");
		return -EINVAL;
	}

	hash_alg = libfsverity_find_hash_alg_by_num(digest->digest_algorithm);
	if (!hash_alg || digest->digest_size != hash_alg->digest_size) {
		libfsverity_error_msg("malformed fsverity digest");
		return -EINVAL;
	}

	err = read_certificate(sig_params->certfile, &cert);
	if (err)
		goto out;

	err = get_private_key(sig_params, &pkey);
	if (err)
		goto out;

	OpenSSL_add_all_digests();
	md = EVP_get_digestbyname(hash_alg->name);
	if (!md) {
		libfsverity_error_msg("'%s' algorithm not found in OpenSSL library",
				      hash_alg->name);
		err = -ENOPKG;
		goto out;
	}

	d = libfsverity_zalloc(sizeof(*d) + digest->digest_size);
	if (!d) {
		err = -ENOMEM;
		goto out;
	}
	memcpy(d->magic, "FSVerity", 8);
	d->digest_algorithm = cpu_to_le16(digest->digest_algorithm);
	d->digest_size = cpu_to_le16(digest->digest_size);
	memcpy(d->digest, digest->digest, digest->digest_size);

	err = sign_pkcs7(d, sizeof(*d) + digest->digest_size,
			 pkey, cert, md, sig_ret, sig_size_ret);
 out:
	X509_free(cert);
	EVP_PKEY_free(pkey);
	free(d);
	return err;
}
