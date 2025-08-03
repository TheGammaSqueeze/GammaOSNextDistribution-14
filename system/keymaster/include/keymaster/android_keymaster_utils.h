/*
 * Copyright 2014 The Android Open Source Project
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

#pragma once

#include <utility>

#include <stdint.h>
#include <time.h>  // for time_t.

#include <hardware/keymaster_defs.h>

#include <keymaster/UniquePtr.h>
#include <keymaster/mem.h>
#include <keymaster/serializable.h>

#ifndef __has_cpp_attribute
#define __has_cpp_attribute(x) 0
#endif

// Mark intentional fallthroughts in switch statements to silence
// -Wimplicit-fallthrough.
#if __has_cpp_attribute(clang::fallthrough)
#define FALLTHROUGH [[clang::fallthrough]]
#else
#define FALLTHROUGH
#endif

namespace keymaster {

/**
 * Convert the specified time value into "Java time", which is a signed 64-bit integer representing
 * elapsed milliseconds since Jan 1, 1970.
 */
inline int64_t java_time(time_t time) {
    // The exact meaning of a time_t value is implementation-dependent.  If this code is ported to a
    // platform that doesn't define it as "seconds since Jan 1, 1970 UTC", this function will have
    // to be revised.
    return static_cast<int64_t>(time) * 1000;
}

/**
 * Convert any unsigned integer from network to host order.  We implement this here rather than
 * using the functions from arpa/inet.h because the TEE doesn't have inet.h.  This isn't the most
 * efficient implementation, but the compiler should unroll the loop and tighten it up.
 */
template <typename T> T ntoh(T t) {
    const uint8_t* byte_ptr = reinterpret_cast<const uint8_t*>(&t);
    T retval = 0;
    for (size_t i = 0; i < sizeof(t); ++i) {
        retval <<= 8;
        retval |= byte_ptr[i];
    }
    return retval;
}

/**
 * Convert any unsigned integer from host to network order.  We implement this here rather than
 * using the functions from arpa/inet.h because the TEE doesn't have inet.h.  This isn't the most
 * efficient implementation, but the compiler should unroll the loop and tighten it up.
 */
template <typename T> T hton(T t) {
    T retval;
    uint8_t* byte_ptr = reinterpret_cast<uint8_t*>(&retval);
    for (size_t i = sizeof(t); i > 0; --i) {
        byte_ptr[i - 1] = t & 0xFF;
        t >>= 8;
    }
    return retval;
}

inline const uint8_t* const& accessBlobData(const keymaster_key_blob_t* blob) {
    return blob->key_material;
}
inline const uint8_t*& accessBlobData(keymaster_key_blob_t* blob) {
    return blob->key_material;
}
inline const size_t& accessBlobSize(const keymaster_key_blob_t* blob) {
    return blob->key_material_size;
}
inline size_t& accessBlobSize(keymaster_key_blob_t* blob) {
    return blob->key_material_size;
}

inline const uint8_t* const& accessBlobData(const keymaster_blob_t* blob) {
    return blob->data;
}
inline const uint8_t*& accessBlobData(keymaster_blob_t* blob) {
    return blob->data;
}
inline const size_t& accessBlobSize(const keymaster_blob_t* blob) {
    return blob->data_length;
}
inline size_t& accessBlobSize(keymaster_blob_t* blob) {
    return blob->data_length;
}

/**
 * TKeymasterBlob is a very simple extension of the C structs keymaster_blob_t and
 * keymaster_key_blob_t.  It manages its own memory, which makes avoiding memory leaks
 * much easier.
 */
