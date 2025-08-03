/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef ART_LIBARTBASE_BASE_BIT_MEMORY_REGION_H_
#define ART_LIBARTBASE_BASE_BIT_MEMORY_REGION_H_

#include "memory_region.h"

#include "bit_utils.h"
#include "memory_tool.h"

#include <array>
#include <cstdint>

namespace art {

// Bit memory region is a bit offset subregion of a normal memoryregion. This is useful for
// abstracting away the bit start offset to avoid needing passing as an argument everywhere.
class BitMemoryRegion final : public ValueObject {
 public:
  // Ensure all loads are naturally-aligned by aligning down the region's data pointer according to
  // the largest data type that will be loaded via LoadBits (as StackMap BitTable uses over 8
  // varints in the header, this is uint64_t).
  using MaxSingleLoadType = uint64_t;
  static constexpr size_t kMaxSingleLoadBytes = sizeof(MaxSingleLoadType);

  BitMemoryRegion() = default;
  ALWAYS_INLINE BitMemoryRegion(uint8_t* data, ssize_t bit_start, size_t bit_size) {
    // Normalize the data pointer. Note that bit_start may be negative.
    data_ = AlignDown(data + (bit_start >> kBitsPerByteLog2), kMaxSingleLoadBytes);
    bit_start_ = bit_start + kBitsPerByte * (data - data_);
    bit_size_ = bit_size;
  }
  ALWAYS_INLINE explicit BitMemoryRegion(MemoryRegion region)
    : BitMemoryRegion(region.begin(), /* bit_start */ 0, region.size_in_bits()) {
  }
  ALWAYS_INLINE BitMemoryRegion(MemoryRegion region, size_t bit_offset, size_t bit_length)
    : BitMemoryRegion(region) {
    *this = Subregion(bit_offset, bit_length);
  }

  ALWAYS_INLINE bool IsValid() const { return data_ != nullptr; }

  const uint8_t* data() const {
    DCHECK_ALIGNED(bit_start_, kBitsPerByte);
    return data_ + bit_start_ / kBitsPerByte;
  }

  size_t size_in_bits() const {
    return bit_size_;
  }

  void Resize(size_t bit_size) {
    bit_size_ = bit_size;
  }

  ALWAYS_INLINE BitMemoryRegion Subregion(size_t bit_offset, size_t bit_length) const {
    DCHECK_LE(bit_offset, bit_size_);
    DCHECK_LE(bit_length, bit_size_ - bit_offset);
    BitMemoryRegion result = *this;
    result.bit_start_ += bit_offset;
    result.bit_size_ = bit_length;
    return result;
  }

  ALWAYS_INLINE BitMemoryRegion Subregion(size_t bit_offset) const {
    DCHECK_LE(bit_offset, bit_size_);
    BitMemoryRegion result = *this;
    result.bit_start_ += bit_offset;
    result.bit_size_ -= bit_offset;
    return result;
  }

  // Load a single bit in the region. The bit at offset 0 is the least
  // significant bit in the first byte.
  ALWAYS_INLINE bool LoadBit(size_t bit_offset) const {
    DCHECK_LT(bit_offset, bit_size_);
    size_t index = (bit_start_ + bit_offset) / kBitsPerByte;
    size_t shift = (bit_start_ + bit_offset) % kBitsPerByte;
    return ((data_[index] >> shift) & 1) != 0;
  }

  ALWAYS_INLINE void StoreBit(size_t bit_offset, bool value) {
    DCHECK_LT(bit_offset, bit_size_);
    size_t index = (bit_start_ + bit_offset) / kBitsPerByte;
    size_t shift = (bit_start_ + bit_offset) % kBitsPerByte;
    data_[index] &= ~(1 << shift);  // Clear bit.
    data_[index] |= (value ? 1 : 0) << shift;  // Set bit.
    DCHECK_EQ(value, LoadBit(bit_offset));
  }

