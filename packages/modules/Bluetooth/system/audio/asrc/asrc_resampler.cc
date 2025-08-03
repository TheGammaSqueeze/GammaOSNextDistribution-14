/*
 * Copyright 2023 The Android Open Source Project
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

#include "asrc_resampler.h"

#include <base/logging.h>
#include <base/strings/stringprintf.h>

#include <algorithm>
#include <cmath>
#include <utility>

#include "asrc_tables.h"

namespace bluetooth::audio::asrc {

class SourceAudioHalAsrc::ClockRecovery : public ClockHandler {
  const int interval_;

  std::mutex mutex_;

  unsigned num_produced_;

  enum class LinkState { RESET, WARMUP, RUNNING };

  struct {
    struct {
      LinkState state;

      uint32_t local_time;
      uint32_t decim_t0;
      int decim_dt[2];

      unsigned num_completed;
      int min_buffer_level;

    } link[2];

    int active_link_id;

    uint32_t t0;
    uint32_t local_time;
    uint32_t stream_time;

    double butter_drift;
    double butter_s[2];
  } state_;

  struct {
    uint32_t local_time;
    uint32_t stream_time;
    double drift;
  } reference_timing_;

  struct {
    double sample_rate;
    int drift_us;
  } output_stats_;

  __attribute__((no_sanitize("integer"))) void OnEvent(
      uint32_t timestamp_us, int link_id, int num_completed) override {
    auto& state = state_;
    auto& link = state.link[link_id];

    // Setup the start point of the streaming

    if (link.state == LinkState::RESET) {
      if (state.link[link_id ^ 1].state == LinkState::RESET) {
        state.t0 = timestamp_us;
        state.local_time = timestamp_us;
      }

      link.local_time = timestamp_us;
      link.decim_t0 = timestamp_us;
      link.decim_dt[1] = INT_MAX;

      link.num_completed = 0;
      link.min_buffer_level = INT_MAX;

      link.state = LinkState::WARMUP;
    }

    // Update buffering level measure

    {
      const std::lock_guard<std::mutex> lock(mutex_);

      link.num_completed += num_completed;
      link.min_buffer_level = std::min(link.min_buffer_level,
                                       int(num_produced_ - link.num_completed));
    }

    // Update timing informations, and compute the minimum deviation
    // in the interval of the decimation (1 second).

    link.local_time += num_completed * interval_;
    if (link_id == state.active_link_id) {
      state.local_time += num_completed * interval_;
      state.stream_time += num_completed * interval_;
    }

    int dt_current = int(timestamp_us - link.local_time);
    link.decim_dt[1] = std::min(link.decim_dt[1], dt_current);

    if (link.local_time - link.decim_t0 < 1000 * 1000) return;

    link.decim_t0 += 1000 * 1000;

    // The first decimation interval is used to adjust the start point.
    // The deviation between local time and stream time in this interval can be
    // ignored.

    if (link.state == LinkState::WARMUP) {
      link.decim_t0 += link.decim_dt[1];
      link.local_time += link.decim_dt[1];
      if (state.active_link_id < 0) {
        state.active_link_id = link_id;
        state.local_time = link.local_time;
        state.stream_time = link.local_time;
      }

      link.decim_dt[0] = 0;
      link.decim_dt[1] = INT_MAX;
      link.state = LinkState::RUNNING;
      return;
    }

    // Deduct the derive of the deviation, from the difference between
    // the two consecutives decimated deviations.

    int drift = link.decim_dt[1] - link.decim_dt[0];
    link.decim_dt[0] = link.decim_dt[1];
    link.decim_dt[1] = INT_MAX;

    // Sanity check, limit the instant derive to +/- 50 ms / second,
    // and the gap between the 2 links to +/- 250ms. Reset the link state
    // with out of range drift, and eventually active the other link.

    int dt_link = state.link[link_id ^ 1].state == LinkState::RUNNING
                      ? link.local_time - state.link[link_id ^ 1].local_time
                      : 0;
    bool stalled = std::abs(dt_link) > 250 * 1000;

    if (std::abs(drift) > 50 * 1000 || stalled) {
      int bad_link_id = link_id ^ stalled;
      auto& bad_link = state.link[bad_link_id];

      bool resetting = (bad_link.state != LinkState::RUNNING);
      bool switching =
          state.active_link_id == bad_link_id &&
          (state.link[bad_link_id ^ 1].state == LinkState::RUNNING);

      bad_link.state = LinkState::RESET;
      if (bad_link_id == state.active_link_id) state.active_link_id = -1;

      if (switching) state.active_link_id = bad_link_id ^ 1;

      if (resetting || switching)
        LOG(WARNING) << "Link unstable or stalled, "
                     << (switching ? "switching" : "resetting") << std::endl;
    }

    if (link_id != state.active_link_id) return;

    // Let's filter the derive, with a low-pass Butterworth filter.
    // The cut-off frequency is set to 1/60th seconds.

    const double a1 = -1.9259839697e+00, a2 = 9.2862708612e-01;
    const double b0 = 6.6077909823e-04, b1 = 1.3215581965e-03, b2 = b0;

    state.butter_drift = drift * b0 + state.butter_s[0];
    state.butter_s[0] =
        state.butter_s[1] + drift * b1 - state.butter_drift * a1;
    state.butter_s[1] = drift * b2 - state.butter_drift * a2;

    // The stream time is adjusted with the filtered drift, and the error is
    // caught up with a gain of 2^-8 (~1/250us). The error is deducted from
    // the difference between the instant stream time, and the local time
    // corrected by the decimated deviation.

    int err = state.stream_time - (state.local_time + link.decim_dt[0]);
    state.stream_time +=
        (int(ldexpf(state.butter_drift, 8)) - err + (1 << 7)) >> 8;

    // Update recovered timing information, and sample the output statistics.

    decltype(output_stats_) output_stats;
    int min_buffer_level;

    {
      const std::lock_guard<std::mutex> lock(mutex_);

      auto& ref = reference_timing_;
      ref.local_time = state.local_time - state.t0;
      ref.stream_time = state.stream_time - state.t0;
      ref.drift = state.butter_drift * 1e-6;

      output_stats = output_stats_;
    }

    LOG(INFO) << base::StringPrintf("Deviation: %6d us (%3.0f ppm)",
                                    state.stream_time - state.local_time,
                                    state.butter_drift)
              << " | "
              << base::StringPrintf("Output Fs: %5.2f Hz  drift: %2d us",
                                    output_stats.sample_rate,
                                    output_stats.drift_us)
              << " | "
              << base::StringPrintf(
                     "Buffer level: %d:%d",
                     std::min(state.link[0].min_buffer_level, 99),
                     std::min(state.link[1].min_buffer_level, 99))
              << std::endl;

    state.link[0].min_buffer_level = INT_MAX;
    state.link[1].min_buffer_level = INT_MAX;
  }

 public:
  ClockRecovery(unsigned interval_us)
      : interval_(interval_us),
        num_produced_(0),
        state_{
            .link = {{.state = LinkState::RESET}, {.state = LinkState::RESET}},
            .active_link_id = -1},
        reference_timing_{0, 0, 0} {}

  ~ClockRecovery() override {}

  __attribute__((no_sanitize("integer"))) uint32_t Convert(
      uint32_t stream_time) {
    // Compute the difference between the stream time and the sampled time
    // of the clock recovery, and adjust according to the drift.
    // Then return the sampled local time, modified by this converted gap.

    const std::lock_guard<std::mutex> lock(mutex_);
    const auto& ref = reference_timing_;

    int stream_dt = int(stream_time - ref.stream_time);
    int local_dt_us = int(round(stream_dt * (1 + ref.drift)));
    return ref.local_time + local_dt_us;
  }

  void UpdateOutputStats(unsigned out_count, double sample_rate, int drift_us) {
    // Atomically update the output statistics,
    // this should be used for logging.

    const std::lock_guard<std::mutex> lock(mutex_);

    num_produced_ += out_count;
    output_stats_ = {sample_rate, drift_us};
  }
};

class SourceAudioHalAsrc::Resampler {
  static const int KERNEL_Q = asrc::ResamplerTables::KERNEL_Q;
  static const int KERNEL_A = asrc::ResamplerTables::KERNEL_A;

  const int32_t (*h_)[2 * KERNEL_A];
  const int16_t (*d_)[2 * KERNEL_A];

  static const unsigned WSIZE = 64;

  int32_t win_[2][WSIZE];
  unsigned out_pos_, in_pos_;
  const int32_t pcm_min_, pcm_max_;

  // Apply the transfer coefficients `h`, corrected by linear interpolation,
  // given fraction position `mu` weigthed by `d` values.

  inline int32_t Filter(const int32_t* in, const int32_t* h, int16_t mu,
                        const int16_t* d);

  // Upsampling loop, the ratio is less than 1.0 in Q26 format,
  // more output samples are produced compared to input.

  template <typename T>
  __attribute__((no_sanitize("integer"))) void Upsample(
      unsigned ratio, const T* in, int in_stride, size_t in_len,
      size_t* in_count, T* out, int out_stride, size_t out_len,
      size_t* out_count) {
    int nin = in_len, nout = out_len;

    while (nin > 0 && nout > 0) {
      unsigned idx = (in_pos_ >> 26);
      unsigned phy = (in_pos_ >> 17) & 0x1ff;
      int16_t mu = (in_pos_ >> 2) & 0x7fff;

      unsigned wbuf = idx < WSIZE / 2 || idx >= WSIZE + WSIZE / 2;
      auto w = win_[wbuf] + ((idx + wbuf * WSIZE / 2) % WSIZE) - WSIZE / 2;

      *out = Filter(w, h_[phy], mu, d_[phy]);
      out += out_stride;
      nout--;
      in_pos_ += ratio;

      if (in_pos_ - (out_pos_ << 26) >= (1u << 26)) {
        win_[0][(out_pos_ + WSIZE / 2) % WSIZE] = win_[1][(out_pos_)] = *in;

        in += in_stride;
        nin--;
        out_pos_ = (out_pos_ + 1) % WSIZE;
      }
    }

    *in_count = in_len - nin;
    *out_count = out_len - nout;
  }

  // Downsample loop, the ratio is greater than 1.0 in Q26 format,
  // less output samples are produced compared to input.

  template <typename T>
  __attribute__((no_sanitize("integer"))) void Downsample(
      unsigned ratio, const T* in, int in_stride, size_t in_len,
      size_t* in_count, T* out, int out_stride, size_t out_len,
      size_t* out_count) {
    size_t nin = in_len, nout = out_len;

    while (nin > 0 && nout > 0) {
      if (in_pos_ - (out_pos_ << 26) < (1u << 26)) {
        unsigned idx = (in_pos_ >> 26);
        unsigned phy = (in_pos_ >> 17) & 0x1ff;
        int16_t mu = (in_pos_ >> 2) & 0x7fff;

        unsigned wbuf = idx < WSIZE / 2 || idx >= WSIZE + WSIZE / 2;
        auto w = win_[wbuf] + ((idx + wbuf * WSIZE / 2) % WSIZE) - WSIZE / 2;

        *out = Filter(w, h_[phy], mu, d_[phy]);
        out += out_stride;
        nout--;
        in_pos_ += ratio;
      }

      win_[0][(out_pos_ + WSIZE / 2) % WSIZE] = win_[1][(out_pos_)] = *in;

      in += in_stride;
      nin--;
      out_pos_ = (out_pos_ + 1) % WSIZE;
    }

    *in_count = in_len - nin;
    *out_count = out_len - nout;
  }

 public:
  Resampler(int bit_depth)
      : h_(asrc::resampler_tables.h),
        d_(asrc::resampler_tables.d),
        win_{{0}, {0}},
        out_pos_(0),
        in_pos_(0),
        pcm_min_(-(int32_t(1) << (bit_depth - 1))),
        pcm_max_((int32_t(1) << (bit_depth - 1)) - 1) {}

  // Resample from `in` buffer to `out` buffer, until the end of any of
  // the two buffers. `in_count` returns the number of consumed samples,
  // and `out_count` the number produced. `in_sub` returns the phase in
  // the input stream, in Q26 format.

  template <typename T>
  void Resample(unsigned ratio_q26, const T* in, int in_stride, size_t in_len,
                size_t* in_count, T* out, int out_stride, size_t out_len,
                size_t* out_count, unsigned* in_sub_q26) {
    auto fn = ratio_q26 < (1u << 26) ? &Resampler::Upsample<T>
                                     : &Resampler::Downsample<T>;

    (this->*fn)(ratio_q26, in, in_stride, in_len, in_count, out, out_stride,
                out_len, out_count);

    *in_sub_q26 = in_pos_ & ((1u << 26) - 1);
  }
};

//
// ARM AArch 64 Neon Resampler Filtering
//

#if __ARM_NEON && __ARM_ARCH_ISA_A64

#include <arm_neon.h>

static inline int32x4_t vmull_low_s16(int16x8_t a, int16x8_t b) {
  return vmull_s16(vget_low_s16(a), vget_low_s16(b));
}

static inline int64x2_t vmull_low_s32(int32x4_t a, int32x4_t b) {
  return vmull_s32(vget_low_s32(a), vget_low_s32(b));
}

static inline int64x2_t vmlal_low_s32(int64x2_t r, int32x4_t a, int32x4_t b) {
  return vmlal_s32(r, vget_low_s32(a), vget_low_s32(b));
}

inline int32_t SourceAudioHalAsrc::Resampler::Filter(const int32_t* x,
                                                     const int32_t* h,
                                                     int16_t _mu,
                                                     const int16_t* d) {
  int64x2_t sx;

  int16x8_t mu = vdupq_n_s16(_mu);

  int16x8_t d0 = vld1q_s16(d + 0);
  int32x4_t h0 = vld1q_s32(h + 0), h4 = vld1q_s32(h + 4);
  int32x4_t x0 = vld1q_s32(x + 0), x4 = vld1q_s32(x + 4);

  h0 = vaddq_s32(h0, vrshrq_n_s32(vmull_low_s16(d0, mu), 7));
  h4 = vaddq_s32(h4, vrshrq_n_s32(vmull_high_s16(d0, mu), 7));

  sx = vmull_low_s32(x0, h0);
  sx = vmlal_high_s32(sx, x0, h0);
  sx = vmlal_low_s32(sx, x4, h4);
  sx = vmlal_high_s32(sx, x4, h4);

  for (int i = 8; i < 32; i += 8) {
    int16x8_t d8 = vld1q_s16(d + i);
    int32x4_t h8 = vld1q_s32(h + i), h12 = vld1q_s32(h + i + 4);
    int32x4_t x8 = vld1q_s32(x + i), x12 = vld1q_s32(x + i + 4);

    h8 = vaddq_s32(h8, vrshrq_n_s32(vmull_low_s16(d8, mu), 7));
    h12 = vaddq_s32(h12, vrshrq_n_s32(vmull_high_s16(d8, mu), 7));

    sx = vmlal_low_s32(sx, x8, h8);
    sx = vmlal_high_s32(sx, x8, h8);
    sx = vmlal_low_s32(sx, x12, h12);
    sx = vmlal_high_s32(sx, x12, h12);
  }

  int64_t s = (vaddvq_s64(sx) + (1 << 30)) >> 31;
  return std::clamp(s, int64_t(pcm_min_), int64_t(pcm_max_));
}

//
// Generic Resampler Filtering
//

#else

inline int32_t SourceAudioHalAsrc::Resampler::Filter(const int32_t* in,
                                                     const int32_t* h,
                                                     int16_t mu,
                                                     const int16_t* d) {
  int64_t s = 0;
  for (int i = 0; i < 2 * KERNEL_A - 1; i++)
    s += int64_t(in[i]) * (h[i] + ((mu * d[i] + (1 << 6)) >> 7));

  s = (s + (1 << 30)) >> 31;
  return std::clamp(s, int64_t(pcm_min_), int64_t(pcm_max_));
}

#endif

SourceAudioHalAsrc::SourceAudioHalAsrc(
    std::shared_ptr<ClockSource> clock_source, int channels, int sample_rate,
    int bit_depth, int interval_us, int num_burst_buffers, int burst_delay_ms)
    : sample_rate_(sample_rate),
      bit_depth_(bit_depth),
      interval_us_(interval_us),
      stream_us_(0),
      drift_us_(0),
      out_counter_(0),
      clock_source_(std::move(clock_source)),
      resampler_pos_{0, 0} {
  buffers_size_ = 0;

  // Check parameters

  auto check_bounds = [](int v, int min, int max) {
    return v >= min && v <= max;
  };

  if (!check_bounds(channels, 1, 8) ||
      !check_bounds(sample_rate, 1 * 1000, 100 * 1000) ||
      !check_bounds(bit_depth, 8, 32) ||
      !check_bounds(interval_us, 1 * 1000, 100 * 1000) ||
      !check_bounds(num_burst_buffers, 0, 10) ||
      !check_bounds(burst_delay_ms, 0, 1000)) {
    LOG(ERROR) << "Bad parameters:"
               << " channels: " << channels << " sample_rate: " << sample_rate
               << " bit_depth: " << bit_depth << " interval_us: " << interval_us
               << " num_burst_buffers: " << num_burst_buffers
               << " burst_delay_ms: " << burst_delay_ms << std::endl;

    return;
  }

  // Compute filter constants

  const double drift_release_sec = 3;
  drift_z0_ = 1. - exp(-3. / (1e6 / interval_us_) / drift_release_sec);

  // Setup modules, the 32 bits resampler is choosed over the 16 bits resampler
  // when the PCM bit_depth is higher than 16 bits.

  clock_recovery_ = std::make_unique<ClockRecovery>(interval_us_);
  clock_source_->Bind(clock_recovery_.get());
  resamplers_ = std::make_unique<std::vector<Resampler>>(channels, bit_depth_);

  // Deduct from the PCM stream characteristics, the size of the pool buffers
  // It needs 3 buffers (one almost full, an entire one, and a last which can be
  // started).

  auto& buffers = buffers_;

  int num_interval_samples =
      channels * (interval_us_ * sample_rate_) / (1000 * 1000);
  buffers_size_ = num_interval_samples *
                  (bit_depth_ <= 16 ? sizeof(int16_t) : sizeof(int32_t));

  for (auto& b : buffers.pool) b.resize(buffers_size_);
  buffers.index = 0;
  buffers.offset = 0;

  // Setup the burst buffers to silence

  auto silence_buffer = &buffers_.pool[0];
  std::fill(silence_buffer->begin(), silence_buffer->end(), 0);

  burst_buffers_.resize(num_burst_buffers);
  for (auto& b : burst_buffers_) b = silence_buffer;

  burst_delay_us_ = burst_delay_ms * 1000;
}

SourceAudioHalAsrc::~SourceAudioHalAsrc() {}

template <typename T>
__attribute__((no_sanitize("integer"))) void SourceAudioHalAsrc::Resample(
    double ratio, const std::vector<uint8_t>& in,
    std::vector<const std::vector<uint8_t>*>* out, uint32_t* output_us) {
  auto& resamplers = *resamplers_;
  auto& buffers = buffers_;
  auto channels = resamplers.size();

  // Convert the resampling ration in fixed Q16,
  // then loop until the input buffer is consumed.

  auto in_size = in.size() / sizeof(T);
  auto in_length = in_size / channels;

  unsigned ratio_q26 = round(ldexp(ratio, 26));
  unsigned sub_q26;

  while (in_length > 0) {
    auto in_data = (const T*)in.data() + (in_size - in_length * channels);

    // Load from the context the current output buffer, the offset
    // and deduct the remaning size. Let's resample the interleaved
    // PCM stream, a separate reampler is used for each channel.

    auto buffer = &buffers.pool[buffers.index];
    auto out_data = (T*)buffer->data() + buffers.offset;
    auto out_size = buffer->size() / sizeof(T);
    auto out_length = (out_size - buffers.offset) / channels;

    size_t in_count, out_count;

    for (auto& r : resamplers)
      r.Resample<T>(ratio_q26, in_data++, channels, in_length, &in_count,
                    out_data++, channels, out_length, &out_count, &sub_q26);

    in_length -= in_count;
    buffers.offset += out_count * channels;

    // Update the resampler position, expressed in seconds
    // and a number of samples in a second. The `sub_q26` variable
    // returned by the resampler, adds the sub-sample information.

    resampler_pos_.samples += out_count;
    for (; resampler_pos_.samples >= sample_rate_;
         resampler_pos_.samples -= sample_rate_)
      resampler_pos_.seconds++;

    // An output buffer has been fulfilled,
    // select a new buffer in the pool, used as a ring.

    if (out_count >= out_length) {
      buffers.index = (buffers.index + 1) % buffers.pool.size();
      buffers.offset = 0;
      out->push_back(buffer);
    }
  }

  // Let's convert the resampler position, in a micro-seconds timestamp.
  // The samples count within a seconds, and sub-sample position, are
  // converted, then add the number of seconds modulo 2^32.

  int64_t output_samples_q26 = (int64_t(resampler_pos_.samples) << 26) -
                               ((int64_t(sub_q26) << 26) / ratio_q26);

  *output_us = resampler_pos_.seconds * (1000 * 1000) +
               uint32_t((output_samples_q26 * 1000 * 1000) /
                        (int64_t(sample_rate_) << 26));
}

__attribute__((no_sanitize("integer"))) std::vector<const std::vector<uint8_t>*>
SourceAudioHalAsrc::Run(const std::vector<uint8_t>& in) {
  std::vector<const std::vector<uint8_t>*> out;

  if (in.size() != buffers_size_) {
    LOG(ERROR) << "Inconsistent input buffer size: " << in.size() << " ("
               << buffers_size_ << " expected)" << std::endl;
    return out;
  }

  // The burst delay has expired, let's generate the burst.

  if (burst_buffers_.size() && stream_us_ >= burst_delay_us_) {
    for (size_t i = 0; i < burst_buffers_.size(); i++)
      out.push_back(burst_buffers_[(out_counter_ + i) % burst_buffers_.size()]);

    burst_buffers_.clear();
  }

  // Convert the stream position to a local time,
  // and catch up the drift within the next second.

  stream_us_ += interval_us_;
  uint32_t local_us = clock_recovery_->Convert(stream_us_);

  double ratio = 1e6 / (1e6 - drift_us_);

  // Let's run the resampler,
  // and update the drift according the output position returned.

  uint32_t output_us;

  if (bit_depth_ <= 16)
    Resample<int16_t>(ratio, in, &out, &output_us);
  else
    Resample<int32_t>(ratio, in, &out, &output_us);

  drift_us_ += drift_z0_ * (int(output_us - local_us) - drift_us_);

  // Delay the stream, in order to generate a burst when
  // the associated delay has expired.

  if (burst_buffers_.size()) {
    for (size_t i = 0; i < out.size(); i++)
      std::exchange<const std::vector<uint8_t>*>(
          out[i], burst_buffers_[(out_counter_ + i) % burst_buffers_.size()]);
  }

  // Return the output statistics to the clock recovery module

  out_counter_ += out.size();
  clock_recovery_->UpdateOutputStats(out.size(), ratio * sample_rate_,
                                     int(output_us - local_us));

  if (0)
    LOG(INFO) << base::StringPrintf(
                     "[%6u.%06u]  Fs: %.2f Hz  drift: %d us",
                     output_us / (1000 * 1000), output_us % (1000 * 1000),
                     ratio * sample_rate_, int(output_us - local_us))
              << std::endl;

  return out;
}

}  // namespace bluetooth::audio::asrc
