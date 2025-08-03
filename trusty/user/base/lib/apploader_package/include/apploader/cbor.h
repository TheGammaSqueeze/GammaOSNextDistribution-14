/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <cassert>
#include <cstring>
#include <map>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

#include <lk/compiler.h>

#include <dice/cbor_reader.h>
#include <dice/cbor_writer.h>

namespace cbor {

/**
 * readCborBoolean() - Read boolean value from CBOR input object.
 * @in: Initialized CBOR input object to read from
 *
 * Return: boolean if read succeeds, %nullopt otherwise
 */
static inline std::optional<bool> readCborBoolean(struct CborIn& in) {
    if (CborReadTrue(&in) == CBOR_READ_RESULT_OK) {
        return true;
    } else if (CborReadFalse(&in) == CBOR_READ_RESULT_OK) {
        return false;
    } else {
        return std::nullopt;
    }
}

/**
 * encodedSizeOf() - Get number of bytes required to encode CBOR item.
 * @val: Value (for types with no content) or length of CBOR item.
 *
 * Figure how many bytes we need to encode a CBOR item with a particular value
 * or item count. This function is not limited to determining the size of
 * unsigned integers since the encoding of arrays, maps, and scalars use a
 * similar encoding. For CBOR types that have content, the result does not
 * include the bytes required to store the content itself.
 *
 * Return: Encoded size of CBOR value less the size of its content if any.
 */
static inline size_t encodedSizeOf(uint64_t val) {
    uint8_t buffer[16];
    struct CborOut out;
    CborOutInit(buffer, sizeof(buffer), &out);
    CborWriteUint(val, &out);
    assert(!CborOutOverflowed(&out));
    return CborOutSize(&out);
}

/**
 * encodedSizeOfInt() - Get number of bytes required to encode signed integer.
 * @val: Integer to encode.
 *
 * Return: Encoded size of signed integer.
 */
static inline size_t encodedSizeOfInt(int64_t val) {
    uint8_t buffer[16];
    struct CborOut out;
    CborOutInit(buffer, sizeof(buffer), &out);
    CborWriteInt(val, &out);
    assert(!CborOutOverflowed(&out));
    return CborOutSize(&out);
}

/**
 * Sorts the map in canonical order, as defined in RFC 7049.
 * https://datatracker.ietf.org/doc/html/rfc7049#section-3.9
 */
template <typename T>
struct CBORCompare {
    constexpr bool operator()(const std::vector<T>& a,
                              const std::vector<T>& b) const {
        const std::basic_string_view<T> av = {a.data(), a.size()};
        const std::basic_string_view<T> bv = {b.data(), b.size()};
        return keyLess(av, bv);
    }