  // Load `bit_length` bits from `data` starting at given `bit_offset`.
  // The least significant bit is stored in the smallest memory offset.
  template<typename Result = size_t>
  ATTRIBUTE_NO_SANITIZE_ADDRESS  // We might touch extra bytes due to the alignment.
  ATTRIBUTE_NO_SANITIZE_HWADDRESS  // The hwasan uses different attribute.
  ALWAYS_INLINE Result LoadBits(size_t bit_offset, size_t bit_length) const {
    static_assert(std::is_integral_v<Result>, "Result must be integral");
    static_assert(std::is_unsigned_v<Result>, "Result must be unsigned");
    static_assert(sizeof(Result) <= kMaxSingleLoadBytes);
    DCHECK(IsAligned<sizeof(Result)>(data_));
    DCHECK_LE(bit_offset, bit_size_);
    DCHECK_LE(bit_length, bit_size_ - bit_offset);
    DCHECK_LE(bit_length, BitSizeOf<Result>());
    if (bit_length == 0) {
      return 0;
    }
    // Load naturally-aligned value which contains the least significant bit.
    Result* data = reinterpret_cast<Result*>(data_);
    size_t width = BitSizeOf<Result>();
    size_t index = (bit_start_ + bit_offset) / width;
    size_t shift = (bit_start_ + bit_offset) % width;
    Result value = data[index] >> shift;
    // Load extra value containing the most significant bit (it might be the same one).
    // We can not just load the following value as that could potentially cause SIGSEGV.
    Result extra = data[index + (shift + (bit_length - 1)) / width];
    // Mask to clear unwanted bits (the 1s are needed to avoid avoid undefined shift).
    Result clear = (std::numeric_limits<Result>::max() << 1) << (bit_length - 1);
    // Prepend the extra value.  We add explicit '& (width - 1)' so that the shift is defined.
    // It is a no-op for `shift != 0` and if `shift == 0` then `value == extra` because of
    // bit_length <= width causing the `value` and `extra` to be read from the same location.
    // The '& (width - 1)' is implied by the shift instruction on ARM and removed by compiler.
    return (value | (extra << ((width - shift) & (width - 1)))) & ~clear;
  }

  // Store `bit_length` bits in `data` starting at given `bit_offset`.
  // The least significant bit is stored in the smallest memory offset.
  ALWAYS_INLINE void StoreBits(size_t bit_offset, size_t value, size_t bit_length) {
    DCHECK_LE(bit_offset, bit_size_);
    DCHECK_LE(bit_length, bit_size_ - bit_offset);
    DCHECK_LE(bit_length, BitSizeOf<size_t>());
    DCHECK_LE(value, MaxInt<size_t>(bit_length));
    if (bit_length == 0) {
      return;
    }
    // Write data byte by byte to avoid races with other threads
    // on bytes that do not overlap with this region.
    size_t mask = std::numeric_limits<size_t>::max() >> (BitSizeOf<size_t>() - bit_length);
    size_t index = (bit_start_ + bit_offset) / kBitsPerByte;
    size_t shift = (bit_start_ + bit_offset) % kBitsPerByte;
    data_[index] &= ~(mask << shift);  // Clear bits.
    data_[index] |= (value << shift);  // Set bits.
    size_t finished_bits = kBitsPerByte - shift;
    for (int i = 1; finished_bits < bit_length; i++, finished_bits += kBitsPerByte) {
      data_[index + i] &= ~(mask >> finished_bits);  // Clear bits.
      data_[index + i] |= (value >> finished_bits);  // Set bits.
    }
    DCHECK_EQ(value, LoadBits(bit_offset, bit_length));
  }

  // Copy bits from other bit region.
  ALWAYS_INLINE void CopyBits(const BitMemoryRegion& src) {
    DCHECK_EQ(size_in_bits(), src.size_in_bits());
    // Hopefully, the loads of the unused `value` shall be optimized away.
    VisitChunks([this, &src](size_t offset, size_t num_bits, [[maybe_unused]] size_t value)
                    ALWAYS_INLINE {
                      StoreChunk(offset, src.LoadBits(offset, num_bits), num_bits);
                      return true;
                    });
  }

