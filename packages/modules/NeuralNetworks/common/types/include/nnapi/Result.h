/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_RESULT_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_RESULT_H

#include <android-base/expected.h>

#include <optional>
#include <sstream>
#include <string>
#include <tuple>
#include <utility>

namespace android::nn {

/**
 * Type alias for `::android::base::expected` where the unexpected state is represented by a
 * std::string describing the error.
 *
 * See the following file for more information on ::android::base::expected:
 *   system/libbase/include/android-base/expected.h
 */
template <typename Type>
using Result = base::expected<Type, std::string>;

namespace detail {

template <typename... Ts>
class ErrorBuilder {
   public:
    template <typename... Us>
    explicit ErrorBuilder(Us&&... args) : mArgs(std::forward<Us>(args)...) {}

    template <typename T, typename E>
    operator base::expected<T, E>() /* NOLINT(google-explicit-constructor) */ {
        return std::apply(
                [this](Ts&&... args) {
                    return base::unexpected<E>(E{std::move(mStream).str(), std::move(args)...});
                },
                std::move(mArgs));
    }

    template <typename T>
    ErrorBuilder operator<<(const T& t) {
        mStream << t;
        return std::move(*this);
    }

   private:
    std::tuple<Ts...> mArgs;
    std::ostringstream mStream;
};

}  // namespace detail

/**
 * Creates an error builder for the case where no arguments are provided.
 */
template <typename... Types>
inline detail::ErrorBuilder<std::decay_t<Types>...> error(Types&&... args) {
    return detail::ErrorBuilder<std::decay_t<Types>...>(std::forward<Types>(args)...);
}

/**
 * Helper macro that will create an error builder already populated with the file name and line
 * number.
 *
 * This macro uses the following customization points:
 * * `::android::nn::error` is a set of functions that can be customized to return a specialized
 *    error builder object. Customization is based on the types of arguments passed and the number
 *    of arguments passed to `error`.
 *
 * Usage at error site:
 *     if (errorDetected) {
 *         return NN_ERROR() << "<error_message>";
 *     }
 *     return <regular_return_value>;
 */
#define NN_ERROR(...)                                                     \
    [&] {                                                                 \
        using ::android::nn::error;                                       \
        return error(__VA_ARGS__) << __FILE__ << ":" << __LINE__ << ": "; \
    }()

template <typename T, typename E>
bool nnTryHasValue(const base::expected<T, E>& o) {
    return o.has_value();
}

template <typename T, typename E>
T nnTryGetValue(base::expected<T, E> o) {
    return std::move(o).value();
}

template <typename T, typename E>
base::unexpected<E> nnTryGetError(base::expected<T, E> o) {
    return base::unexpected(std::move(o).error());
}

template <typename T>
bool nnTryHasValue(const std::optional<T>& o) {
    return o.has_value();
}

template <typename T>
T nnTryGetValue(std::optional<T> o) {
    return std::move(o).value();
}

template <typename T>
std::nullopt_t nnTryGetError(std::optional<T> /*o*/) {
    return std::nullopt;
}

/**
 * A macro that will exit from the current function if `expr` is unexpected or return the expected
 * value from the macro if `expr` is expected.
 *
 * This macro can currently be used on `::android::nn::Result`, `::android::base::expected`, or
 * `std::optional` values. To enable this macro to be used with other values, implement the
 * following functions for the type:
 * * `::android::nn::nnTryHasValue` returns `true` if the `expr` holds a successful value, false if
 *    the `expr` value holds an error
 * * `::android::nn::nnTryGetError` returns the error value of `expr` or crashes
 * * `::android::nn::nnTryGetValue` returns the successful value of `expr` or crashes
 *
 * Usage at call site:
 *     const auto [a, b, c] = NN_TRY(failableFunction(args));
 */
#define NN_TRY(expr)                                               \
    ({                                                             \
        using ::android::nn::nnTryHasValue;                        \
        using ::android::nn::nnTryGetValue;                        \
        using ::android::nn::nnTryGetError;                        \
        auto nnTryTemporaryResult = expr;                          \
        if (!nnTryHasValue(nnTryTemporaryResult)) {                \
            return nnTryGetError(std::move(nnTryTemporaryResult)); \
        }                                                          \
        nnTryGetValue(std::move(nnTryTemporaryResult));            \
    })

}  // namespace android::nn

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_RESULT_H