    /* Returns true iff key a sorts before key b in CBOR order */
    constexpr bool keyLess(const std::basic_string_view<T>& a,
                           const std::basic_string_view<T>& b) const {
        /* If two keys have different lengths, the shorter one sorts earlier */
        if (a.size() < b.size())
            return true;
        if (a.size() > b.size())
            return false;

        /* If keys have the same length, do a byte-wise comparison */
        return std::lexicographical_compare(a.begin(), a.end(), b.begin(),
                                            b.end());
    }
};

using CborMap = std::map<std::vector<uint8_t>,
                         std::basic_string_view<uint8_t>,
                         CBORCompare<uint8_t>>;

static inline bool populateMap(CborMap& map,
                               const std::basic_string_view<uint8_t>& encMap) {
    if (!encMap.size()) {
        /* No elements to add to map */
        return true;
    }

    struct CborIn in;
    CborInInit(encMap.data(), encMap.size(), &in);

    size_t numPairs;
    if (CborReadMap(&in, &numPairs) != CBOR_READ_RESULT_OK) {
        return false;
    }

    int64_t key;
    struct CborOut out;
    struct CborIn savedIn;
    for (size_t i = 0; i < numPairs; i++) {
        /* Read key */
        if (CborReadInt(&in, &key) != CBOR_READ_RESULT_OK) {
            return false;
        }

        /* skip value */
        savedIn = in;
        if (CborReadSkip(&in) != CBOR_READ_RESULT_OK) {
            return false;
        }

        std::vector<uint8_t> encKey(encodedSizeOfInt(key));
        CborOutInit(encKey.data(), encKey.size(), &out);
        CborWriteInt(key, &out);
        assert(!CborOutOverflowed(&out));

        std::basic_string_view<uint8_t> value = {
                savedIn.buffer + savedIn.cursor, in.cursor - savedIn.cursor};

        map[std::move(encKey)] = value;
    }

    return true;
}

/**
 * mergeMaps() - Merge the items in two CBOR maps, return canonical map.
 *
 * @lhs: CBOR-encoded map using signed integers as keys
 * @rhs: CBOR-encoded map using signed integers as keys
 *
 * Return:
 *      Canonical CBOR encoding of the combined map or %nullopt if an error
 *      occurred.
 */
static inline std::optional<std::vector<uint8_t>> mergeMaps(
        const std::basic_string_view<uint8_t>& lhs,
        const std::basic_string_view<uint8_t>& rhs) {
    /*
     * map is sorted on the encoded key which ensures that the CBOR encoding is
     * canonical.
     */
    CborMap map;

    if (!populateMap(map, lhs)) {
        return std::nullopt;
    }
    if (!populateMap(map, rhs)) {
        return std::nullopt;
    }

    size_t outputSize = encodedSizeOf(map.size());
    for (const auto& [key, value] : map) {
        outputSize += key.size() + value.size();
    }

    auto output = std::vector<uint8_t>(outputSize);
    struct CborOut out;
    CborOutInit(output.data(), output.size(), &out);
    CborWriteMap(map.size(), &out);
    for (const auto& [key, value] : map) {
        /* insert key */
        std::memcpy(output.data() + out.cursor, key.data(), key.size());
        out.cursor += key.size();

        /* insert value */
        std::memcpy(output.data() + out.cursor, value.data(), value.size());
        out.cursor += value.size();
    }

    assert(out.cursor == output.size());
    assert(!CborOutOverflowed(&out));
    return output;
}

/**
 * encodeBstrHeader() - write CBOR header for a binary string of a given size.
 * @payloadSize: Size of binary string to encode header for.
 * @outBufSize:  Size of output buffer.
 * @outBuf:      Output buffer to write CBOR header to.
 *
 * Return:       A pointer to one past the last byte written.
 */
static inline uint8_t* encodeBstrHeader(uint64_t bstrSize,
                                        size_t outBufSize,
                                        uint8_t* outBuf) {
    struct CborOut fakeOut;
    const size_t bstrHeaderSize = cbor::encodedSizeOf(bstrSize);
    assert(0 < bstrHeaderSize <= outBufSize);
    size_t fakeBufferSize;
    if (__builtin_add_overflow(bstrHeaderSize, bstrSize, &fakeBufferSize)) {
        return nullptr;
    }
    // NOTE: CborAllocBstr will fail if we don't provide a buffer object that
    // appears large enough. CborAllocBstr will *only* write header information
    // about the binary string so it will only touch allocated memory.
    CborOutInit(outBuf, fakeBufferSize, &fakeOut);
    // CborAllocBstr will only write the type and length of the binary string
    // into outBuf and manipulate the fakeOut object itself. Further
    // writes to fakeOut will trigger memory corruption.
    uint8_t* bstrHeaderEnd = CborAllocBstr(bstrSize, &fakeOut);
    assert(!CborOutOverflowed(&fakeOut));
    assert(bstrHeaderEnd != nullptr);
    assert((size_t)(bstrHeaderEnd - outBuf) == bstrHeaderSize);

    return bstrHeaderEnd;
}

class ArrayVector {
public:
    uint8_t* data() const { return mArr.get(); }

    size_t size() const { return mSize; }

