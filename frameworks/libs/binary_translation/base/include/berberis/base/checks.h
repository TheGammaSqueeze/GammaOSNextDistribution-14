/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef BERBERIS_BASE_CHECKS_H_
#define BERBERIS_BASE_CHECKS_H_

#include <array>
#include <cinttypes>

#include "berberis/base/logging.h"

// Helpers for building message format, without incurring any function calls when the condition
// does not fail.

namespace berberis {

class FmtSpec {
 private:
  constexpr static const char (&Get(int32_t))[sizeof "%" PRId32] { return "%" PRId32; }
  constexpr static const char (&Get(uint32_t))[sizeof "%" PRIu32] { return "%" PRIu32; }
  constexpr static const char (&Get(int64_t))[sizeof "%" PRId64] { return "%" PRId64; }
  constexpr static const char (&Get(uint64_t))[sizeof "%" PRIu64] { return "%" PRIu64; }
  constexpr static const char (&Get(double))[sizeof "%f"] { return "%f"; }
  constexpr static const char (&Get(const void*))[sizeof "%p"] { return "%p"; }

 public:
  template <typename Type>
  constexpr static auto& kValue = FmtSpec::Get(static_cast<std::decay_t<Type>>(0));

  template <size_t prefix_len, size_t op_len, size_t spec1_len, size_t spec2_len>
  constexpr static std::array<char, prefix_len + op_len + spec1_len + spec2_len - 3> Fmt(
      const char (&prefix)[prefix_len], const char (&op)[op_len], const char (&spec1)[spec1_len],
      const char (&spec2)[spec2_len]) {
    std::array<char, prefix_len + op_len + spec1_len + spec2_len - 3> fmt{};
    auto pos = begin(fmt);
    Append(&pos, prefix, prefix_len);
    Append(&pos, spec1, spec1_len);
    Append(&pos, op, op_len);
    Append(&pos, spec2, spec2_len);
    return fmt;
  }

 private:
  template <typename Iterator>
  constexpr static void Append(Iterator* pos, const char* text, size_t len) {
    while (--len > 0) *(*pos)++ = *text++;
  }
};

}  // namespace berberis

#define BERBERIS_VALUE_STR_IMPL(v) #v
#define BERBERIS_VALUE_STR(v) BERBERIS_VALUE_STR_IMPL(v)
#define BERBERIS_CHECK_PREFIX __FILE__ ":" BERBERIS_VALUE_STR(__LINE__) ": CHECK failed: "

// Log fatal error.
// NEVER stripped - side effects always apply.

#define FATAL(...) LOG_ALWAYS_FATAL(__VA_ARGS__)

#define UNREACHABLE() FATAL("This code is (supposed to be) unreachable.")

#ifdef CHECK
#undef CHECK
#endif
#define CHECK(cond) LOG_ALWAYS_FATAL_IF(!(cond), "%s", BERBERIS_CHECK_PREFIX #cond)

// TODO(b/232598137): fix multiple evaluation of v1 and v2!
// TODO(b/232598137): change message from '1 == 0' to 'x == y (1 == 0)'!
#define BERBERIS_CHECK_OP(op, v1, v2)                                                    \
  LOG_ALWAYS_FATAL_IF(                                                                   \
      !((v1)op(v2)), /* // NOLINT */                                                     \
      []() {                                                                             \
        constexpr static auto __fmt = berberis::FmtSpec::Fmt(                            \
            BERBERIS_CHECK_PREFIX, " " #op " ", berberis::FmtSpec::kValue<decltype(v1)>, \
            berberis::FmtSpec::kValue<decltype(v2)>);                                    \
        return __fmt.data();                                                             \
      }(),                                                                               \
      v1, v2)

#ifdef CHECK_EQ
#undef CHECK_EQ
#endif
#define CHECK_EQ(v1, v2) BERBERIS_CHECK_OP(==, v1, v2)

#ifdef CHECK_NE
#undef CHECK_NE
#endif
#define CHECK_NE(v1, v2) BERBERIS_CHECK_OP(!=, v1, v2)

#ifdef CHECK_LT
#undef CHECK_LT
#endif
#define CHECK_LT(v1, v2) BERBERIS_CHECK_OP(<, v1, v2)

#ifdef CHECK_LE
#undef CHECK_LE
#endif
#define CHECK_LE(v1, v2) BERBERIS_CHECK_OP(<=, v1, v2)

#ifdef CHECK_GT
#undef CHECK_GT
#endif
#define CHECK_GT(v1, v2) BERBERIS_CHECK_OP(>, v1, v2)

#ifdef CHECK_GE
#undef CHECK_GE
#endif
#define CHECK_GE(v1, v2) BERBERIS_CHECK_OP(>=, v1, v2)

// Log fatal error.
// ATTENTION - stripped from release builds, be careful with side effects!

#ifdef DCHECK
#undef DCHECK
#endif
#if LOG_NDEBUG
#define DCHECK(cond)
#else
#define DCHECK(cond) CHECK(cond)
#endif

#if LOG_NDEBUG
#define BERBERIS_DCHECK_OP(op, v1, v2)
#else
#define BERBERIS_DCHECK_OP(op, v1, v2) BERBERIS_CHECK_OP(op, v1, v2)
#endif

#ifdef DCHECK_EQ
#undef DCHECK_EQ
#endif
#define DCHECK_EQ(v1, v2) BERBERIS_DCHECK_OP(==, v1, v2)

#ifdef DCHECK_NE
#undef DCHECK_NE
#endif
#define DCHECK_NE(v1, v2) BERBERIS_DCHECK_OP(!=, v1, v2)

#ifdef DCHECK_LT
#undef DCHECK_LT
#endif
#define DCHECK_LT(v1, v2) BERBERIS_DCHECK_OP(<, v1, v2)

#ifdef DCHECK_LE
#undef DCHECK_LE
#endif
#define DCHECK_LE(v1, v2) BERBERIS_DCHECK_OP(<=, v1, v2)

#ifdef DCHECK_GT
#undef DCHECK_GT
#endif
#define DCHECK_GT(v1, v2) BERBERIS_DCHECK_OP(>, v1, v2)

#ifdef DCHECK_GE
#undef DCHECK_GE
#endif
#define DCHECK_GE(v1, v2) BERBERIS_DCHECK_OP(>=, v1, v2)

#endif  // BERBERIS_BASE_CHECKS_H_
