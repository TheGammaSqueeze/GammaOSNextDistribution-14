/* SPDX-License-Identifier: MIT */
/*
 * libfsverity API
 *
 * Copyright 2018 Google LLC
 * Copyright (C) 2020 Facebook
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */

#ifndef LIBFSVERITY_H
#define LIBFSVERITY_H

#ifdef __cplusplus
extern "C" {
#endif

#include <errno.h>
#include <stddef.h>
#include <stdint.h>

#define FSVERITY_UTILS_MAJOR_VERSION	1
#define FSVERITY_UTILS_MINOR_VERSION	5

#define FS_VERITY_HASH_ALG_SHA256       1
#define FS_VERITY_HASH_ALG_SHA512       2

/**
 * struct libfsverity_merkle_tree_params - properties of a file's Merkle tree
 *
 * Zero this, then fill in at least @version and @file_size.
 */
struct libfsverity_merkle_tree_params {

	/** @version: must be 1 */
	uint32_t version;

	/**
	 * @hash_algorithm: one of FS_VERITY_HASH_ALG_*, or 0 to use the default
	 * of FS_VERITY_HASH_ALG_SHA256
	 */
	uint32_t hash_algorithm;

	/** @file_size: the file size in bytes */
	uint64_t file_size;

	/**
	 * @block_size: the Merkle tree block size in bytes, or 0 to use the
	 * default of 4096 bytes
	 */
	uint32_t block_size;

	/** @salt_size: the salt size in bytes, or 0 if unsalted */
	uint32_t salt_size;

	/** @salt: pointer to the salt, or NULL if unsalted */
	const uint8_t *salt;

	/** @reserved1: must be 0 */
	uint64_t reserved1[8];

	/**
	 * @metadata_callbacks: if non-NULL, this gives a set of callback
	 * functions to which libfsverity_compute_digest() will pass the Merkle
	 * tree blocks and fs-verity descriptor after they are computed.
	 * Normally this isn't useful, but this can be needed in rare cases
	 * where the metadata needs to be consumed by something other than one
	 * of the native Linux kernel implementations of fs-verity.
	 */
	const struct libfsverity_metadata_callbacks *metadata_callbacks;

	/** @reserved2: must be 0 */
	uintptr_t reserved2[7];
};

struct libfsverity_digest {
	uint16_t digest_algorithm;	/* one of FS_VERITY_HASH_ALG_* */
	uint16_t digest_size;		/* digest size in bytes */
	uint8_t digest[];		/* the actual digest */
};

/**
 * struct libfsverity_signature_params - certificate and private key information
 *
 * Zero this, then set @certfile.  Then, to specify the private key by key file,
 * set @keyfile.  Alternatively, to specify the private key by PKCS#11 token,
 * set @pkcs11_engine, @pkcs11_module, and optionally @pkcs11_keyid.
 *
 * Support for PKCS#11 tokens is unavailable when libfsverity was linked to
 * BoringSSL rather than OpenSSL.
 */
struct libfsverity_signature_params {

	/** @keyfile: the path to the key file in PEM format, when applicable */
	const char *keyfile;

	/** @certfile: the path to the certificate file in PEM format */
	const char *certfile;

	/** @reserved1: must be 0 */
	uint64_t reserved1[8];

	/**
	 * @pkcs11_engine: the path to the PKCS#11 engine .so file, when
	 * applicable
	 */
	const char *pkcs11_engine;

	/**
	 * @pkcs11_module: the path to the PKCS#11 module .so file, when
	 * applicable
	 */
	const char *pkcs11_module;

	/** @pkcs11_keyid: the PKCS#11 key identifier, when applicable */
	const char *pkcs11_keyid;

	/** @reserved2: must be 0 */
	uintptr_t reserved2[5];
};

struct libfsverity_metadata_callbacks {

	/** @ctx: context passed to the below callbacks (opaque to library) */
	void *ctx;

	/**
	 * @merkle_tree_size: if non-NULL, called with the total size of the
	 * Merkle tree in bytes, prior to any call to @merkle_tree_block.  Must
	 * return 0 on success, or a negative errno value on failure.
	 */
	int (*merkle_tree_size)(void *ctx, uint64_t size);

	/**
	 * @merkle_tree_block: if non-NULL, called with each block of the
	 * Merkle tree after it is computed.  The offset is the offset in bytes
	 * to the block within the Merkle tree, using the Merkle tree layout
	 * used by FS_IOC_READ_VERITY_METADATA.  The offsets won't necessarily
	 * be in increasing order.  Must return 0 on success, or a negative
	 * errno value on failure.
	 */
	int (*merkle_tree_block)(void *ctx, const void *block, size_t size,
				 uint64_t offset);

	/**
	 * @descriptor: if non-NULL, called with the fs-verity descriptor after
	 * it is computed.  Must return 0 on success, or a negative errno value
	 * on failure.
	 */
	int (*descriptor)(void *ctx, const void *descriptor, size_t size);
};

/*
 * libfsverity_read_fn_t - callback that incrementally provides a file's data
 * @fd: the user-provided "file descriptor" (opaque to library)
 * @buf: buffer into which to read the next chunk of the file's data
 * @count: number of bytes to read in this chunk
 *
 * Must return 0 on success (all 'count' bytes read), or a negative errno value
 * on failure.
 */
typedef int (*libfsverity_read_fn_t)(void *fd, void *buf, size_t count);

/**
 * libfsverity_compute_digest() - Compute digest of a file
 *          A fs-verity file digest is the hash of a file's fsverity_descriptor.
 *          Not to be confused with a traditional file digest computed over the
 *          entire file, or with the bare fsverity_descriptor::root_hash.
 * @fd: context that will be passed to @read_fn
 * @read_fn: a function that will read the data of the file
 * @params: Pointer to the Merkle tree parameters
 * @digest_ret: Pointer to pointer for computed digest.
 *
 * Returns:
 * * 0 for success, -EINVAL for invalid input arguments, -ENOMEM if libfsverity
 *   failed to allocate memory, or an error returned by @read_fn or by one of
 *   the @params->metadata_callbacks.
 * * digest_ret returns a pointer to the digest on success. The digest object
 *   is allocated by libfsverity and must be freed by the caller using free().
 */
int
libfsverity_compute_digest(void *fd, libfsverity_read_fn_t read_fn,
			   const struct libfsverity_merkle_tree_params *params,
			   struct libfsverity_digest **digest_ret);

/**
 * libfsverity_sign_digest() - Sign a file for built-in signature verification
 *	    Sign a file digest in a way that is compatible with the Linux
 *	    kernel's fs-verity built-in signature verification support.  The
 *	    resulting signature will be a PKCS#7 message in DER format.  Note
 *	    that this is not the only way to do signatures with fs-verity.  For
 *	    more details, refer to the fsverity-utils README and to
 *	    Documentation/filesystems/fsverity.rst in the kernel source tree.
 * @digest: pointer to previously computed digest
 * @sig_params: pointer to the certificate and private key information
 * @sig_ret: Pointer to pointer for signed digest
 * @sig_size_ret: Pointer to size of signed return digest
 *
 * Return:
 * * 0 for success, -EINVAL for invalid input arguments or if the cryptographic
 *   operations to sign the digest failed, -EBADMSG if the key and/or
 *   certificate file is invalid, or another negative errno value.
 * * sig_ret returns a pointer to the signed digest on success. This object
 *   is allocated by libfsverity and must be freed by the caller using free().
 * * sig_size_ret returns the size (in bytes) of the signed digest on success.
 */
int
libfsverity_sign_digest(const struct libfsverity_digest *digest,
			const struct libfsverity_signature_params *sig_params,
			uint8_t **sig_ret, size_t *sig_size_ret);

/**
 * libfsverity_enable() - Enable fs-verity on a file
 * @fd: read-only file descriptor to the file
 * @params: pointer to the Merkle tree parameters
 *
 * This is a simple wrapper around the FS_IOC_ENABLE_VERITY ioctl.
 *
 * Return: 0 on success, -EINVAL for invalid arguments, or a negative errno
 *	   value from the FS_IOC_ENABLE_VERITY ioctl.  See
 *	   Documentation/filesystems/fsverity.rst in the kernel source tree for
 *	   the possible error codes from FS_IOC_ENABLE_VERITY.
 */
int
libfsverity_enable(int fd, const struct libfsverity_merkle_tree_params *params);

/**
 * libfsverity_enable_with_sig() - Enable fs-verity on a file, with a signature
 * @fd: read-only file descriptor to the file
 * @params: pointer to the Merkle tree parameters
 * @sig: pointer to the file's signature
 * @sig_size: size of the file's signature in bytes
 *
 * Like libfsverity_enable(), but allows specifying a built-in signature (i.e. a
 * singature created with libfsverity_sign_digest()) to associate with the file.
 * This is only needed if the in-kernel signature verification support is being
 * used; it is not needed if signatures are being verified in userspace.
 *
 * If @sig is NULL and @sig_size is 0, this is the same as libfsverity_enable().
 *
 * Return: See libfsverity_enable().
 */
int
libfsverity_enable_with_sig(int fd,
			    const struct libfsverity_merkle_tree_params *params,
			    const uint8_t *sig, size_t sig_size);

/**
 * libfsverity_find_hash_alg_by_name() - Find hash algorithm by name
 * @name: Pointer to name of hash algorithm
 *
 * Return: The hash algorithm number, or zero if not found.
 */
uint32_t libfsverity_find_hash_alg_by_name(const char *name);

/**
 * libfsverity_get_digest_size() - Get size of digest for a given algorithm
 * @alg_num: Number of hash algorithm
 *
 * Return: size of digest in bytes, or -1 if algorithm is unknown.
 */
int libfsverity_get_digest_size(uint32_t alg_num);

/**
 * libfsverity_get_hash_name() - Get name of hash algorithm by number
 * @alg_num: Number of hash algorithm
 *
 * Return: The name of the hash algorithm, or NULL if algorithm is unknown.
 */
const char *libfsverity_get_hash_name(uint32_t alg_num);

/**
 * libfsverity_set_error_callback() - Set callback to handle error messages
 * @cb: the callback function.
 *
 * If a callback is already set, it is replaced.  @cb may be NULL in order to
 * remove the existing callback.
 */
void libfsverity_set_error_callback(void (*cb)(const char *msg));

#ifdef __cplusplus
}
#endif

#endif /* LIBFSVERITY_H */