    /**
     * resize() - change the reported size of underlying array.
     * @count: New size of the array.
     *
     * This function is needed for compatibility with std::vector. We only
     * support two cases 1) growing a zero-element array and 2) reducing the
     * size of a non-zero element array without shrinking the underlying
     * allocation.
     */
    void resize(size_t count) {
        if (mSize == 0 && !mArr) {
            mArr = std::unique_ptr<uint8_t[]>(new (std::nothrow)
                                                      uint8_t[count]);
            mSize = mArr ? /* success */ count : /* fail */ 0;
        } else if (count <= mSize) {
            mSize = count;
        } else {
            /*
             * Shouldn't hit this case since the CountingEncoder computes how
             * many bytes we need for encoding.
             */
            assert(false && "resizing existing array allocation not supported");
        }

        assert(count <= mSize);
        mSize = count;
    }

    std::unique_ptr<uint8_t[]> arr() { return std::move(mArr); }

private:
    std::unique_ptr<uint8_t[]> mArr;
    size_t mSize = 0;
};

/**
 * This class wraps the open-dice API defined in `cbor_writer.h`. Users of this
 * class need not determine the correct size of the output buffer manually. By
 * accepting a set of callbacks that are pure (can be called multiple times),
 * this class first performs a dry run to calculate the number of bytes needed
 * to represent a structure as CBOR, then it allocates the necessary memory and
 * performs the actual encoding.
 *
 * Limitations:
 * * The encoder will enter an error state unless map keys are ordered
 *   canonically as defined in section 3.9 of the CBOR RFC [0].
 * * The outermost CBOR element must be a tag, map, or an array. Trying to
 *   encode any other item with a newly created encoder object is not supported.
 *
 * [0]: https://datatracker.ietf.org/doc/html/rfc7049#section-3.9
 */
template <typename V>
class CborEncoder {
private:
    /**
     * Helper class which provides the same interface as the CborEncoder but
     * instead of encoding its arguments, it calculates the encoding length.
     * This lets us precisely size the CBOR output buffer ahead of time instead
     * of having to resize it on the fly.
     *
     * The counts provided by this class (bytes, array elements, map pairs) are
     * only valid if the count didn't overflow. Users of this class must check
     * whether an overflow happened before accepting any other property.
     */
    class CountingEncoder {
    public:
        template <typename Fn>
        void encodeTag(int64_t tag, Fn fn) {
            CountingEncoder enc;
            fn(enc);

            countBytes(encodedSizeOf(tag));
            countBytes(enc);
        }

        template <typename Fn>
        void encodeArray(Fn fn) {
            CountingEncoder enc;
            fn(enc);

            countBytes(encodedSizeOf(enc.arrayElements()));
            countBytes(enc);
        }

        template <typename Fn>
        void encodeMap(Fn fn) {
            CountingEncoder enc;
            fn(enc);

            countBytes(encodedSizeOf(enc.mapPairs()));
            countBytes(enc);
            mArrayElements++;
        }

        template <typename Fn>
        void encodeKeyValue(int64_t key, Fn fn) {
            CountingEncoder enc;
            fn(enc);

            countBytesToEncode(key);
            countBytes(enc);
            mMapPairs++;
        }

        void encodeKeyValue(int64_t key, int64_t val) {
            countBytesToEncode(key);
            countBytesToEncode(val);
            mMapPairs++;
        }

        void encodeKeyValue(int64_t key, int val) {
            encodeKeyValue(key, (int64_t)val);
        }

        void encodeKeyValue(int64_t key, __UNUSED bool val) {
            countBytesToEncode(key);
            /* Value 20 encodes false; 21 encodes true. Each requires a byte */
            countBytes(1);
            mMapPairs++;
        }

        void encodeKeyValue(int64_t key, const char* val) {
            size_t len = strlen(val);
            countBytesToEncode(key);
            countBytes(encodedSizeOf(len));
            countBytes(len);
            mMapPairs++;
        }

