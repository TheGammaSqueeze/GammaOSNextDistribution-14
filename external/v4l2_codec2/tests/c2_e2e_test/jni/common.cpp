// Copyright 2018 The Chromium OS Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// #define LOG_NDEBUG 0
#define LOG_TAG "Common"

#include "common.h"

#include <strings.h>
#include <time.h>

#include <algorithm>
#include <cmath>
#include <numeric>
#include <sstream>

#include <log/log.h>

namespace android {

InputFile::InputFile(std::string file_path) {
    file_ = std::ifstream(file_path);
}

InputFile::InputFile(std::string file_path, std::ios_base::openmode openmode) {
    file_ = std::ifstream(file_path, openmode);
}

bool InputFile::IsValid() const {
    return file_.is_open();
}

size_t InputFile::GetLength() {
    int current_pos = file_.tellg();

    file_.seekg(0, file_.end);
    size_t ret = file_.tellg();

    file_.seekg(current_pos, file_.beg);
    return ret;
}

void InputFile::Rewind() {
    file_.clear();
    file_.seekg(0);
}

CachedInputFileStream::CachedInputFileStream(std::string file_path)
      : InputFile(file_path, std::ifstream::binary) {
    if (IsValid()) {
        data_.resize(GetLength());
        file_.read(data_.data(), GetLength());
    }
}

size_t CachedInputFileStream::Read(char* buffer, size_t size) {
    memcpy(buffer, data_.data() + position_, size);
    position_ += size;
    return size;
}

void CachedInputFileStream::Rewind() {
    position_ = 0;
}

InputFileASCII::InputFileASCII(std::string file_path) : InputFile(file_path) {}

bool InputFileASCII::ReadLine(std::string* line) {
    std::string read_line;
    while (std::getline(file_, read_line)) {
        if (read_line.empty())  // be careful: an empty line might be read
            continue;           //             even if none exist.
        *line = read_line;
        return true;
    }
    return false;  // no more lines
}

IVFWriter::IVFWriter(std::ofstream* output_file, VideoCodecType codec)
      : output_file_(output_file), codec_(codec) {}

bool IVFWriter::WriteHeader(const Size& resolution, uint32_t frame_rate, uint32_t num_frames) {
    constexpr uint16_t kIVFHeaderSize = 32;
    char header[kIVFHeaderSize];

    // Helper lambdas to write 16bit and 32bit data, expects the device to use little endian.
    auto write16 = [&header](int i, uint16_t data) { memcpy(&header[i], &data, sizeof(data)); };
    auto write32 = [&header](int i, uint32_t data) { memcpy(&header[i], &data, sizeof(data)); };

    const char* codec_str;
    switch (codec_) {
    case VideoCodecType::VP8:
        codec_str = "VP80";
        break;
    case VideoCodecType::VP9:
        codec_str = "VP90";
        break;
    default:
        printf("[ERR] Unknown codec: \n");
        return false;
    }

    strcpy(&header[0], "DKIF");  // Bytes 0-3 of an IVF file header always contain 'DKIF' signature.
    constexpr uint16_t kVersion = 0;
    write16(4, kVersion);
    write16(6, kIVFHeaderSize);
    strcpy(&header[8], codec_str);
    write16(12, resolution.width);
    write16(14, resolution.height);
    write32(16, frame_rate);
    write32(20, 1);
    write32(24, num_frames);
    write32(28, 0);  // Reserved.

    output_file_->write(header, kIVFHeaderSize);
    return !output_file_->bad();
}

bool IVFWriter::WriteFrame(const uint8_t* data, uint32_t data_size, uint64_t timestamp) {
    constexpr size_t kIVFFrameHeaderSize = 12;
    char frame_header[kIVFFrameHeaderSize];
    memcpy(&frame_header[0], &data_size, sizeof(data_size));
    memcpy(&frame_header[4], &timestamp, sizeof(timestamp));
    output_file_->write(frame_header, kIVFFrameHeaderSize);
    output_file_->write(reinterpret_cast<const char*>(data), data_size);
    return !output_file_->bad();
}

bool IVFWriter::SetNumFrames(uint32_t num_frames) {
    output_file_->seekp(24);
    output_file_->write(reinterpret_cast<const char*>(&num_frames), sizeof(num_frames));
    return !output_file_->bad();
}

bool OutputFile::Open(const std::string& file_path, VideoCodecType codec) {
    output_file_.open(file_path, std::ofstream::binary);
    if (!output_file_.is_open()) {
        return false;
    }

    if ((codec == VideoCodecType::VP8) || (codec == VideoCodecType::VP9)) {
        ivf_writer_ = std::make_unique<IVFWriter>(&output_file_, codec);
    }
    return true;
}

void OutputFile::Close() {
    if (ivf_writer_) {
        ivf_writer_->SetNumFrames(frame_index_);
        ivf_writer_.reset();
    }
    output_file_.close();
}

bool OutputFile::IsOpen() {
    return output_file_.is_open();
}

// Write the file header.
bool OutputFile::WriteHeader(const Size& resolution, uint32_t frame_rate, uint32_t num_frames) {
    return !ivf_writer_ || ivf_writer_->WriteHeader(resolution, frame_rate, num_frames);
}

bool OutputFile::WriteFrame(uint32_t data_size, const uint8_t* data) {
    if (ivf_writer_) {
        return (ivf_writer_->WriteFrame(data, data_size, frame_index_++));
    } else {
        output_file_.write(reinterpret_cast<const char*>(data), data_size);
        return (output_file_.fail());
    }
}

// Reference: (https://source.chromium.org/chromium/chromium/src/+/main:
//             media/gpu/video_decode_accelerator_perf_tests.cc
PerformanceTimeStats::PerformanceTimeStats(const std::vector<double>& times) {
    avg_us_ = std::accumulate(times.begin(), times.end(), 0.0) / times.size();
    std::vector<double> sorted_times = times;
    std::sort(sorted_times.begin(), sorted_times.end());
    percentile_25_us_ = sorted_times[sorted_times.size() / 4];
    percentile_50_us_ = sorted_times[sorted_times.size() / 2];
    percentile_75_us_ = sorted_times[(sorted_times.size() * 3) / 4];
}

bool FPSCalculator::RecordFrameTimeDiff() {
    int64_t now_us = GetNowUs();
    if (last_frame_time_us_ != 0) {
        int64_t frame_diff_us = now_us - last_frame_time_us_;
        if (frame_diff_us <= 0) return false;
        frame_time_diffs_us_.push_back(static_cast<double>(frame_diff_us));
    }
    last_frame_time_us_ = now_us;
    return true;
}

// Reference: (https://cs.corp.google.com/android/cts/common/device-side/util/
//             src/com/android/compatibility/common/util/MediaPerfUtils.java)
//            addPerformanceStatsToLog
double FPSCalculator::CalculateFPS() const {
    std::vector<double> moving_avgs = MovingAvgOverSum();
    std::sort(moving_avgs.begin(), moving_avgs.end());

    int index = static_cast<int>(std::round(kRegardedPercentile * (moving_avgs.size() - 1) / 100));
    ALOGD("Frame decode time stats (us): { min=%.4f, regarded=%.4f, "
          "max=%.4f}, window=%.0f",
          moving_avgs[0], moving_avgs[index], moving_avgs[moving_avgs.size() - 1],
          kMovingAvgWindowUs);

    return 1E6 / moving_avgs[index];
}

PerformanceTimeStats FPSCalculator::CalucalateDeliveryTimeStats() const {
    return PerformanceTimeStats(frame_time_diffs_us_);
}

// Reference: (https://cs.corp.google.com/android/cts/common/device-side/util/
//             src/com/android/compatibility/common/util/MediaUtils.java)
//            movingAverageOverSum
std::vector<double> FPSCalculator::MovingAvgOverSum() const {
    std::vector<double> moving_avgs;

    double sum = std::accumulate(frame_time_diffs_us_.begin(), frame_time_diffs_us_.end(), 0.0);
    int data_size = static_cast<int>(frame_time_diffs_us_.size());
    double avg = sum / data_size;
    if (kMovingAvgWindowUs >= sum) {
        moving_avgs.push_back(avg);
        return moving_avgs;
    }

    int samples = static_cast<int>(std::ceil((sum - kMovingAvgWindowUs) / avg));
    double cumulative_sum = 0;
    int num = 0;
    int bi = 0;
    int ei = 0;
    double space = kMovingAvgWindowUs;
    double foot = 0;

    int ix = 0;
    while (ix < samples) {
        while (ei < data_size && frame_time_diffs_us_[ei] <= space) {
            space -= frame_time_diffs_us_[ei];
            cumulative_sum += frame_time_diffs_us_[ei];
            num++;
            ei++;
        }

        if (num > 0) {
            moving_avgs.push_back(cumulative_sum / num);
        } else if (bi > 0 && foot > space) {
            moving_avgs.push_back(frame_time_diffs_us_[bi - 1]);
        } else if (ei == data_size) {
            break;
        } else {
            moving_avgs.push_back(frame_time_diffs_us_[ei]);
        }

        ix++;
        foot -= avg;
        space += avg;

        while (bi < ei && foot < 0) {
            foot += frame_time_diffs_us_[bi];
            cumulative_sum -= frame_time_diffs_us_[bi];
            num--;
            bi++;
        }
    }
    return moving_avgs;
}

VideoCodecType VideoCodecProfileToType(VideoCodecProfile profile) {
    if (profile >= H264PROFILE_MIN && profile <= H264PROFILE_MAX) return VideoCodecType::H264;
    if (profile >= VP8PROFILE_MIN && profile <= VP8PROFILE_MAX) return VideoCodecType::VP8;
    if (profile >= VP9PROFILE_MIN && profile <= VP9PROFILE_MAX) return VideoCodecType::VP9;
    if (profile >= HEVCPROFILE_MIN && profile <= HEVCPROFILE_MAX) return VideoCodecType::HEVC;
    return VideoCodecType::UNKNOWN;
}

std::vector<std::string> SplitString(const std::string& src, char delim) {
    std::stringstream ss(src);
    std::string item;
    std::vector<std::string> ret;
    while (std::getline(ss, item, delim)) {
        ret.push_back(item);
    }
    return ret;
}

int64_t GetNowUs() {
    struct timespec t;
    t.tv_sec = t.tv_nsec = 0;
    clock_gettime(CLOCK_MONOTONIC, &t);
    int64_t nsecs = static_cast<int64_t>(t.tv_sec) * 1000000000LL + t.tv_nsec;
    return nsecs / 1000ll;
}

const char* GetMimeType(VideoCodecType type) {
    switch (type) {
    case VideoCodecType::H264:
        return "video/avc";
    case VideoCodecType::VP8:
        return "video/x-vnd.on2.vp8";
    case VideoCodecType::VP9:
        return "video/x-vnd.on2.vp9";
    case VideoCodecType::HEVC:
        return "video/hevc";
    default:  // unknown type
        return nullptr;
    }
}

}  // namespace android