  // And bits from other bit region.
  ALWAYS_INLINE void AndBits(const BitMemoryRegion& src) {
    DCHECK_EQ(size_in_bits(), src.size_in_bits());
    VisitChunks([this, &src](size_t offset, size_t num_bits, size_t value) ALWAYS_INLINE {
      StoreChunk(offset, value & src.LoadBits(offset, num_bits), num_bits);
      return true;
    });
  }

  // Or bits from other bit region.
  ALWAYS_INLINE void OrBits(const BitMemoryRegion& src) {
    DCHECK_EQ(size_in_bits(), src.size_in_bits());
    VisitChunks([this, &src](size_t offset, size_t num_bits, size_t value) ALWAYS_INLINE {
      StoreChunk(offset, value | src.LoadBits(offset, num_bits), num_bits);
      return true;
    });
  }

  // Xor bits from other bit region.
  ALWAYS_INLINE void XorBits(const BitMemoryRegion& src) {
    DCHECK_EQ(size_in_bits(), src.size_in_bits());
    VisitChunks([this, &src](size_t offset, size_t num_bits, size_t value) ALWAYS_INLINE {
      StoreChunk(offset, value ^ src.LoadBits(offset, num_bits), num_bits);
      return true;
    });
  }

  // Count the number of set bits within this region.
  ALWAYS_INLINE size_t PopCount() const {
    size_t result = 0u;
    VisitChunks([&]([[maybe_unused]] size_t offset, [[maybe_unused]] size_t num_bits, size_t value)
                    ALWAYS_INLINE {
                      result += POPCOUNT(value);
                      return true;
                    });
    return result;
  }

  // Count the number of set bits within the given bit range.
  ALWAYS_INLINE size_t PopCount(size_t bit_offset, size_t bit_length) const {
    return Subregion(bit_offset, bit_length).PopCount();
  }

  // Check if this region has all bits clear.
  ALWAYS_INLINE bool HasAllBitsClear() const {
    return VisitChunks(
        []([[maybe_unused]] size_t offset, [[maybe_unused]] size_t num_bits, size_t value)
            ALWAYS_INLINE { return value == 0u; });
  }

  // Check if this region has any bit set.
  ALWAYS_INLINE bool HasSomeBitSet() const {
    return !HasAllBitsClear();
  }

  // Check if there is any bit set within the given bit range.
  ALWAYS_INLINE bool HasSomeBitSet(size_t bit_offset, size_t bit_length) const {
    return Subregion(bit_offset, bit_length).HasSomeBitSet();
  }

  static int Compare(const BitMemoryRegion& lhs, const BitMemoryRegion& rhs) {
    if (lhs.size_in_bits() != rhs.size_in_bits()) {
      return (lhs.size_in_bits() < rhs.size_in_bits()) ? -1 : 1;
    }
    int result = 0;
    bool equals = lhs.VisitChunks(
        [&](size_t offset, size_t num_bits, size_t lhs_value) ALWAYS_INLINE {
          size_t rhs_value = rhs.LoadBits(offset, num_bits);
          if (lhs_value == rhs_value) {
            return true;
          }
          // We have found a difference. To avoid the comparison being dependent on how the region
          // is split into chunks, check the lowest bit that differs. (Android is little-endian.)
          int bit = CTZ(lhs_value ^ rhs_value);
          result = ((rhs_value >> bit) & 1u) != 0u ? 1 : -1;
          return false;  // Stop iterating.
        });
    DCHECK_EQ(equals, result == 0);
    return result;
  }

  static bool Equals(const BitMemoryRegion& lhs, const BitMemoryRegion& rhs) {
    if (lhs.size_in_bits() != rhs.size_in_bits()) {
      return false;
    }
    return lhs.VisitChunks([&rhs](size_t offset, size_t num_bits, size_t lhs_value) ALWAYS_INLINE {
      return lhs_value == rhs.LoadBits(offset, num_bits);
    });
  }