        void encodeTstr(const std::basic_string_view<char> str) {
            size_t len = str.size();
            countBytes(encodedSizeOf(len));
            countBytes(len);
            mArrayElements++;
        }

        void encodeTstr(const char* str) {
            size_t len = strlen(str);
            countBytes(encodedSizeOf(len));
            countBytes(len);
            mArrayElements++;
        }

        void encodeBstr(const std::string& str) {
            encodeBstr(reinterpret_cast<const uint8_t*>(str.data()),
                       str.size());
        }

        void encodeBstr(const std::vector<uint8_t>& vec) {
            encodeBstr(vec.data(), vec.size());
        }

        void encodeBstr(const std::basic_string_view<uint8_t>& view) {
            encodeBstr(view.data(), view.size());
        }

        void encodeBstr(__UNUSED const uint8_t* src, const size_t srcsz) {
            countBytes(encodedSizeOf(srcsz));
            countBytes(srcsz);
            mArrayElements++;
        }

        void encodeEmptyBstr() {
            countBytes(1); /* null is encoded as value 22 and takes up a byte */
            mArrayElements++;
        }

        void encodeInt(const int64_t val) {
            countBytesToEncode(val);
            mArrayElements++;
        }

        void encodeUint(const uint64_t val) {
            countBytes(encodedSizeOf(val));
            mArrayElements++;
        }

        void encodeNull() {
            countBytes(1);
            mArrayElements++;
        }

        bool copyBytes(const std::basic_string_view<uint8_t>& view) {
            return copyBytes(view.data(), view.size());
        }

        bool copyBytes(const std::vector<uint8_t>& vec) {
            return copyBytes(vec.data(), vec.size());
        }

        bool copyBytes(const uint8_t* src, const size_t srcsz) {
            countBytes(srcsz);
            mArrayElements++;
            return !mOverflowed;
        }

        bool overflowed() const { return mOverflowed; }

        size_t bytes() const { return mBytes; }

        size_t arrayElements() const { return mArrayElements; }

        size_t mapPairs() const { return mMapPairs; }

    private:
        /*
         * if true, the count failed and other properties should not be relied
         * upon for CBOR encoding.
         */
        bool mOverflowed = false;
        /* bytes needed for CBOR encoding unless an overflow occurred */
        size_t mBytes = 0;
        /* array elements, not including sub-elements, to write */
        size_t mArrayElements = 0;
        /* map pairs, not including map pairs in sub-elements, to write */
        size_t mMapPairs = 0;

        void countBytes(size_t count) {
            mOverflowed |= __builtin_add_overflow(count, mBytes, &mBytes);
        }

        void countBytes(CountingEncoder enc) {
            if (enc.overflowed()) {
                mOverflowed = true;
                return;
            }
            countBytes(enc.bytes());
        }

        void countBytesToEncode(int64_t val) {
            countBytes(encodedSizeOfInt(val));
        }

        /* CborEncoded calls countBytes */
        friend class CborEncoder;
    };

public:
    enum class State {
        /* buffer not allocated */
        kInitial,
        /* initialization or resizing of buffer failed */
        kInvalid,
        /* encoding or ready to encode */
        kEncoding,
        /* encoding would have overflowed buffer */
        kOverflowed,
        /* encoder no longer owns buffer */
        kEmptied,
    };

    template <typename Fn>
    void encodeTag(int64_t tag, Fn fn) {
        CountingEncoder enc;
        fn(enc);
        enc.countBytes(encodedSizeOf(tag));

        if (enc.overflowed()) {
            mState = State::kOverflowed;
            return;
        }

        if (ensureCapacity(enc.bytes())) {
            CborWriteTag(tag, &mOut);
            fn(*this);
        }
    }

    template <typename Fn>
    void encodeArray(Fn fn) {
        CountingEncoder enc;
        fn(enc);
        enc.countBytes(encodedSizeOf(enc.arrayElements()));

        if (enc.overflowed()) {
            mState = State::kOverflowed;
            return;
        }

        if (ensureCapacity(enc.bytes())) {
            CborWriteArray(enc.arrayElements(), &mOut);
            fn(*this);
        }
    }