template <typename BlobType> struct TKeymasterBlob : public BlobType {
    TKeymasterBlob() {
        accessBlobData(this) = nullptr;
        accessBlobSize(this) = 0;
    }

    TKeymasterBlob(const uint8_t* data, size_t size) {
        accessBlobSize(this) = 0;
        accessBlobData(this) = dup_buffer(data, size);
        if (accessBlobData(this)) accessBlobSize(this) = size;
    }

    explicit TKeymasterBlob(size_t size) {
        accessBlobSize(this) = 0;
        accessBlobData(this) = new (std::nothrow) uint8_t[size];
        if (accessBlobData(this)) accessBlobSize(this) = size;
    }

    explicit TKeymasterBlob(const BlobType& blob) {
        accessBlobSize(this) = 0;
        accessBlobData(this) = dup_buffer(accessBlobData(&blob), accessBlobSize(&blob));
        if (accessBlobData(this)) accessBlobSize(this) = accessBlobSize(&blob);
    }

    template <size_t N> explicit TKeymasterBlob(const uint8_t (&data)[N]) {
        accessBlobSize(this) = 0;
        accessBlobData(this) = dup_buffer(data, N);
        if (accessBlobData(this)) accessBlobSize(this) = N;
    }

    TKeymasterBlob(const TKeymasterBlob& blob) {
        accessBlobSize(this) = 0;
        accessBlobData(this) = dup_buffer(accessBlobData(&blob), accessBlobSize(&blob));
        if (accessBlobData(this)) accessBlobSize(this) = accessBlobSize(&blob);
    }

    TKeymasterBlob(TKeymasterBlob&& rhs) {
        accessBlobSize(this) = accessBlobSize(&rhs);
        accessBlobData(this) = accessBlobData(&rhs);
        accessBlobSize(&rhs) = 0;
        accessBlobData(&rhs) = nullptr;
    }

    TKeymasterBlob& operator=(const TKeymasterBlob& blob) {
        if (this != &blob) {
            Clear();
            accessBlobData(this) = dup_buffer(accessBlobData(&blob), accessBlobSize(&blob));
            accessBlobSize(this) = accessBlobSize(&blob);
        }
        return *this;
    }

    TKeymasterBlob& operator=(TKeymasterBlob&& rhs) {
        if (this != &rhs) {
            Clear();
            accessBlobSize(this) = accessBlobSize(&rhs);
            accessBlobData(this) = accessBlobData(&rhs);
            accessBlobSize(&rhs) = 0;
            accessBlobData(&rhs) = nullptr;
        }
        return *this;
    }

    ~TKeymasterBlob() { Clear(); }

    const uint8_t* begin() const { return accessBlobData(this); }
    const uint8_t* end() const { return accessBlobData(this) + accessBlobSize(this); }

    size_t size() const { return accessBlobSize(this); }

    void Clear() {
        if (accessBlobSize(this)) {
            memset_s(const_cast<uint8_t*>(accessBlobData(this)), 0, accessBlobSize(this));
        }
        delete[] accessBlobData(this);
        accessBlobData(this) = nullptr;
        accessBlobSize(this) = 0;
    }

    const uint8_t* Reset(size_t new_size) {
        Clear();
        accessBlobData(this) = new (std::nothrow) uint8_t[new_size];
        if (accessBlobData(this)) accessBlobSize(this) = new_size;
        return accessBlobData(this);
    }

    // The key_material in keymaster_key_blob_t is const, which is the right thing in most
    // circumstances, but occasionally we do need to write into it.  This method exposes a non-const
    // version of the pointer.  Use sparingly.
    uint8_t* writable_data() { return const_cast<uint8_t*>(accessBlobData(this)); }

    BlobType release() {
        BlobType tmp = {accessBlobData(this), accessBlobSize(this)};
        accessBlobData(this) = nullptr;
        accessBlobSize(this) = 0;
        return tmp;
    }

    size_t SerializedSize() const { return sizeof(uint32_t) + accessBlobSize(this); }
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const {
        return append_size_and_data_to_buf(buf, end, accessBlobData(this), accessBlobSize(this));
    }

    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) {
        Clear();
        UniquePtr<uint8_t[]> tmp;
        if (!copy_size_and_data_from_buf(buf_ptr, end, &accessBlobSize(this), &tmp)) {
            accessBlobData(this) = nullptr;
            accessBlobSize(this) = 0;
            return false;
        }
        accessBlobData(this) = tmp.release();
        return true;
    }
};

typedef TKeymasterBlob<keymaster_blob_t> KeymasterBlob;
typedef TKeymasterBlob<keymaster_key_blob_t> KeymasterKeyBlob;

struct Characteristics_Delete {
    void operator()(keymaster_key_characteristics_t* p) {
        keymaster_free_characteristics(p);
        free(p);
    }
};

/**
 * Attempt to determine an arbitrary elliptic curve from a key size.  If the key size
 * is ambiguous, KM_ERROR_UNSUPPORTED_KEY_SIZE is returned.
 */
keymaster_error_t EllipticKeySizeToCurve(uint32_t key_size_bits, keymaster_ec_curve_t* curve);

/**
 * Attempt to determine an ECDSA curve from a key size, where the key is know to be
 * an ECDSA (i.e. non-Edwards) specifically.
 */
