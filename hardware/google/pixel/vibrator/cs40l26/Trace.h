/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <aidl/android/hardware/vibrator/BnVibrator.h>
#include <aidl/android/hardware/vibrator/BnVibratorCallback.h>
#include <android-base/stringprintf.h>
#include <hardware/hardware.h>
#include <hardware/vibrator.h>
#include <linux/input.h>

#include <iomanip>
#include <iostream>
#include <sstream>
#include <string>
#include <typeinfo>
#include <vector>

#include "DspMemChunk.h"

/* Macros to expand argument (x) into pair("x", x) for nicer tracing logs
 * Easily extendible past 7 elements
 */
#define WITH_NAME(a) std::make_pair(#a, a)

#define VA_NUM_ARGS(...) VA_NUM_ARGS_IMPL(__VA_ARGS__, 7, 6, 5, 4, 3, 2, 1)
#define VA_NUM_ARGS_IMPL(_1, _2, _3, _4, _5, _6, _7, N, ...) N

#define CONCAT_IMPL(x, y) x##y
#define MACRO_CONCAT(x, y) CONCAT_IMPL(x, y)

#define PREPEND_EACH_ARG_WITH_NAME_1(a) WITH_NAME(a)
#define PREPEND_EACH_ARG_WITH_NAME_2(a, ...) WITH_NAME(a), PREPEND_EACH_ARG_WITH_NAME_1(__VA_ARGS__)
#define PREPEND_EACH_ARG_WITH_NAME_3(a, ...) WITH_NAME(a), PREPEND_EACH_ARG_WITH_NAME_2(__VA_ARGS__)
#define PREPEND_EACH_ARG_WITH_NAME_4(a, ...) WITH_NAME(a), PREPEND_EACH_ARG_WITH_NAME_3(__VA_ARGS__)
#define PREPEND_EACH_ARG_WITH_NAME_5(a, ...) WITH_NAME(a), PREPEND_EACH_ARG_WITH_NAME_4(__VA_ARGS__)
#define PREPEND_EACH_ARG_WITH_NAME_6(a, ...) WITH_NAME(a), PREPEND_EACH_ARG_WITH_NAME_5(__VA_ARGS__)
#define PREPEND_EACH_ARG_WITH_NAME_7(a, ...) WITH_NAME(a), PREPEND_EACH_ARG_WITH_NAME_6(__VA_ARGS__)
#define PREPEND_EACH_ARG_WITH_NAME(...) \
    MACRO_CONCAT(PREPEND_EACH_ARG_WITH_NAME_, VA_NUM_ARGS(__VA_ARGS__))(__VA_ARGS__)

namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {

using ::android::base::StringPrintf;

/* Supported typenames */

// Fallback to typeid
template <typename T>
struct TypeName {
    static const char *Get() { return "<unknown>"; }
};

// Helper Macro
#define SUPPORT_TYPENAME(T)        \
    template <>                    \
    struct TypeName<T> {           \
        static const char *Get() { \
            return #T;             \
        }                          \
    }

SUPPORT_TYPENAME(bool);

SUPPORT_TYPENAME(int8_t);
SUPPORT_TYPENAME(int16_t);
SUPPORT_TYPENAME(int32_t);
SUPPORT_TYPENAME(uint8_t);
SUPPORT_TYPENAME(uint16_t);
SUPPORT_TYPENAME(uint32_t);

SUPPORT_TYPENAME(int8_t *);
SUPPORT_TYPENAME(int16_t *);
SUPPORT_TYPENAME(int32_t *);
SUPPORT_TYPENAME(uint8_t *);
SUPPORT_TYPENAME(uint16_t *);
SUPPORT_TYPENAME(uint32_t *);

SUPPORT_TYPENAME(const int16_t *);
SUPPORT_TYPENAME(const int32_t *);
SUPPORT_TYPENAME(const uint16_t *);
SUPPORT_TYPENAME(const uint32_t *);

SUPPORT_TYPENAME(float);
SUPPORT_TYPENAME(float *);
SUPPORT_TYPENAME(const float *);

SUPPORT_TYPENAME(std::string);
SUPPORT_TYPENAME(const std::string &);
SUPPORT_TYPENAME(const char **);

SUPPORT_TYPENAME(std::vector<ff_effect> *);
SUPPORT_TYPENAME(const ff_effect *);
SUPPORT_TYPENAME(ff_effect);
SUPPORT_TYPENAME(ff_effect *);

SUPPORT_TYPENAME(Effect);
SUPPORT_TYPENAME(EffectStrength);
SUPPORT_TYPENAME(std::vector<Effect> *);

SUPPORT_TYPENAME(const std::vector<PrimitivePwle> &);
SUPPORT_TYPENAME(const std::vector<PrimitivePwle>);
SUPPORT_TYPENAME(std::vector<PrimitivePwle> &);
SUPPORT_TYPENAME(std::vector<PrimitivePwle>);

SUPPORT_TYPENAME(const std::shared_ptr<IVibratorCallback> &&);
SUPPORT_TYPENAME(const std::shared_ptr<IVibratorCallback> &);
SUPPORT_TYPENAME(const std::shared_ptr<IVibratorCallback>);
SUPPORT_TYPENAME(std::shared_ptr<IVibratorCallback> &&);
SUPPORT_TYPENAME(std::shared_ptr<IVibratorCallback> &);
SUPPORT_TYPENAME(std::shared_ptr<IVibratorCallback>);

SUPPORT_TYPENAME(std::vector<CompositePrimitive> *);
SUPPORT_TYPENAME(CompositePrimitive);

SUPPORT_TYPENAME(const std::vector<CompositeEffect> &);
SUPPORT_TYPENAME(const std::vector<CompositeEffect>);
SUPPORT_TYPENAME(std::vector<CompositeEffect> &);
SUPPORT_TYPENAME(std::vector<CompositeEffect>);

SUPPORT_TYPENAME(std::vector<Braking> *);
SUPPORT_TYPENAME(struct pcm **);
SUPPORT_TYPENAME(const DspMemChunk *);
SUPPORT_TYPENAME(DspMemChunk *);

/* Support printing */

template <typename T>
std::ostream &operator<<(std::ostream &out, const std::vector<T> &arg) {
    out << "{";
    for (size_t i = 0; i < arg.size(); i++) {
        out << arg[i];
        if (i != arg.size() - 1) {
            out << ", ";
        }
    }
    out << "}";
    return out;
}

std::ostream &operator<<(std::ostream &out, const std::shared_ptr<IVibratorCallback> arg);
std::ostream &operator<<(std::ostream &out, const ff_effect *arg);
std::ostream &operator<<(std::ostream &out, const ff_effect &arg);
std::ostream &operator<<(std::ostream &out, const CompositePrimitive &arg);
std::ostream &operator<<(std::ostream &out, const Braking &arg);
std::ostream &operator<<(std::ostream &out, const PrimitivePwle &arg);
std::ostream &operator<<(std::ostream &out, const CompositeEffect &arg);
std::ostream &operator<<(std::ostream &out, const DspMemChunk *arg);
std::ostream &operator<<(std::ostream &out, Effect arg);
std::ostream &operator<<(std::ostream &out, EffectStrength arg);

/* Tracing classes */

class Trace {
  public:
    static void debug(int fd);
    static int depth() { return mDepth; }
    static void enter() { mDepth++; }
    static void exit() { mDepth--; }
    static void push(const std::string &t) { mTrace.push_back(t); }
    static void pop() { mTrace.pop_back(); }
    static void save() {
        std::vector<std::string> temp;
        std::swap(mTrace, temp);
        mPreviousTraces.push_back(std::move(temp));
    }

  private:
    static int mDepth;
    static std::vector<std::string> mTrace;
    static std::vector<std::vector<std::string>> mPreviousTraces;
};

class FunctionTrace : public Trace {
  public:
    FunctionTrace(const char *funcName);
    FunctionTrace(const char *className, const char *funcName);
    ~FunctionTrace();

    template <typename T>
    void addParameter(std::pair<const char *, T> t) {
        std::stringstream fmt;
        fmt << TypeName<T>::Get() << " " << t.first << ":" << t.second;
        mParameters.push_back(fmt.str());
    }

    template <typename T, typename... Ts>
    void addParameter(std::pair<const char *, T> t, Ts... ts) {
        addParameter(t);
        addParameter(ts...);
    }

    void addParameter() { return; }

    void save();

  private:
    std::string mClassName;
    std::string mFuncName;
    std::vector<std::string> mParameters;
};

class EffectTrace : public Trace {
  public:
    EffectTrace(uint16_t index, float scale, int32_t duration, const DspMemChunk *ch);
    void save();

  private:
    std::string mDescription;
};

}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