    template <typename Fn>
    void encodeMap(Fn fn) {
        CountingEncoder enc;
        fn(enc);
        enc.countBytes(encodedSizeOf(enc.mapPairs()));

        if (enc.overflowed()) {
            mState = State::kOverflowed;
            return;
        }

        if (ensureCapacity(enc.bytes())) {
            CborWriteMap(enc.mapPairs(), &mOut);

            auto savedKey = mLastKey;
            mLastKey = {nullptr, 0};

            fn(*this);

            mLastKey = savedKey;
        }
    }

    template <typename Fn>
    void encodeKeyValue(int64_t key, Fn fn) {
        encodeKeyCanonicalOrder([key, this] { CborWriteInt(key, &mOut); });

        fn(*this);
    }

    void encodeKeyValue(int64_t key, int val) {
        encodeKeyValue(key, (int64_t)val);
    }

    void encodeKeyValue(int64_t key, int64_t val) {
        encodeKeyCanonicalOrder([key, this] { CborWriteInt(key, &mOut); });
        CborWriteInt(val, &mOut);
    }

    void encodeKeyValue(int64_t key, bool val) {
        encodeKeyCanonicalOrder([key, this] { CborWriteInt(key, &mOut); });
        if (val)
            CborWriteTrue(&mOut);
        else
            CborWriteFalse(&mOut);
    }

    void encodeKeyValue(int64_t key, const char* val) {
        encodeKeyCanonicalOrder([key, this] { CborWriteInt(key, &mOut); });
        encodeTstr(val);
    }

    void encodeTstr(const char* str) {
        const std::string_view view(str);
        encodeTstr(view);
    }

    void encodeTstr(const std::string_view str) {
        ensureEncoding();
        CborWriteTstr(str.data(), &mOut);
    }

    void encodeBstr(const std::string& str) {
        encodeBstr(reinterpret_cast<const uint8_t*>(str.data()), str.size());
    }

    void encodeBstr(const std::basic_string_view<uint8_t>& byteView) {
        encodeBstr(byteView.data(), byteView.size());
    }

    void encodeBstr(const std::vector<uint8_t>& vec) {
        encodeBstr(vec.data(), vec.size());
    }

    void encodeBstr(const uint8_t* data, const size_t size) {
        ensureEncoding();
        CborWriteBstr(size, data, &mOut);
    }

    void encodeEmptyBstr() {
        ensureEncoding();
        encodeBstr(nullptr, 0);
    }

    void encodeInt(const int64_t val) {
        ensureEncoding();
        CborWriteInt(val, &mOut);
    }

    void encodeUint(const uint64_t val) {
        ensureEncoding();
        CborWriteUint(val, &mOut);
    }

    void encodeNull() {
        ensureEncoding();
        CborWriteNull(&mOut);
    }

    bool copyBytes(const std::basic_string_view<uint8_t>& view) {
        return copyBytes(view.data(), view.size());
    }

    bool copyBytes(const std::vector<uint8_t>& vec) {
        return copyBytes(vec.data(), vec.size());
    }

    bool copyBytes(const uint8_t* src, const size_t srcsz) {
        if (CborOutOverflowed(&mOut) || mState == State::kOverflowed) {
            goto err_overflow;
        }

        if (mState != State::kEncoding) {
            mState = State::kInvalid;
            return false;
        }

        size_t dest;
        if (__builtin_add_overflow((size_t)mOut.buffer, mOut.cursor, &dest)) {
            goto err_overflow;
        }

        size_t destsz;
        if (__builtin_sub_overflow(mBuffer.size(), mOut.cursor, &destsz)) {
            goto err_overflow;
        }

        if (destsz < srcsz) {
            goto err_overflow;
        }

        std::memcpy((void*)dest, src, srcsz);
        mOut.cursor += srcsz;
        return true;

    err_overflow:
        mState = State::kOverflowed;
        return false;
    }

