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

#ifndef BERBERIS_BASE_FORMAT_BUFFER_H_
#define BERBERIS_BASE_FORMAT_BUFFER_H_

#include <limits.h>  // CHAR_BIT
#include <stdarg.h>
#include <stddef.h>  // size_t
#include <stdint.h>  // (u)intmax_t, uintptr_t

#include <berberis/base/arena_string.h>

namespace berberis {

// FormatBuffer functions are reentrant and safe to call from signal handlers.
// Implementation avoids using libc to ensure this.

// FormatBufferImpl is a template building block that extracts arguments from a custom list
// and appends formatted output to a custom buffer.
//
// Output buffer type requirements:
//   bool Put(char c) - add one char to buffer, return true if added OK
//
// Argument list type requirements:
//   (u)intmax_t Get(U)Int()      - extract argument for %d, %u, %x
//   (u)intmax_t Get(U)Long()     - extract argument for %ld, %lu, %lx
//   (u)intmax_t Get(U)LongLong() - extract argument for %lld, %llu, %llx
//   const char* GetCStr()        - extract argument for %s
//   uintmax_t   GetPtrAsUInt()   - extract argument for %p
//   intmax_t    GetChar()        - extract argument for %c
//   uintmax_t   GetSizeT()       - extract argument for %zu, %zx

namespace format_buffer_internal {

// printf format specifier is
//   %[flags][width][.precision][length]specifier
//
// length and specifier are captured by argument extraction and printing helper
// flags, width and precision are captured in style
struct Style {
  size_t width = 0;
  bool pad_number = false;
};

template <typename Out>
bool PutString(Out* out, const Style& style, const char* s) {
  if (!s) {
    s = "(null)";
  }

  if (style.width) {
    size_t n = 0;
    for (const char* p = s; *p; ++p, ++n) {
    }
    for (; n < style.width; ++n) {
      if (!out->Put(' ')) {
        return false;
      }
    }
  }

  for (const char* p = s; *p; ++p) {
    if (!out->Put(*p)) {
      return false;
    }
  }

  return true;
}

template <typename Out>
bool PutChar(Out* out, const Style& style, char c) {
  for (size_t n = 1; n < style.width; ++n) {
    if (!out->Put(' ')) {
      return false;
    }
  }
  return out->Put(c);
}

template <typename Out>
bool PutUInt(Out* out,
             const Style& style,
             const char* prefix,
             size_t prefix_size,
             uintmax_t v,
             size_t base) {
  // Reserve for max possible count of binary digits.
  char buf[sizeof(uintmax_t) * CHAR_BIT];
  char* p = buf;

  // Generate digits in reverse order.
  if (v == 0) {
    *p++ = '0';
  } else {
    while (v) {
      size_t d = v % base;
      v /= base;
      if (d < 10) {
        *p++ = '0' + d;
      } else {
        *p++ = 'a' + (d - 10);
      }
    }
  }

  size_t n_print = prefix_size + (p - buf);
  size_t n_pad = style.width > n_print ? style.width - n_print : 0;

  // Pad with spaces before prefix.
  if (!style.pad_number) {
    for (; n_pad > 0; --n_pad) {
      if (!out->Put(' ')) {
        return false;
      }
    }
  }

  for (size_t i = 0; i < prefix_size; ++i) {
    if (!out->Put(prefix[i])) {
      return false;
    }
  }

  // Pad with zeros after prefix.
  if (style.pad_number) {
    for (; n_pad > 0; --n_pad) {
      if (!out->Put('0')) {
        return false;
      }
    }
  }

  while (p != buf) {
    if (!out->Put(*--p)) {
      return false;
    }
  }

  return true;
}

template <typename Out>
bool PutInt(Out* out, const Style& style, intmax_t v, size_t base) {
  if (v < 0) {
    return PutUInt(out, style, "-", 1, -v, base);
  }
  return PutUInt(out, style, nullptr, 0, v, base);
}

// This class is a syntax sugar to avoid passing format pointer and argument list by reference.
template <typename Out, typename Args>
class FormatAndArgs {
 public:
  FormatAndArgs(const char* format, Args* args) : format_(format), args_(args) {}

  bool Put(Out* out) {
    while (char c = *format_++) {
      if (c == '%') {
        if (!PutSpec(out)) {
          return false;
        }
      } else {
        if (!out->Put(c)) {
          return false;
        }
      }
    }
    return true;
  }

 private:
  bool PutSpec(Out* out) {
    Style style = ParseStyle();
    switch (*format_++) {
      case '%':
        return out->Put('%');
      case 'c':
        return PutChar(out, style, args_->GetChar());
      case 's':
        return PutString(out, style, args_->GetCStr());
      case 'p':
        return PutUInt(out, style, "0x", 2, args_->GetPtrAsUInt(), 16);
      case 'd':
        return PutInt(out, style, args_->GetInt(), 10);
      case 'u':
        return PutUInt(out, style, nullptr, 0, args_->GetUInt(), 10);
      case 'x':
        return PutUInt(out, style, nullptr, 0, args_->GetUInt(), 16);
      case 'l':
        return PutLongSpec(out, style);
      case 'z':
        return PutSizeTSpec(out, style);
      default:
        return false;
    }
  }

