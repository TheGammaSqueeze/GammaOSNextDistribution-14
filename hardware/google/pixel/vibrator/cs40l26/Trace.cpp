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

#include "Trace.h"

#include <aidl/android/hardware/vibrator/BnVibrator.h>
#include <log/log.h>

namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {

enum WaveformIndex : uint16_t {
    /* Physical waveform */
    WAVEFORM_LONG_VIBRATION_EFFECT_INDEX = 0,
    WAVEFORM_RESERVED_INDEX_1 = 1,
    WAVEFORM_CLICK_INDEX = 2,
    WAVEFORM_SHORT_VIBRATION_EFFECT_INDEX = 3,
    WAVEFORM_THUD_INDEX = 4,
    WAVEFORM_SPIN_INDEX = 5,
    WAVEFORM_QUICK_RISE_INDEX = 6,
    WAVEFORM_SLOW_RISE_INDEX = 7,
    WAVEFORM_QUICK_FALL_INDEX = 8,
    WAVEFORM_LIGHT_TICK_INDEX = 9,
    WAVEFORM_LOW_TICK_INDEX = 10,
    WAVEFORM_RESERVED_MFG_1,
    WAVEFORM_RESERVED_MFG_2,
    WAVEFORM_RESERVED_MFG_3,
    WAVEFORM_MAX_PHYSICAL_INDEX,
    /* OWT waveform */
    WAVEFORM_COMPOSE = WAVEFORM_MAX_PHYSICAL_INDEX,
    WAVEFORM_PWLE,
    /*
     * Refer to <linux/input.h>, the WAVEFORM_MAX_INDEX must not exceed 96.
     * #define FF_GAIN          0x60  // 96 in decimal
     * #define FF_MAX_EFFECTS   FF_GAIN
     */
    WAVEFORM_MAX_INDEX,
};

/* Support printing */

std::ostream &operator<<(std::ostream &out, std::shared_ptr<IVibratorCallback> arg) {
    return out << arg->descriptor << "()";
}

std::ostream &operator<<(std::ostream &out, const ff_effect *arg) {
    if (arg == nullptr) {
        return out;
    }

    return out << StringPrintf("%p", arg).c_str();
}

std::ostream &operator<<(std::ostream &out, const ff_effect &arg) {
    out << "(";
    out << "FF_PERIODIC, " << arg.id << ", " << arg.replay.length << "ms, "
        << arg.u.periodic.custom_len << " bytes";
    out << ")";
    return out;
}

std::ostream &operator<<(std::ostream &out, const CompositePrimitive &arg) {
    return out << toString(arg).c_str();
}

std::ostream &operator<<(std::ostream &out, const Braking &arg) {
    return out << toString(arg).c_str();
}

std::ostream &operator<<(std::ostream &out, const PrimitivePwle &arg) {
    out << "(";
    switch (arg.getTag()) {
        case PrimitivePwle::active: {
            auto active = arg.get<PrimitivePwle::active>();
            out << std::fixed << std::setprecision(2) << active.startAmplitude << ", "
                << active.startFrequency << "Hz, " << active.endAmplitude << ", "
                << active.endFrequency << "Hz, " << active.duration << "ms";
            break;
        }
        case PrimitivePwle::braking: {
            out << "Deprecated!";
            break;
        }
    }
    out << ")";
    return out;
}

std::ostream &operator<<(std::ostream &out, const CompositeEffect &arg) {
    out << "(" << arg.delayMs << "ms, " << toString(arg.primitive) << ", " << arg.scale << ")";
    return out;
}

std::ostream &operator<<(std::ostream &out, const DspMemChunk *arg) {
    if (arg == nullptr) {
        return out << "NULL";
    }

    out << "(";
    if (arg->type() == 14) {
        out << "WAVEFORM_COMPOSE, ";
    } else if (arg->type() == 15) {
        out << "WAVEFORM_PWLE, ";
    }
    out << arg->size() << " bytes";
    out << ")";
    return out;
}

std::ostream &operator<<(std::ostream &out, Effect arg) {
    return out << toString(arg).c_str();
}

std::ostream &operator<<(std::ostream &out, EffectStrength arg) {
    return out << toString(arg).c_str();
}

/* Trace Interface */

int Trace::mDepth = -1;
std::vector<std::string> Trace::mTrace = {};
std::vector<std::vector<std::string>> Trace::mPreviousTraces = {};

void Trace::debug(int fd) {
    std::vector<std::string> tTrace;
    std::swap(mTrace, tTrace);

    std::vector<std::vector<std::string>> tPreviousTraces;
    std::swap(mPreviousTraces, tPreviousTraces);

    dprintf(fd, "\nCurrent Trace:\n");
    for (auto line : tTrace) {
        dprintf(fd, "%s\n", line.c_str());
    }

    if (tPreviousTraces.size() > 0) {
        for (auto i = tPreviousTraces.size(); i--;) {
            dprintf(fd, "\nPrevious Trace #%zu:\n", i);
            for (auto line : tPreviousTraces[i]) {
                dprintf(fd, "%s\n", line.c_str());
            }
        }
    }
}

/* FunctionTrace Interface */

FunctionTrace::FunctionTrace(const char *funcName) : mClassName(""), mFuncName(funcName) {
    Trace::enter();
}

FunctionTrace::FunctionTrace(const char *className, const char *funcName)
    : mClassName(className), mFuncName(funcName) {
    Trace::enter();
}

FunctionTrace::~FunctionTrace() {
    Trace::exit();
}

void FunctionTrace::save() {
    std::stringstream fmt;
    int d = Trace::depth();
    for (int i = 0; i < d; i++) {
        fmt << "   ";
    }

    if (mClassName != "") {
        fmt << mClassName << "::";
    }
    fmt << mFuncName << "(";

    for (auto param : mParameters) {
        fmt << param;
        if (param != mParameters.back()) {
            fmt << ", ";
        }
    }

    fmt << ")";

    std::string fmtOut = fmt.str();
    ALOGI("%s", fmtOut.c_str());
    Trace::push(fmtOut);
}

/* Effect Trace Implementation */

EffectTrace::EffectTrace(uint16_t index, float scale, int32_t duration, const DspMemChunk *ch) {
    std::stringstream fmt;
    fmt << "Effect(";
    switch (index) {
        case WAVEFORM_LONG_VIBRATION_EFFECT_INDEX:
            fmt << "LONG_VIBRATION, " << scale << ", " << duration << ")";
            break;
        case WAVEFORM_CLICK_INDEX:
            fmt << "CLICK, " << scale << ")";
            break;
        case WAVEFORM_SHORT_VIBRATION_EFFECT_INDEX:
            fmt << "SHORT_VIBRATION, " << scale << ", " << duration << ")";
            break;
        case WAVEFORM_THUD_INDEX:
        case WAVEFORM_SPIN_INDEX:
        case WAVEFORM_QUICK_RISE_INDEX:
        case WAVEFORM_SLOW_RISE_INDEX:
        case WAVEFORM_QUICK_FALL_INDEX:
            break;
        case WAVEFORM_LIGHT_TICK_INDEX:
            fmt << "LIGHT_TICK, " << scale << ")";
            break;
        case WAVEFORM_LOW_TICK_INDEX:
            break;
        case WAVEFORM_COMPOSE:
            fmt << "COMPOSITE, " << ch->size() << " bytes)";
            break;
        case WAVEFORM_PWLE:
            fmt << "PWLE, " << ch->size() << " bytes)";
            break;
        default:
            break;
    }
    mDescription = fmt.str();
}

void EffectTrace::save() {
    std::stringstream fmt;
    for (int i = 0; i < depth(); i++) {
        fmt << "   ";
    }
    fmt << mDescription;

    std::string fmtOut = fmt.str();
    ALOGI("%s", fmtOut.c_str());
    Trace::push(fmtOut);
    Trace::save();
}

}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
