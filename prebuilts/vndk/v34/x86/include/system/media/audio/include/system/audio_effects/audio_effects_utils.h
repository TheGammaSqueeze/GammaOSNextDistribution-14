/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <sstream>
#include <string>
#include <sys/types.h>
#include <utils/Errors.h>

#include <system/audio_effect.h>

namespace android {
namespace effect {
namespace utils {

/**
 * A wrapper class of legacy effect_param_t to avoid raw pointer read/write everywhere.
 * The wrapper use the reference of underlying effect_param_t structure, and does not manage
 * ownership of the structure.
 * Thread safety is not in consideration in this class for now.
 */
class EffectParamWrapper {
 public:
  explicit EffectParamWrapper(effect_param_t& param) : mParam(param) {}

  // validate command size to be at least parameterSize + valueSize after effect_param_t
  bool validateCmdSize(size_t cmdSize) const {
    return (uint64_t)getPaddedParameterSize() + mParam.vsize + sizeof(effect_param_t) <=
           cmdSize;
  }

  /**
   *  validate parameter and value size to be at least equals to the target size.
   */
  bool validateParamValueSize(size_t paramSize, size_t valueSize) const {
    return mParam.psize >= paramSize && mParam.vsize >= valueSize;
  }

  std::string toString() const {
    std::ostringstream os;
    os << "effect_param_t: { ";
    os << "status: " << mParam.status << ", p: " << mParam.psize
       << " (padded: " << getPaddedParameterSize() << "), v: " << mParam.vsize
       << ", dataAddr: " << &mParam.data;
    os << "}";
    return os.str();
  }

  status_t copyDataWithCheck(void* dst, const void* src, size_t len,
                                    size_t offset, size_t max) {
    if (!dst || !src || len + offset > max) {
      return BAD_VALUE;
    }
    std::memcpy(dst, src, len);
    return OK;
  }

  status_t readFromData(void* buf, size_t len, size_t offset, size_t max) {
    return copyDataWithCheck(buf, mParam.data + offset, len, offset, max);
  }

  status_t getStatus() const { return mParam.status; }
  size_t getPaddedParameterSize() const { return padding(mParam.psize); }
  size_t getParameterSize() const { return mParam.psize; }
  size_t getValueSize() const { return mParam.vsize; }
  const uint8_t* getValueAddress() const {
    return (uint8_t*)mParam.data + getPaddedParameterSize();
  }

  uint64_t getTotalSize() const {
    return (uint64_t)sizeof(effect_param_t) + getPaddedParameterSize() + getValueSize();
  }

  /**
   * Get reference to effect_param_t.
   */
  const effect_param_t& getEffectParam() const { return mParam; }

  bool operator==(const EffectParamWrapper& other) const {
    return (&other == this) || 0 == std::memcmp(&mParam, &other.mParam, sizeof(effect_param_t));
  }

  /**
   * Padding psize to 32 bits aligned, because "start of value field inside
   * the data field is always on a 32 bits boundary".
   */
  static constexpr inline size_t padding(size_t size) {
    return ((size - 1) / sizeof(int32_t) + 1) * sizeof(int32_t);
  }

 private:
  /* member with variable sized type at end of class */
  const effect_param_t& mParam;
};

/**
 * Reader class of effect_param_t data buffer.
 */
class EffectParamReader : public EffectParamWrapper {
 public:
  explicit EffectParamReader(effect_param_t& param)
      : EffectParamWrapper(param), mValueROffset(getPaddedParameterSize()) {}

  /**
   * Read n value of type T from data to buf, mParamROffset will advance n * sizeof(T) if success,
   * no advance if failure.
   */
  template <typename T>
  status_t readFromParameter(T* buf, size_t n = 1) {
    size_t len = n * sizeof(T);
    status_t ret = readFromData(buf, len, mParamROffset /* param offset */,
                                getParameterSize() /* max offset */);
    if (OK == ret) {
       mParamROffset += len;
    }
    return ret;
  }

  /**
   * Read number of value in type T from data to buf, mValueROffset will advance n * sizeof(T) if
   * success, no advance if failure.
   */
  template <typename T>
  status_t readFromValue(T* buf, size_t n = 1) {
    size_t len = n * sizeof(T);
    status_t ret = readFromData(buf, len, mValueROffset /* data offset */,
                                getPaddedParameterSize() + getValueSize() /* max offset */);
    if (OK == ret) {
       mValueROffset += len;
    }
    return ret;
  }

  std::string toString() const {
    std::ostringstream os;
    os << EffectParamWrapper::toString();
    os << ", paramROffset: " << mParamROffset;
    os << ", valueROffset: " << mValueROffset;
    return os.str();
  }

 private:
  size_t mParamROffset = 0;
  size_t mValueROffset = 0;
};

/**
 * Writer class of effect_param_t data buffer.
 */
class EffectParamWriter : public EffectParamReader {
 public:
  explicit EffectParamWriter(effect_param_t& param)
      : EffectParamReader(param), mParam(param), mValueWOffset(getPaddedParameterSize()) {
  }

  /**
   * Write n number of param in type T from buf to data, mParamWOffset will advance n * sizeof(T)
   * if success, no advance if failure.
   */
  template <typename T>
  status_t writeToParameter(const T* buf, size_t n = 1) {
    size_t len = n * sizeof(T);
    status_t ret = writeToData(buf, len, mParamWOffset /* data offset */,
                               getParameterSize() /* max */);
    if (OK == ret) {
       mParamWOffset += len;
    }
    return ret;
  }

  status_t writeToData(const void* buf, size_t len, size_t offset, size_t max) {
    return copyDataWithCheck(mParam.data + offset, buf, len, offset, max);
  }
  /**
   * Write n number of value in type T from buf to data, mValueWOffset will advance n * sizeof(T)
   * if success, no advance if failure.
   */
  template <typename T>
  status_t writeToValue(const T* buf, size_t n = 1) {
    size_t len = n * sizeof(T);
    status_t ret = writeToData(buf, len, mValueWOffset /* data offset */,
                               getPaddedParameterSize() + getValueSize() /* max */);
    if (OK == ret) {
       mValueWOffset += len;
    }
    return ret;
  }

  /**
   * Set the current value write offset to vsize.
   * Together with getTotalSize(), can be used by getParameter to set replySize.
   */
  void finishValueWrite() { mParam.vsize = mValueWOffset - getPaddedParameterSize(); }

  void setStatus(status_t status) { mParam.status = status; }

  std::string toString() const {
    std::ostringstream os;
    os << EffectParamReader::toString();
    os << ", paramWOffset: " << mParamWOffset;
    os << ", valueWOffset: " << mValueWOffset;
    return os.str();
  }

 private:
  effect_param_t& mParam;
  size_t mParamWOffset = 0;
  size_t mValueWOffset = 0;
};

}  // namespace utils
}  // namespace effect
}  // namespace android
