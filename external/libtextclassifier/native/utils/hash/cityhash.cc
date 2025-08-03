/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include "utils/hash/cityhash.h"

#include <cstdint>

#include "absl/base/internal/endian.h"
#include "absl/numeric/int128.h"

namespace tc3farmhash {
namespace {
// Some primes between 2^63 and 2^64 for various uses.
static const uint64_t k0 = 0xa5b85c5e198ed849ULL;
static const uint64_t k1 = 0x8d58ac26afe12e47ULL;
static const uint64_t k2 = 0xc47b6e9e3a970ed3ULL;
static const uint64_t k3 = 0xc70f6907e782aa0bULL;

// Hash 128 input bits down to 64 bits of output.
// This is intended to be a reasonably good hash function.
// It may change from time to time.
inline uint64_t Hash128to64(const absl::uint128 x) {
  // Murmur-inspired hashing.
  const uint64_t kMul = 0xc6a4a7935bd1e995ULL;
  uint64_t a = (absl::Uint128Low64(x) ^ absl::Uint128High64(x)) * kMul;
  a ^= (a >> 47);
  uint64_t b = (absl::Uint128High64(x) ^ a) * kMul;
  b ^= (b >> 47);
  b *= kMul;
  return b;
}

uint64_t HashLen16(uint64_t u, uint64_t v) {
  return Hash128to64(absl::MakeUint128(u, v));
}

static uint64_t Rotate(uint64_t val, size_t shift) {
  assert(shift <= 63);
  return (val >> shift) | (val << (-shift & 63));
}

static uint64_t ShiftMix(uint64_t val) { return val ^ (val >> 47); }

uint64_t HashLen0to16(const char *s, size_t len) {
  assert(len <= 16);
  if (len > 8) {
    uint64_t a = absl::little_endian::Load64(s);
    uint64_t b = absl::little_endian::Load64(s + len - 8);
    return HashLen16(a, Rotate(b + len, len)) ^ b;
  }
  if (len >= 4) {
    uint64_t a = absl::little_endian::Load32(s);
    return HashLen16(len + (a << 3), absl::little_endian::Load32(s + len - 4));
  }
  if (len > 0) {
    uint8_t a = s[0];
    uint8_t b = s[len >> 1];
    uint8_t c = s[len - 1];
    uint32_t y = static_cast<uint32_t>(a) + (static_cast<uint32_t>(b) << 8);
    uint32_t z = len + (static_cast<uint32_t>(c) << 2);
    return ShiftMix(y * k2 ^ z * k3) * k2;
  }
  return k2;
}

// Return a 16-byte hash for 48 bytes.  Quick and dirty.
// Callers do best to use "random-looking" values for a and b.
// (For more, see the code review discussion of CL 18799087.)
std::pair<uint64_t, uint64_t> WeakHashLen32WithSeeds(uint64_t w, uint64_t x,
                                                     uint64_t y, uint64_t z,
                                                     uint64_t a, uint64_t b) {
  a += w;
  b = Rotate(b + a + z, 51);
  uint64_t c = a;
  a += x;
  a += y;
  b += Rotate(a, 23);
  return std::make_pair(a + z, b + c);
}

// Return a 16-byte hash for s[0] ... s[31], a, and b.  Quick and dirty.
std::pair<uint64_t, uint64_t> WeakHashLen32WithSeeds(const char *s, uint64_t a,
                                                     uint64_t b) {
  return WeakHashLen32WithSeeds(absl::little_endian::Load64(s),
                                absl::little_endian::Load64(s + 8),
                                absl::little_endian::Load64(s + 16),
                                absl::little_endian::Load64(s + 24), a, b);
}

}  // namespace

// This probably works well for 16-byte strings as well, but it may be overkill
// in that case.
static uint64_t HashLen17to32(const char *s, size_t len) {
  assert(len >= 17);
  assert(len <= 32);
  uint64_t a = absl::little_endian::Load64(s) * k1;
  uint64_t b = absl::little_endian::Load64(s + 8);
  uint64_t c = absl::little_endian::Load64(s + len - 8) * k2;
  uint64_t d = absl::little_endian::Load64(s + len - 16) * k0;
  return HashLen16(Rotate(a - b, 43) + Rotate(c, 30) + d,
                   a + Rotate(b ^ k3, 20) - c + len);
}

// Return an 8-byte hash for 33 to 64 bytes.
static uint64_t HashLen33to64(const char *s, size_t len) {
  uint64_t z = absl::little_endian::Load64(s + 24);
  uint64_t a = absl::little_endian::Load64(s) +
               (len + absl::little_endian::Load64(s + len - 16)) * k0;
  uint64_t b = Rotate(a + z, 52);
  uint64_t c = Rotate(a, 37);
  a += absl::little_endian::Load64(s + 8);
  c += Rotate(a, 7);
  a += absl::little_endian::Load64(s + 16);
  uint64_t vf = a + z;
  uint64_t vs = b + Rotate(a, 31) + c;
  a = absl::little_endian::Load64(s + 16) +
      absl::little_endian::Load64(s + len - 32);
  z += absl::little_endian::Load64(s + len - 8);
  b = Rotate(a + z, 52);
  c = Rotate(a, 37);
  a += absl::little_endian::Load64(s + len - 24);
  c += Rotate(a, 7);
  a += absl::little_endian::Load64(s + len - 16);
  uint64_t wf = a + z;
  uint64_t ws = b + Rotate(a, 31) + c;
  uint64_t r = ShiftMix((vf + ws) * k2 + (wf + vs) * k0);
  return ShiftMix(r * k0 + vs) * k2;
}

uint64_t CityHash64(const char *s, size_t len) {
  if (len <= 32) {
    if (len <= 16) {
      return HashLen0to16(s, len);
    } else {
      return HashLen17to32(s, len);
    }
  } else if (len <= 64) {
    return HashLen33to64(s, len);
  }

  // For strings over 64 bytes we hash the end first, and then as we
  // loop we keep 56 bytes of state: v, w, x, y, and z.
  uint64_t x = absl::little_endian::Load64(s + len - 40);
  uint64_t y = absl::little_endian::Load64(s + len - 16) +
               absl::little_endian::Load64(s + len - 56);
  uint64_t z = HashLen16(absl::little_endian::Load64(s + len - 48) + len,
                         absl::little_endian::Load64(s + len - 24));
  std::pair<uint64_t, uint64_t> v =
      WeakHashLen32WithSeeds(s + len - 64, len, z);
  std::pair<uint64_t, uint64_t> w =
      WeakHashLen32WithSeeds(s + len - 32, y + k1, x);
  x = x * k1 + absl::little_endian::Load64(s);

  // Decrease len to the nearest multiple of 64, and operate on 64-byte chunks.
  len = (len - 1) & ~static_cast<size_t>(63);
  assert(len > 0);
  assert(len == len / 64 * 64);
  do {
    x = Rotate(x + y + v.first + absl::little_endian::Load64(s + 8), 37) * k1;
    y = Rotate(y + v.second + absl::little_endian::Load64(s + 48), 42) * k1;
    x ^= w.second;
    y += v.first + absl::little_endian::Load64(s + 40);
    z = Rotate(z + w.first, 33) * k1;
    v = WeakHashLen32WithSeeds(s, v.second * k1, x + w.first);
    w = WeakHashLen32WithSeeds(s + 32, z + w.second,
                               y + absl::little_endian::Load64(s + 16));
    std::swap(z, x);
    s += 64;
    len -= 64;
  } while (len != 0);
  return HashLen16(HashLen16(v.first, w.first) + ShiftMix(y) * k1 + z,
                   HashLen16(v.second, w.second) + x);
}
}  // namespace tc3farmhash