keymaster_error_t EcKeySizeToCurve(uint32_t key_size_bits, keymaster_ec_curve_t* curve);

keymaster_error_t EcCurveToKeySize(keymaster_ec_curve_t curve, uint32_t* key_size_bits);

template <class F> class final_action {
  public:
    explicit final_action(F f) : f_(std::move(f)) {}
    ~final_action() { f_(); }

  private:
    F f_;
};

template <class F> inline final_action<F> finally(const F& f) {
    return final_action<F>(f);
}

struct CertificateChain : public keymaster_cert_chain_t {
    CertificateChain() : keymaster_cert_chain_t{} {}

    /**
     * Create a chain with space allocated for `length` certificates.  If allocation fails,
     * `entries` will be null.
     */
    explicit CertificateChain(size_t length) : keymaster_cert_chain_t{} {
        entries = new (std::nothrow) keymaster_blob_t[length];
        if (!entries) return;
        entry_count = length;
        for (size_t i = 0; i < entry_count; ++i) {
            entries[i] = {};
        }
    }

    CertificateChain(CertificateChain&& other) : keymaster_cert_chain_t{}
    { *this = std::move(other); }

    ~CertificateChain() { Clear(); }

    CertificateChain& operator=(CertificateChain&& other) {
        Clear();
        (keymaster_cert_chain_t&)(*this) = (keymaster_cert_chain_t&)(other);
        (keymaster_cert_chain_t&)(other) = {};
        return *this;
    }

    /**
     * Clone `other`.  If anything fails, `entries` will be null.
     */
    static CertificateChain clone(const keymaster_cert_chain_t& other) {
        CertificateChain retval;
        retval.entry_count = other.entry_count;
        retval.entries = new (std::nothrow) keymaster_blob_t[retval.entry_count];
        if (!retval.entries) return {};

        for (auto& entry : retval) {
            entry = {};
        }

        for (size_t i = 0; i < retval.entry_count; ++i) {
            retval.entries[i].data_length = other.entries[i].data_length;
            retval.entries[i].data = new (std::nothrow) uint8_t[retval.entries[i].data_length];
            if (!retval.entries[i].data) return {};

            memcpy(const_cast<uint8_t*>(retval.entries[i].data), other.entries[i].data,
                   retval.entries[i].data_length);
        }

        return retval;
    }

    explicit operator bool() { return entries; }

    keymaster_blob_t* begin() { return entries; }
    const keymaster_blob_t* begin() const { return entries; }
    keymaster_blob_t* end() { return entries + entry_count; }
    const keymaster_blob_t* end() const { return entries + entry_count; }

    // Insert the provided blob at the front of the chain.  CertificateChain takes ownership of the
    // contents of `new_entry`.
    bool push_front(const keymaster_blob_t& new_entry) {
        keymaster_blob_t* new_entries = new (std::nothrow) keymaster_blob_t[entry_count + 1];
        if (!new_entries) return false;

        new_entries[0] = new_entry;
        for (size_t i = 1; i < entry_count + 1; ++i) {
            new_entries[i] = entries[i - 1];
        }

        delete[] entries;
        entries = new_entries;
        ++entry_count;
        return true;
    }

    keymaster_cert_chain_t release() {
        keymaster_cert_chain_t retval = *this;
        entries = nullptr;
        entry_count = 0;
        return retval;
    }

    void Clear() {
        if (entries) {
            for (size_t i = 0; i < entry_count; ++i) {
                delete[] entries[i].data;
            }
            delete[] entries;
        }
        entry_count = 0;
        entries = nullptr;
    }
};

// Per RFC 5280 4.1.2.5, an undefined expiration (not-after) field should be set to GeneralizedTime
// 999912312359559, which is 253402300799000 ms from Jan 1, 1970.
constexpr int64_t kUndefinedExpirationDateTime = 253402300799000;

// A confirmation token is the output of HMAC-SHA256. */
constexpr size_t kConfirmationTokenSize = 32;

// Defined in hardware/interfaces/confirmationui/1.0/IConfirmationResultCallback.hal
constexpr const char kConfirmationTokenMessageTag[] = "confirmation token";

constexpr size_t kConfirmationTokenMessageTagSize = sizeof(kConfirmationTokenMessageTag) - 1;

// Maximum supported size for CBOR which includes prompt and
// extra_data as returned by the ConfirmationUI. See
// https://source.android.com/security/protected-confirmation/implementation
constexpr size_t kConfirmationMessageMaxSize = 6144;

}  // namespace keymaster
