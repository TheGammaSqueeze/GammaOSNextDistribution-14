/*
 * Copyright (C) 2014-2016 The Android Open Source Project
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

#include <assert.h>
#include <lk/compiler.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>

#include <openssl/evp.h>
#include <openssl/hmac.h>
#include <openssl/rand.h>

#include "crypt.h"

/* Backwards compatability */
#if OPENSSL_VERSION_NUMBER < 0x10100000
static void EVP_CIPHER_CTX_reset(EVP_CIPHER_CTX* ctx) {
    EVP_CIPHER_CTX_cleanup(ctx);
    EVP_CIPHER_CTX_init(ctx);
}

static HMAC_CTX* HMAC_CTX_new(void) {
    HMAC_CTX* ctx = malloc(sizeof(HMAC_CTX));
    if (ctx) {
        HMAC_CTX_init(ctx);
    }
    return ctx;
}

static void HMAC_CTX_reset(HMAC_CTX* ctx) {
    HMAC_CTX_cleanup(ctx);
    HMAC_CTX_init(ctx);
}

static void HMAC_CTX_free(HMAC_CTX* ctx) {
    if (ctx) {
        HMAC_CTX_cleanup(ctx);
        free(ctx);
    }
}
#endif

/*
 * The OpenSSL 1.1.0 API requires we allocate these dynamically. Cache them
 * globally to avoid alocator thrash and the potential for another dynamic
 * failure.
 */
static EVP_CIPHER_CTX* cipher_ctx;
static HMAC_CTX* hmac_ctx;

void crypt_init(void) {
    assert(!cipher_ctx);
    assert(!hmac_ctx);

    cipher_ctx = EVP_CIPHER_CTX_new();
    assert(cipher_ctx);

    hmac_ctx = HMAC_CTX_new();
    assert(hmac_ctx);
}

void crypt_shutdown(void) {
    EVP_CIPHER_CTX_free(cipher_ctx);
    cipher_ctx = NULL;

    HMAC_CTX_free(hmac_ctx);
    hmac_ctx = NULL;
}

/**
 * crypt - Helper function for encrypt and decrypt.
 * @key:            Key object.
 * @data_in_out:    Data to encrypt or decrypt.
 * @data_size:      Number of bytes in @data_in_out.
 * @iv:             Initialization vector to use for Cipher Block Chaining.
 * @encrypt:        %true to select encrypt, %false to select decrypt.
 *
 * Return: 0 on success, -1 if an error was detected.
 */
static int crypt(const struct key* key,
                 void* data_in_out,
                 size_t data_size,
                 const struct iv* iv,
                 bool encrypt) {
    int evp_ret;
    const EVP_CIPHER* cipher;
    int out_data_size;
    size_t key_len;

    /*
     * Make sure iv is large enough. Current implementation allows static
     * check.
     * TODO: Switch to runtime check for selcted cipher if EVP_MAX_IV_LENGTH
     * changes to cover larger ivs used by other cipers.
     */
    STATIC_ASSERT(sizeof(*iv) >= EVP_MAX_IV_LENGTH);

    cipher = EVP_aes_128_ctr();
    key_len = EVP_CIPHER_key_length(cipher);
    if (key_len > sizeof(*key)) {
        fprintf(stderr, "key too small for selected cipher, %zd < %zd\n",
                sizeof(*key), key_len);
        evp_ret = 0;
        goto err;
    }

    assert(cipher_ctx);
    EVP_CIPHER_CTX_reset(cipher_ctx);

    evp_ret = EVP_CipherInit_ex(cipher_ctx, cipher, NULL, key->byte, iv->byte,
                                encrypt);
    if (!evp_ret) {
        fprintf(stderr, "EVP_CipherInit_ex failed\n");
        goto err;
    }

    evp_ret = EVP_CIPHER_CTX_set_padding(cipher_ctx, 0);
    if (!evp_ret) {
        fprintf(stderr, "EVP_CIPHER_CTX_set_padding failed\n");
        goto err;
    }

    evp_ret = EVP_CipherUpdate(cipher_ctx, data_in_out, &out_data_size,
                               data_in_out, data_size);
    if (!evp_ret) {
        fprintf(stderr, "EVP_CipherUpdate failed\n");
        goto err;
    }
    if (out_data_size != (int)data_size) {
        fprintf(stderr, "bad output data size %d != %zd\n", out_data_size,
                data_size);
        evp_ret = 0;
        goto err;
    }

    evp_ret = EVP_CipherFinal_ex(cipher_ctx, NULL, &out_data_size);
    if (!evp_ret) {
        fprintf(stderr, "EVP_CipherFinal_ex failed\n");
        goto err;
    }

err:
    return evp_ret ? 0 : -1;
}