 private:
  // Visit the region in aligned `size_t` chunks. The first and last chunk may have fewer bits.
  //
  // Returns `true` if the iteration visited all chunks successfully, i.e. none of the
  // calls to `visitor(offset, num_bits, value)` returned `false`; otherwise `false`.
  template <typename VisitorType>
  ATTRIBUTE_NO_SANITIZE_ADDRESS  // We might touch extra bytes due to the alignment.
  ATTRIBUTE_NO_SANITIZE_HWADDRESS  // The hwasan uses different attribute.
  ALWAYS_INLINE bool VisitChunks(VisitorType&& visitor) const {
    constexpr size_t kChunkSize = BitSizeOf<size_t>();
    size_t remaining_bits = bit_size_;
    if (remaining_bits == 0) {
      return true;
    }
    DCHECK(IsAligned<sizeof(size_t)>(data_));
    const size_t* data = reinterpret_cast<const size_t*>(data_);
    size_t offset = 0u;
    size_t bit_start = bit_start_;
    data += bit_start / kChunkSize;
    if ((bit_start % kChunkSize) != 0u) {
      size_t leading_bits = kChunkSize - (bit_start % kChunkSize);
      size_t value = (*data) >> (bit_start % kChunkSize);
      if (leading_bits > remaining_bits) {
        leading_bits = remaining_bits;
        value = value & ~(std::numeric_limits<size_t>::max() << remaining_bits);
      }
      if (!visitor(offset, leading_bits, value)) {
        return false;
      }
      offset += leading_bits;
      remaining_bits -= leading_bits;
      ++data;
    }
    while (remaining_bits >= kChunkSize) {
      size_t value = *data;
      if (!visitor(offset, kChunkSize, value)) {
        return false;
      }
      offset += kChunkSize;
      remaining_bits -= kChunkSize;
      ++data;
    }
    if (remaining_bits != 0u) {
      size_t value = (*data) & ~(std::numeric_limits<size_t>::max() << remaining_bits);
      if (!visitor(offset, remaining_bits, value)) {
        return false;
      }
    }
    return true;
  }

  ALWAYS_INLINE void StoreChunk(size_t bit_offset, size_t value, size_t bit_length) {
    if (bit_length == BitSizeOf<size_t>()) {
      DCHECK_ALIGNED(bit_start_ + bit_offset, BitSizeOf<size_t>());
      uint8_t* data = data_ + (bit_start_ + bit_offset) / kBitsPerByte;
      DCHECK_ALIGNED(data, sizeof(size_t));
      reinterpret_cast<size_t*>(data)[0] = value;
    } else {
      StoreBits(bit_offset, value, bit_length);
    }
  }