  bool PutLongSpec(Out* out, const Style& style) {
    switch (*format_++) {
      case 'd':
        return PutInt(out, style, args_->GetLong(), 10);
      case 'u':
        return PutUInt(out, style, nullptr, 0, args_->GetULong(), 10);
      case 'x':
        return PutUInt(out, style, nullptr, 0, args_->GetULong(), 16);
      case 'l':
        return PutLongLongSpec(out, style);
      default:
        return false;
    }
  }

  bool PutLongLongSpec(Out* out, const Style& style) {
    switch (*format_++) {
      case 'd':
        return PutInt(out, style, args_->GetLongLong(), 10);
      case 'u':
        return PutUInt(out, style, nullptr, 0, args_->GetULongLong(), 10);
      case 'x':
        return PutUInt(out, style, nullptr, 0, args_->GetULongLong(), 16);
      default:
        return false;
    }
  }

  bool PutSizeTSpec(Out* out, const Style& style) {
    switch (*format_++) {
      case 'u':
        return PutUInt(out, style, nullptr, 0, args_->GetSizeT(), 10);
      case 'x':
        return PutUInt(out, style, nullptr, 0, args_->GetSizeT(), 16);
      default:
        return false;
    }
  }

  Style ParseStyle() {
    Style style;
    char c = *format_;
    if (c == '0') {
      c = *++format_;
      style.pad_number = true;
    }
    if (c == '*') {
      ++format_;
      style.width = args_->GetInt();
    } else {
      style.width = 0;
      for (; c >= '0' && c <= '9'; c = *++format_) {
        style.width = style.width * 10 + (c - '0');
      }
    }
    return style;
  }

  const char* format_;
  Args* args_;
};

}  // namespace format_buffer_internal

// Output to char array.
class CStrBuffer {
 public:
  // TODO(eaeltsin): check 'buf' is not null!
  CStrBuffer(char* buf, size_t buf_size) : start_(buf), cur_(buf), end_(buf + buf_size) {}

  bool Put(char c) {
    if (cur_ == end_) {
      return false;
    }
    *cur_++ = c;
    return true;
  }

  size_t Size() const { return cur_ - start_; }

 private:
  char* const start_;
  char* cur_;
  char* const end_;
};

// Output to a dynamic char array.
class DynamicCStrBuffer {
 public:
  DynamicCStrBuffer() : static_cur_(static_buf_), dynamic_buf_(&arena_) {}

  bool Put(char c) {
    if (IsDynamic()) {
      dynamic_buf_ += c;
      return true;
    }
    if (static_cur_ == (static_buf_ + kStaticSize)) {
      dynamic_buf_.append(static_buf_, kStaticSize);
      return Put(c);
    }
    *static_cur_++ = c;
    return true;
  }

  size_t Size() const { return IsDynamic() ? dynamic_buf_.size() : (static_cur_ - static_buf_); }

  const char* Data() const { return IsDynamic() ? dynamic_buf_.data() : static_buf_; }

  bool IsDynamicForTesting() const { return IsDynamic(); }

 private:
  bool IsDynamic() const { return !dynamic_buf_.empty(); }

  static constexpr size_t kStaticSize = 256;
  char static_buf_[kStaticSize];
  char* static_cur_;
  // The buffer is static initially, but if statically allocated storage is
  // exhausted we use dynamic ArenaString.
  Arena arena_;
  // Do not use std::string to avoid mallocs.
  ArenaString dynamic_buf_;
};

// Extract arguments from va_list.
class FormatBufferVaListArgs {
 public:
  explicit FormatBufferVaListArgs(va_list ap) { va_copy(ap_, ap); }

  const char* GetCStr() { return va_arg(ap_, const char*); }
  uintmax_t GetPtrAsUInt() { return reinterpret_cast<uintptr_t>(va_arg(ap_, void*)); }
  intmax_t GetInt() { return va_arg(ap_, int); }
  intmax_t GetLong() { return va_arg(ap_, long); }
  intmax_t GetLongLong() { return va_arg(ap_, long long); }
  uintmax_t GetUInt() { return va_arg(ap_, unsigned int); }
  uintmax_t GetULong() { return va_arg(ap_, unsigned long); }
  uintmax_t GetULongLong() { return va_arg(ap_, unsigned long long); }
  intmax_t GetChar() { return va_arg(ap_, int); }
  uintmax_t GetSizeT() { return va_arg(ap_, size_t); }

 private:
  va_list ap_;
};

template <typename Out, typename Args>
bool FormatBufferImpl(Out* out, const char* format, Args* args) {
  return format_buffer_internal::FormatAndArgs<Out, Args>(format, args).Put(out);
}

template <typename Out>
bool __attribute__((__format__(printf, 2, 3)))
FormatBufferImplF(Out* out, const char* format, ...) {
  va_list ap;
  va_start(ap, format);
  FormatBufferVaListArgs args(ap);
  bool status = FormatBufferImpl(out, format, &args);
  va_end(ap);
  return status;
}

// Writes at most 'buf_size' characters, INcluding '\0' terminator.
// Returns number of written characters, EXcluding '\0' terminator.
// Does NOT report errors, just stops printing.
size_t FormatBuffer(char* buf, size_t buf_size, const char* format, ...);
size_t FormatBufferV(char* buf, size_t buf_size, const char* format, va_list ap);

}  // namespace berberis

#endif  // BERBERIS_BASE_FORMAT_BUFFER_H_