    V intoVec() {
        assert(mState != State::kEmptied && "buffer was moved out of encoder");
        assert(mState != State::kInvalid && "encoder is in an invalid state");
        if (mState != State::kInitial) {
            assert((!CborOutOverflowed(&mOut) &&
                    mState != State::kOverflowed) &&
                   "buffer was too small to hold cbor encoded content");
            assert(mBuffer.size() == CborOutSize(&mOut) &&
                   "buffer was larger than required to hold encoded content");
        }
        mState = State::kEmptied;
        return std::move(mBuffer);
    }

    std::basic_string_view<uint8_t> view() const {
        assert((mState == State::kInitial || mState == State::kEncoding) &&
               "requested view of buffer from encoder in invalid state");
        if (mState != State::kInitial) {
            assert((!CborOutOverflowed(&mOut) &&
                    mState != State::kOverflowed) &&
                   "buffer was too small to hold CBOR encoded content");
            assert(mBuffer.size() == CborOutSize(&mOut) &&
                   "buffer was larger than required to hold CBOR encoded content");
        }
        return {mBuffer.data(), mBuffer.size()};
    }

    size_t size() const {
        if (mState != State::kInitial) {
            assert((!CborOutOverflowed(&mOut) &&
                    mState != State::kOverflowed) &&
                   "requested encoding size after overflow");

            return CborOutSize(&mOut);
        } else {
            return 0u;
        }
    }

    State state() const { return mState; }

private:
    State mState = State::kInitial;
    /* vector or vector-like buffer */
    V mBuffer;
    /* cursor used for CBOR encoding which points into mBuffer */
    struct CborOut mOut;
    /*
     * Used to ensure that map keys are encoded in canonical order. When the
     * encoder is not encoding a map, the field has no value.
     */
    std::optional<std::basic_string_view<uint8_t>> mLastKey = std::nullopt;
    /* determines CBOR ordering between two keys */
    CBORCompare<uint8_t> mComparer;

    void ensureEncoding() const {
        assert(mState == State::kEncoding &&
               "Call encodeArray, encodeTag, or encodeMap before this method");
    }

    bool ensureCapacity(const size_t capacity) {
        if (mState == State::kInitial) {
            mBuffer.resize(capacity);
            CborOutInit(mBuffer.data(), mBuffer.size(), &mOut);
            mState = mBuffer.size() == capacity ? State::kEncoding
                                                : State::kInvalid;
        }
        return mState == State::kEncoding;
    }

    template <typename Fn>
    void encodeKeyCanonicalOrder(Fn fn) {
        if (mState != State::kEncoding || !mLastKey.has_value()) {
            mState = State::kInvalid;
            return;
        }

        const struct CborOut preCursor = mOut;
        fn();

        const size_t newKeySz = mOut.cursor - preCursor.cursor;
        const uint8_t* newKeyStart = preCursor.buffer + preCursor.cursor;
        const std::basic_string_view<uint8_t> newKey = {newKeyStart, newKeySz};

        /*
         * The keys in every map must be sorted lowest value to highest.
         * Sorting is performed on the bytes of the representation of the key
         * data items without paying attention to the 3/5 bit splitting for
         * major types. The sorting rules are:
         *
         *  * If two keys have different lengths, the shorter one sorts earlier;
         *
         *  * If two keys have the same length, the one with the lower value
         *    in (byte-wise) lexical order sorts earlier.
         */
        if (mComparer.keyLess(mLastKey.value(), newKey)) {
            mLastKey = newKey;
            return;
        }

        /* CBOR encoding is not canonical */
        mState = State::kInvalid;
    }
};

using ArrayCborEncoder = CborEncoder<ArrayVector>;
using VectorCborEncoder = CborEncoder<std::vector<uint8_t>>;

}  // namespace cbor