  uint8_t* data_ = nullptr;  // The pointer is aligned down to kMaxSingleLoadBytes.
  size_t bit_start_ = 0;
  size_t bit_size_ = 0;
};

// Minimum number of bits used for varint. A varint represents either a value stored "inline" or
// the number of bytes that are required to encode the value.
constexpr uint32_t kVarintBits = 4;
// Maximum value which is stored "inline". We use the rest of the values to encode the number of
// bytes required to encode the value when the value is greater than kVarintMax.
// We encode any value less than or equal to 11 inline. We use 12, 13, 14 and 15
// to represent that the value is encoded in 1, 2, 3 and 4 bytes respectively.
//
// For example if we want to encode 1, 15, 16, 7, 11, 256:
//
// Low numbers (1, 7, 11) are encoded inline. 15 and 12 are set with 12 to show
// we need to load one byte for each to have their real values (15 and 12), and
// 256 is set with 13 to show we need to load two bytes. This is done to
// compress the values in the bit array and keep the size down. Where the actual value
// is read from depends on the use case.
//
// Values greater than kVarintMax could be encoded as a separate list referred
// to as InterleavedVarints (see ReadInterleavedVarints / WriteInterleavedVarints).
// This is used when there are fixed number of fields like CodeInfo headers.
// In our example the interleaved encoding looks like below:
//
// Meaning: 1--- 15-- 12-- 7--- 11-- 256- 15------- 12------- 256----------------
// Bits:    0001 1100 1100 0111 1011 1101 0000 1111 0000 1100 0000 0001 0000 0000
//
// In other cases the value is recorded just following the size encoding. This is
// referred as consecutive encoding (See ReadVarint / WriteVarint). In our
// example the consecutively encoded varints looks like below:
//
// Meaning: 1--- 15-- 15------- 12-- 12------- 7--- 11-- 256- 256----------------
// Bits:    0001 1100 0000 1100 1100 0000 1100 0111 1011 1101 0000 0001 0000 0000
constexpr uint32_t kVarintMax = 11;

class BitMemoryReader {
 public:
  BitMemoryReader(BitMemoryReader&&) = default;
  explicit BitMemoryReader(BitMemoryRegion data)
      : finished_region_(data.Subregion(0, 0) /* set the length to zero */ ) {
  }
  explicit BitMemoryReader(const uint8_t* data, ssize_t bit_offset = 0)
      : finished_region_(const_cast<uint8_t*>(data), bit_offset, /* bit_length */ 0) {
  }

  const uint8_t* data() const { return finished_region_.data(); }

  BitMemoryRegion GetReadRegion() const { return finished_region_; }

  size_t NumberOfReadBits() const { return finished_region_.size_in_bits(); }

  ALWAYS_INLINE BitMemoryRegion ReadRegion(size_t bit_length) {
    size_t bit_offset = finished_region_.size_in_bits();
    finished_region_.Resize(bit_offset + bit_length);
    return finished_region_.Subregion(bit_offset, bit_length);
  }

  template<typename Result = size_t>
  ALWAYS_INLINE Result ReadBits(size_t bit_length) {
    return ReadRegion(bit_length).LoadBits<Result>(/* bit_offset */ 0, bit_length);
  }

  ALWAYS_INLINE bool ReadBit() {
    return ReadRegion(/* bit_length */ 1).LoadBit(/* bit_offset */ 0);
  }

  // Read variable-length bit-packed integer.
  // The first four bits determine the variable length of the encoded integer:
  //   Values 0..11 represent the result as-is, with no further following bits.
  //   Values 12..15 mean the result is in the next 8/16/24/32-bits respectively.
  ALWAYS_INLINE uint32_t ReadVarint() {
    uint32_t x = ReadBits(kVarintBits);
    return (x <= kVarintMax) ? x : ReadBits((x - kVarintMax) * kBitsPerByte);
  }

  // Read N 'interleaved' varints (different to just reading consecutive varints).
  // All small values are stored first and the large values are stored after them.
  // This requires fewer bit-reads compared to indidually storing the varints.
  template<size_t N>
  ALWAYS_INLINE std::array<uint32_t, N> ReadInterleavedVarints() {
    static_assert(N * kVarintBits <= BitMemoryRegion::kMaxSingleLoadBytes * kBitsPerByte,
                  "N too big");
    std::array<uint32_t, N> values;
    // StackMap BitTable uses over 8 varints in the header, so we need uint64_t.
    uint64_t data = ReadBits<uint64_t>(N * kVarintBits);
    for (size_t i = 0; i < N; i++) {
      values[i] = BitFieldExtract(data, i * kVarintBits, kVarintBits);
    }
    // Do the second part in its own loop as that seems to produce better code in clang.
    for (size_t i = 0; i < N; i++) {
      if (UNLIKELY(values[i] > kVarintMax)) {
        values[i] = ReadBits((values[i] - kVarintMax) * kBitsPerByte);
      }
    }
    return values;
  }

