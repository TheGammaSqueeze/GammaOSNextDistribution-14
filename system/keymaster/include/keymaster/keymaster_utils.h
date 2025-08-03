/*
 * Copyright 2021 The Android Open Source Project
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

#include <optional>

#include <keymaster/logger.h>

namespace keymaster {

/**
 * KmErrorOr contains either a keymaster_error_t or a value of type `T`.
 *
 * KmErrorOr values must be checked, or the destructor will log a messange and abort. They may be
 * checked by calling `isOk()`, which returns true iff the object contains a value rather than
 * an error, or by using the conversion to bool operator.
 *
 * After checking, the value may be retrieved using the value() methods or the operator-> or
 * operator* methods, as users of std::optional, etc., would expect.
 */
template <typename T> class KmErrorOr {
  public:
    // Construct empty, typically to move another error into later.  Default-constructed KmErrorOr's
    // don't need to be checked.  You can't get a value from them, though; that will crash.
    KmErrorOr() : error_(KM_ERROR_UNKNOWN_ERROR), value_checked_(true) {}

    // Construct from error.
    KmErrorOr(keymaster_error_t error) : error_(error) {}  // NOLINT(google-explicit-constructor)

    // Construct from value.
    KmErrorOr(const T& value) : value_(value) {}        // NOLINT(google-explicit-constructor)
    KmErrorOr(T&& value) : value_(std::move(value)) {}  // NOLINT(google-explicit-constructor)

    // Move-construct or move-assign
    KmErrorOr(KmErrorOr&& other) {  // NOLINT(google-explicit-constructor)
        error_ = KM_ERROR_UNKNOWN_ERROR;
        value_checked_ = true;
        operator=(std::move(other));
    }
    KmErrorOr& operator=(KmErrorOr&& other) {
        if (&other == this) return *this;

        std::swap(error_, other.error_);
        std::swap(value_, other.value_);
        std::swap(value_checked_, other.value_checked_);

        return *this;
    }

    // Don't copy.
    KmErrorOr(const KmErrorOr&) = delete;
    void operator=(const KmErrorOr&) = delete;

    ~KmErrorOr() {
        if (!value_checked_) {
            LOG_S("KmErrorOr not checked");
            abort();
        }
    }

    bool isOk() const {
        value_checked_ = true;
        return value_.has_value();
    }
    operator bool() const { return isOk(); }  // NOLINT(google-explicit-constructor)

    keymaster_error_t error() { return value_checked_ ? error_ : KM_ERROR_UNKNOWN_ERROR; };

    T& value() & { return value_.value(); }
    const T& value() const& { return value_.value(); }
    T&& value() && { return value_.value(); }
    const T&& value() const&& { return value_.value(); }

    T* operator->() { return &value_.value(); }
    const T* operator->() const { return &value_.value(); }

    T& operator*() & { return value_.value(); }
    const T& operator*() const& { return value_.value(); }
    T&& operator*() && { return std::move(value_).value(); }

  private:
    keymaster_error_t error_ = KM_ERROR_OK;
    std::optional<T> value_;
    mutable bool value_checked_ = false;
};

}  // namespace keymaster
