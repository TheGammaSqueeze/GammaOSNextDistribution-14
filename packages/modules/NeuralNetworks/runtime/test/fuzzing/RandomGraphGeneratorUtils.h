/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_TEST_FUZZING_RANDOM_GRAPH_GENERATOR_UTILS_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_TEST_FUZZING_RANDOM_GRAPH_GENERATOR_UTILS_H

#include <android/log.h>

#include <chrono>
#include <fstream>
#include <limits>
#include <memory>
#include <random>
#include <sstream>
#include <string>
#include <vector>

#include "RandomGraphGenerator.h"
#include "RandomVariable.h"
#include "TestHarness.h"
#include "TestNeuralNetworksWrapper.h"

namespace android {
namespace nn {
namespace fuzzing_test {

#define NN_FUZZER_LOG_INIT(filename) Logger::get()->init((filename))
#define NN_FUZZER_LOG_WRITE_FATAL_TO_SYSLOG(logTag) \
    LoggerStream::writeAbortMessageToSystemLog(logTag)
#define NN_FUZZER_LOG_CLOSE Logger::get()->close()
#define NN_FUZZER_LOG              \
    if (!Logger::get()->enabled()) \
        ;                          \
    else                           \
        LoggerStream(false) << alignedString(__FUNCTION__, 20)
#define NN_FUZZER_CHECK(condition)                                                             \
    if ((condition))                                                                           \
        ;                                                                                      \
    else                                                                                       \
        LoggerStream(true) << alignedString(__FUNCTION__, 20) << "Check failed " << #condition \
                           << ": "

// A Singleton manages the global configurations of logging.
class Logger {
   public:
    static Logger* get() {
        static Logger instance;
        return &instance;
    }
    void init(const std::string& filename) {
        os.open(filename);
        mStart = std::chrono::high_resolution_clock::now();
    }
    bool enabled() { return os.is_open(); }
    void close() {
        if (os.is_open()) os.close();
    }
    void log(const std::string& str) {
        if (os.is_open()) os << getElapsedTime() << str << std::flush;
    }

   private:
    Logger() = default;
    Logger(const Logger&) = delete;
    Logger& operator=(const Logger&) = delete;
    std::string getElapsedTime();
    std::ofstream os;
    std::chrono::time_point<std::chrono::high_resolution_clock> mStart;
};

// Controls logging of a single line.
class LoggerStream {
   public:
    LoggerStream(bool abortAfterLog) : mAbortAfterLog(abortAfterLog) {}
    ~LoggerStream() {
        Logger::get()->log(ss.str() + '\n');
        if (mAbortAfterLog) {
            if (LoggerStream::mWriteAbortMessageToSystemLog) {
                __android_log_print(ANDROID_LOG_FATAL, mLogTag.c_str(), "%s", ss.str().c_str());
            } else {
                std::cout << ss.str() << std::endl;
            }
            abort();
        }
    }

    template <typename T>
    LoggerStream& operator<<(const T& str) {
        ss << str;
        return *this;
    }

    static void writeAbortMessageToSystemLog(const std::string& logTag) {
        LoggerStream::mWriteAbortMessageToSystemLog = true;
        LoggerStream::mLogTag = logTag;
    }

   private:
    LoggerStream(const LoggerStream&) = delete;
    LoggerStream& operator=(const LoggerStream&) = delete;
    std::stringstream ss;
    bool mAbortAfterLog;