 private:
  // Represents all of the bits which were read so far. There is no upper bound.
  // Therefore, by definition, the "cursor" is always at the end of the region.
  BitMemoryRegion finished_region_;

  DISALLOW_COPY_AND_ASSIGN(BitMemoryReader);
};

template<typename Vector>
class BitMemoryWriter {
 public:
  explicit BitMemoryWriter(Vector* out, size_t bit_offset = 0)
      : out_(out), bit_start_(bit_offset), bit_offset_(bit_offset) {
    DCHECK_EQ(NumberOfWrittenBits(), 0u);
  }

  void Truncate(size_t bit_offset) {
    DCHECK_GE(bit_offset, bit_start_);
    DCHECK_LE(bit_offset, bit_offset_);
    bit_offset_ = bit_offset;
    DCHECK_LE(BitsToBytesRoundUp(bit_offset), out_->size());
    out_->resize(BitsToBytesRoundUp(bit_offset));  // Shrink.
  }

  BitMemoryRegion GetWrittenRegion() const {
    return BitMemoryRegion(out_->data(), bit_start_, bit_offset_ - bit_start_);
  }

  const uint8_t* data() const { return out_->data(); }

  size_t NumberOfWrittenBits() const { return bit_offset_ - bit_start_; }

  ALWAYS_INLINE BitMemoryRegion Allocate(size_t bit_length) {
    out_->resize(BitsToBytesRoundUp(bit_offset_ + bit_length));
    BitMemoryRegion region(out_->data(), bit_offset_, bit_length);
    DCHECK_LE(bit_length, std::numeric_limits<size_t>::max() - bit_offset_) << "Overflow";
    bit_offset_ += bit_length;
    return region;
  }

  ALWAYS_INLINE void WriteRegion(const BitMemoryRegion& region) {
    Allocate(region.size_in_bits()).CopyBits(region);
  }

  ALWAYS_INLINE void WriteBits(uint32_t value, size_t bit_length) {
    Allocate(bit_length).StoreBits(/* bit_offset */ 0, value, bit_length);
  }

  ALWAYS_INLINE void WriteBit(bool value) {
    Allocate(1).StoreBit(/* bit_offset */ 0, value);
  }

  template<size_t N>
  ALWAYS_INLINE void WriteInterleavedVarints(std::array<uint32_t, N> values) {
    // Write small values (or the number of bytes needed for the large values).
    for (uint32_t value : values) {
      if (value > kVarintMax) {
        WriteBits(kVarintMax + BitsToBytesRoundUp(MinimumBitsToStore(value)), kVarintBits);
      } else {
        WriteBits(value, kVarintBits);
      }
    }
    // Write large values.
    for (uint32_t value : values) {
      if (value > kVarintMax) {
        WriteBits(value, BitsToBytesRoundUp(MinimumBitsToStore(value)) * kBitsPerByte);
      }
    }
  }

  ALWAYS_INLINE void WriteVarint(uint32_t value) {
    WriteInterleavedVarints<1>({value});
  }

  void WriteBytesAligned(const uint8_t* bytes, size_t length) {
    DCHECK_ALIGNED(bit_start_, kBitsPerByte);
    DCHECK_ALIGNED(bit_offset_, kBitsPerByte);
    DCHECK_EQ(BitsToBytesRoundUp(bit_offset_), out_->size());
    out_->insert(out_->end(), bytes, bytes + length);
    bit_offset_ += length * kBitsPerByte;
  }

  ALWAYS_INLINE void ByteAlign() {
    DCHECK_ALIGNED(bit_start_, kBitsPerByte);
    bit_offset_ = RoundUp(bit_offset_, kBitsPerByte);
  }

 private:
  Vector* out_;
  size_t bit_start_;
  size_t bit_offset_;

  DISALLOW_COPY_AND_ASSIGN(BitMemoryWriter);
};

}  // namespace art

#endif  // ART_LIBARTBASE_BASE_BIT_MEMORY_REGION_H_