/**
 * str_hash - Cacluate a 64-bit hash for a string.
 * @str:        Key object.
 *
 * Return: Low 8 bytes of SHA1 hash as a little endian 64 bit value.
 */
uint64_t str_hash(const char* str) {
    int evp_ret;
    size_t len = strlen(str);
    uint8_t md[EVP_MAX_MD_SIZE];
    uint64_t ret;

    evp_ret = EVP_Digest(str, len, md, NULL, EVP_sha1(), NULL);
    if (!evp_ret) {
        fprintf(stderr, "EVP_Digest failed\n");
        assert(false);
        return 0;
    }
    STATIC_ASSERT(sizeof(ret) <= sizeof(md));
    memcpy(&ret, md, sizeof(ret));
    return ret;
}

/**
 * calculate_mac - Calulate keyed-hash message authentication code (HMAC SHA256)
 * @key:            Key object.
 * @mac:            Mac object to return calulated mac in.
 * @data:           Data to calculate mac for.
 * @data_size:      Number of bytes in @data.
 *
 * Return: 0 on success, -1 if an error was detected.
 */
int calculate_mac(const struct key* key,
                  struct mac* mac,
                  const void* data,
                  size_t data_size) {
    int hmac_ret;
    unsigned int md_len;
    unsigned char mac_buf[EVP_MAX_MD_SIZE];

    assert(hmac_ctx);
    HMAC_CTX_reset(hmac_ctx);

    hmac_ret = HMAC_Init_ex(hmac_ctx, key, sizeof(*key), EVP_sha256(), NULL);
    if (!hmac_ret) {
        fprintf(stderr, "HMAC_Init_ex failed\n");
        goto err;
    }

    hmac_ret = HMAC_Update(hmac_ctx, data, data_size);
    if (!hmac_ret) {
        fprintf(stderr, "HMAC_Update failed\n");
        goto err;
    }

    hmac_ret = HMAC_Final(hmac_ctx, mac_buf, &md_len);
    if (!hmac_ret) {
        fprintf(stderr, "HMAC_Final failed\n");
        goto err;
    }
    if (md_len < sizeof(*mac)) {
        fprintf(stderr, "bad md_len %d < %zd\n", md_len, sizeof(*mac));
        hmac_ret = 0;
        goto err;
    }
    memcpy(mac, mac_buf, sizeof(*mac));

err:
    return hmac_ret ? 0 : -1;
}

/**
 * generate_iv - Generate a random iv value.
 * @iv_out:     IV object.
 *
 * Return: 0 on success, -1 if an error was detected.
 */
int generate_iv(struct iv* iv_out) {
    int rand_ret;

    rand_ret = RAND_bytes(iv_out->byte, sizeof(iv_out->byte));
    if (!rand_ret) {
        fprintf(stderr, "RAND_bytes failed\n");
    }
    return rand_ret ? 0 : -1;
}

/**
 * encrypt - Encrypt data using AES-128-CTR.
 * @key:            Key object.
 * @data_in_out:    Data to encrypt.
 * @data_size:      Number of bytes in @data_in_out, but be a multiple of
 *                  AES_BLOCK_SIZE.
 * @iv_in:          Initialization vector to use for Cipher Block Chaining.
 *
 * Return: 0 on success, -1 if an error was detected.
 */
int encrypt(const struct key* key,
            void* data_in_out,
            size_t data_size,
            const struct iv* iv_in) {
    return crypt(key, data_in_out, data_size, iv_in, true);
}

/**
 * encrypt - Decrypt data using AES-128-CTR.
 * @key:            Key object.
 * @data_in_out:    Data to decrypt.
 * @data_size:      Number of bytes in @data_in_out, but be a multiple of
 *                  AES_BLOCK_SIZE.
 * @iv_in:          Initialization vector to use for Cipher Block Chaining.
 *
 * Return: 0 on success, -1 if an error was detected.
 */
int decrypt(const struct key* key,
            void* data_in_out,
            size_t data_size,
            const struct iv* iv_in) {
    return crypt(key, data_in_out, data_size, iv_in, false);
}