    static bool mWriteAbortMessageToSystemLog;
    static std::string mLogTag;
};

template <typename T>
inline std::string joinStr(const std::string& joint, const std::vector<T>& items) {
    std::stringstream ss;
    for (uint32_t i = 0; i < items.size(); i++) {
        if (i == 0) {
            ss << items[i];
        } else {
            ss << joint << items[i];
        }
    }
    return ss.str();
}

template <typename T, class Function>
inline std::string joinStr(const std::string& joint, const std::vector<T>& items, Function str) {
    std::stringstream ss;
    for (uint32_t i = 0; i < items.size(); i++) {
        if (i != 0) ss << joint;
        ss << str(items[i]);
    }
    return ss.str();
}

template <typename T>
inline std::string joinStr(const std::string& joint, int limit, const std::vector<T>& items) {
    if (items.size() > static_cast<size_t>(limit)) {
        std::vector<T> topMax(items.begin(), items.begin() + limit);
        std::stringstream ss;
        ss << joinStr(joint, topMax) << ", (" << (items.size() - limit) << " omitted), "
           << items.back();
        return ss.str();
    } else {
        return joinStr(joint, items);
    }
}

static const bool kScalarDataType[]{
        true,   // ANEURALNETWORKS_FLOAT32
        true,   // ANEURALNETWORKS_INT32
        true,   // ANEURALNETWORKS_UINT32
        false,  // ANEURALNETWORKS_TENSOR_FLOAT32
        false,  // ANEURALNETWORKS_TENSOR_INT32
        false,  // ANEURALNETWORKS_TENSOR_QUANT8_ASYMM
        true,   // ANEURALNETWORKS_BOOL
        false,  // ANEURALNETWORKS_TENSOR_QUANT16_SYMM
        false,  // ANEURALNETWORKS_TENSOR_FLOAT16
        false,  // ANEURALNETWORKS_TENSOR_BOOL8
        true,   // ANEURALNETWORKS_FLOAT16
        false,  // ANEURALNETWORKS_TENSOR_QUANT8_SYMM_PER_CHANNEL
        false,  // ANEURALNETWORKS_TENSOR_QUANT16_ASYMM
        false,  // ANEURALNETWORKS_TENSOR_QUANT8_SYMM
        false,  // ANEURALNETWORKS_TENSOR_QUANT8_ASYMM_SIGNED
};

static const uint32_t kSizeOfDataType[]{
        4,  // ANEURALNETWORKS_FLOAT32
        4,  // ANEURALNETWORKS_INT32
        4,  // ANEURALNETWORKS_UINT32
        4,  // ANEURALNETWORKS_TENSOR_FLOAT32
        4,  // ANEURALNETWORKS_TENSOR_INT32
        1,  // ANEURALNETWORKS_TENSOR_QUANT8_ASYMM
        1,  // ANEURALNETWORKS_BOOL
        2,  // ANEURALNETWORKS_TENSOR_QUANT16_SYMM
        2,  // ANEURALNETWORKS_TENSOR_FLOAT16
        1,  // ANEURALNETWORKS_TENSOR_BOOL8
        2,  // ANEURALNETWORKS_FLOAT16
        1,  // ANEURALNETWORKS_TENSOR_QUANT8_SYMM_PER_CHANNEL
        2,  // ANEURALNETWORKS_TENSOR_QUANT16_ASYMM
        1,  // ANEURALNETWORKS_TENSOR_QUANT8_SYMM
        1,  // ANEURALNETWORKS_TENSOR_QUANT8_ASYMM_SIGNED
};

inline std::ostream& operator<<(std::ostream& os, const RandomVariableType& type) {
    static const std::string typeNames[] = {"FREE", "CONST", "OP"};
    return os << typeNames[static_cast<int>(type)];
}

inline std::string alignedString(std::string str, int width) {
    str.push_back(':');
    str.resize(width + 1, ' ');
    return str;
}

inline std::ostream& operator<<(std::ostream& os, const RandomVariableRange& range) {
    return os << "[" + joinStr(", ", 20, range.getChoices()) + "]";
}

inline std::ostream& operator<<(std::ostream& os, const RandomOperandType& type) {
    static const std::string typeNames[] = {"Input", "Output", "Internal", "Parameter", "No Value"};
    return os << typeNames[static_cast<int>(type)];
}

inline std::ostream& operator<<(std::ostream& os, const RandomVariableNode& var) {
    os << "var" << var->index << " = ";
    switch (var->type) {
        case RandomVariableType::FREE:
            os << "FREE " << var->range;
            break;
        case RandomVariableType::CONST:
            os << "CONST " << var->value;
            break;
        case RandomVariableType::OP:
            os << "var" << var->parent1->index << " " << var->op->getName();
            if (var->parent2 != nullptr) os << " var" << var->parent2->index;
            os << ", " << var->range;
            break;
        default:
            NN_FUZZER_CHECK(false);
    }
    os << ", timestamp = " << var->timestamp;
    return os;
}

inline std::ostream& operator<<(std::ostream& os, const RandomVariable& var) {
    return os << "var" + std::to_string(var.get()->index);
}

inline std::ostream& operator<<(std::ostream& os, const RandomOperand& op) {
    return os << op.type << ", dimension = ["
              << joinStr(", ", op.dimensions,
                         [](const RandomVariable& var) { return std::to_string(var.getValue()); })
              << "], scale = " << op.scale << " , zero_point = " << op.zeroPoint;
}

// This class is a workaround for two issues our code relies on:
// 1. sizeof(bool) is implementation defined.
// 2. vector<bool> does not allow direct pointer access via the data() method.
class bool8 {
   public:
    bool8() : mValue() {}
    /* implicit */ bool8(bool value) : mValue(value) {}
    inline operator bool() const { return mValue != 0; }

