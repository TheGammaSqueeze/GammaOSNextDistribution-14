// Copyright 2017-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#include <cstddef>
#include "audio_core/dsp_interface.h"
#include <string>
#include <mutex>
#include <cstring>
#include "audio_core/sink.h"
#include "audio_core/sink_details.h"
#include "common/assert.h"
#include "common/logging/log.h"
#include "common/settings.h"
#include "core/core.h"
#include "core/dumping/backend.h"

namespace AudioCore {

DspInterface::DspInterface(Core::System& system_) : system(system_) {}

namespace {
// One platform sink for the whole process. A save state load shuts the system
// down and initialises it again, which used to destroy and reopen the platform
// audio streams each time; on Android that AudioTrack churn races audioserver
// releasing the old track's callback binder and crashed binder threads.
struct SharedSink {
    std::mutex mutex;
    std::shared_ptr<Sink> sink;
    SinkType type{};
    std::string device;
};
SharedSink& GetSharedSink() {
    static SharedSink shared;
    return shared;
}
} // namespace

DspInterface::~DspInterface() {
    if (sink) {
        // The sink lives on: detach this DSP from it before going away.
        sink->SetCallback([](s16* buffer, std::size_t num_frames) {
            std::memset(buffer, 0, num_frames * 2 * sizeof(s16));
        });
    }
}

void DspInterface::SetSink(AudioCore::SinkType sink_type, std::string_view audio_device) {
    // ApplySettings calls this on every settings change. Re-creating an
    // unchanged sink tears down and reopens the platform audio streams each
    // time (on Android: a cubeb probe stream plus a new AudioTrack), churn that
    // races audioserver's release of the old track's callback binder and has
    // crashed the process on a binder thread. Keep an unchanged sink.
    if (sink && sink_type == current_sink_type && audio_device == current_audio_device) {
        return;
    }
    current_sink_type = sink_type;
    current_audio_device = std::string(audio_device);

    auto& shared = GetSharedSink();
    std::scoped_lock lock{shared.mutex};
    LOG_INFO(Audio, "SetSink type {} device '{}' (shared: {} type {} device '{}')",
             static_cast<u32>(sink_type), audio_device, shared.sink ? "yes" : "no",
             static_cast<u32>(shared.type), shared.device);
    if (shared.sink && shared.type == sink_type && shared.device == audio_device) {
        sink = shared.sink;   // reuse the running platform stream
    } else {
        // Dispose of the current sink first to avoid contention.
        sink.reset();
        shared.sink.reset();
        shared.sink = AudioCore::GetSinkDetails(sink_type).create_sink(audio_device);
        shared.type = sink_type;
        shared.device = std::string(audio_device);
        sink = shared.sink;
    }
    sink->SetCallback(
        [this](s16* buffer, std::size_t num_frames) { OutputCallback(buffer, num_frames); });
    time_stretcher.SetOutputSampleRate(sink->GetNativeSampleRate());
}

Sink& DspInterface::GetSink() {
    ASSERT(sink);
    return *sink.get();
}

void DspInterface::EnableStretching(bool enable) {
    enable_time_stretching = enable;
}

void DspInterface::OutputFrame(StereoFrame16 frame) {
    if (!sink) {
        return;
    }

    if (sink->ImmediateSubmission()) {
        sink->PushSamples(frame.data(), frame.size());
    } else {
        fifo.Push(frame.data(), frame.size());
    }

    auto video_dumper = system.GetVideoDumper();
    if (video_dumper && video_dumper->IsDumping()) {
        video_dumper->AddAudioFrame(std::move(frame));
    }
}

void DspInterface::OutputSample(std::array<s16, 2> sample) {
    if (!sink) {
        return;
    }

    if (sink->ImmediateSubmission()) {
        sink->PushSamples(&sample, 1);
    } else {
        fifo.Push(&sample, 1);
    }

    auto video_dumper = system.GetVideoDumper();
    if (video_dumper && video_dumper->IsDumping()) {
        video_dumper->AddAudioSample(std::move(sample));
    }
}

void DspInterface::OutputCallback(s16* buffer, std::size_t num_frames) {
    // Determine if we should stretch based on the current emulation speed.
    // TODO: Only activate audio stretching when emulation speed goes below 95% threshold
    //       (see #2487) -OS
    // Fast forward always stretches: the audio then plays at the emulation speed instead of
    // piling up behind a 1x output.
    const bool stretch = enable_time_stretching.load() || Settings::values.gammaos_fast_forward;
    if (performing_time_stretching && !stretch) {
        // If we just stopped stretching, flush the stretcher before returning to normal output.
        flushing_time_stretcher = true;
    }
    performing_time_stretching = stretch;

    std::size_t frames_written = 0;
    if (performing_time_stretching) {
        const std::vector<s16> in{fifo.Pop()};
        const std::size_t num_in{in.size() / 2};
        frames_written = time_stretcher.Process(in.data(), num_in, buffer, num_frames);
    } else {
        if (flushing_time_stretcher) {
            time_stretcher.Flush();
            frames_written = time_stretcher.Process(nullptr, 0, buffer, num_frames);
            flushing_time_stretcher = false;

            // Make sure any frames that did not fit are cleared from the time stretcher,
            // so that they do not bleed into the next time the stretcher is enabled.
            time_stretcher.Clear();
        }
        frames_written += fifo.Pop(buffer, num_frames - frames_written);
    }

    if (frames_written > 0) {
        std::memcpy(&last_frame[0], buffer + 2 * (frames_written - 1), 2 * sizeof(s16));
    }

    // Hold last emitted frame; this prevents popping.
    for (std::size_t i = frames_written; i < num_frames; i++) {
        std::memcpy(buffer + 2 * i, &last_frame[0], 2 * sizeof(s16));
    }

    // Implementation of the hardware volume slider
    // A cubic curve is used to approximate a linear change in human-perceived loudness
    const float linear_volume = std::clamp(Settings::Volume(), 0.0f, 1.0f);
    if (linear_volume != 1.0) {
        const float volume_scale_factor = linear_volume * linear_volume * linear_volume;
        for (std::size_t i = 0; i < num_frames; i++) {
            buffer[i * 2 + 0] = static_cast<s16>(buffer[i * 2 + 0] * volume_scale_factor);
            buffer[i * 2 + 1] = static_cast<s16>(buffer[i * 2 + 1] * volume_scale_factor);
        }
    }
}

} // namespace AudioCore