   private:
    uint8_t mValue;
};
static_assert(sizeof(bool8) == 1, "size of bool8 must be 8 bits");

struct RandomNumberGenerator {
    static std::mt19937 generator;
};

inline bool getBernoulli(double p) {
    std::bernoulli_distribution dis(p);
    return dis(RandomNumberGenerator::generator);
}

template <typename T>
inline constexpr bool nnIsFloat = std::is_floating_point_v<T> || std::is_same_v<T, _Float16>;

// getUniform for floating point values operates on a open interval (lower, upper).
// This is important for generating a scale that is greater than but not equal to a lower bound.
template <typename T>
inline std::enable_if_t<nnIsFloat<T>, T> getUniform(T lower, T upper) {
    float nextLower = std::nextafter(static_cast<float>(lower), std::numeric_limits<float>::max());
    std::uniform_real_distribution<float> dis(nextLower, upper);
    return dis(RandomNumberGenerator::generator);
}
template <typename T>
inline std::enable_if_t<nnIsFloat<T>, T> getUniformNonZero(T lower, T upper, T zeroPoint) {
    if (upper >= zeroPoint) {
        upper = std::nextafter(static_cast<float>(upper), std::numeric_limits<float>::min());
    }
    std::uniform_real_distribution<float> dis(lower, upper);
    const float value = dis(RandomNumberGenerator::generator);
    return value >= zeroPoint ? std::nextafter(value, std::numeric_limits<float>::max()) : value;
}

// getUniform for integers operates on a closed interval [lower, upper].
// This is important that 255 should be included as a valid candidate for QUANT8_ASYMM values.
//
// This template only accepts int8_t, uint8_t, bool, or the types accepted by stdlib's
// uniform_int_distribution. `char` is not an officially supported type, but may be
// supported depending on library implementation.
template <typename T>
inline std::enable_if_t<std::is_integral_v<T>, T> getUniform(T lower, T upper) {
    // uniform_int_distribution is only defined by the stdlib standard
    // when T is of types short, int, long, long long,
    // unsigned short, unsigned int, unsigned long, or unsigned long long.
    //
    // However, existing code relies on getUniform working for some smaller types,
    // so we special case them here.
    if constexpr (std::is_same_v<T, uint8_t> || std::is_same_v<T, int8_t> ||
                  std::is_same_v<T, bool>) {
        // We can get away with using bool here because lower and upper are 0 or 1.
        // The uint8_t case can always be upsized to int16_t and then downsized because
        // we'll never generate a random number lower than the minimum of 0 when T
        // is unsigned.
        std::uniform_int_distribution<int16_t> dis(lower, upper);
        return static_cast<T>(dis(RandomNumberGenerator::generator));
    } else {
        std::uniform_int_distribution<T> dis(lower, upper);
        return dis(RandomNumberGenerator::generator);
    }
}
template <typename T>
inline std::enable_if_t<std::is_integral_v<T>, T> getUniformNonZero(T lower, T upper, T zeroPoint) {
    if (upper >= zeroPoint) upper--;
    const T value = getUniform(lower, upper);
    return value >= zeroPoint ? value + 1 : value;
}

template <typename T>
inline const T& getRandomChoice(const std::vector<T>& choices) {
    NN_FUZZER_CHECK(!choices.empty()) << "Empty choices!";
    std::uniform_int_distribution<size_t> dis(0, choices.size() - 1);
    size_t i = dis(RandomNumberGenerator::generator);
    return choices[i];
}

template <typename T>
inline void randomShuffle(std::vector<T>* vec) {
    std::shuffle(vec->begin(), vec->end(), RandomNumberGenerator::generator);
}

}  // namespace fuzzing_test
}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_TEST_FUZZING_RANDOM_GRAPH_GENERATOR_UTILS_H
