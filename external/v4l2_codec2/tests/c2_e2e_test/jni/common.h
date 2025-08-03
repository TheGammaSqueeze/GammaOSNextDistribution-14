// Copyright 2018 The Chromium OS Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef C2_E2E_TEST_COMMON_H_
#define C2_E2E_TEST_COMMON_H_

#include <fstream>
#include <ios>
#include <memory>
#include <string>
#include <vector>

namespace android {

// The enumeration of video codec profile. This would be better to align with
// VideoCodecProfile enum in Chromium so we could use the identical test stream
// data arguments for both ARC end-to-end and Chromium tests.
enum VideoCodecProfile {
    VIDEO_CODEC_PROFILE_UNKNOWN = -1,
    VIDEO_CODEC_PROFILE_MIN = VIDEO_CODEC_PROFILE_UNKNOWN,
    H264PROFILE_MIN = 0,
    H264PROFILE_BASELINE = H264PROFILE_MIN,
    H264PROFILE_MAIN = 1,
    H264PROFILE_EXTENDED = 2,
    H264PROFILE_HIGH = 3,
    H264PROFILE_HIGH10PROFILE = 4,
    H264PROFILE_HIGH422PROFILE = 5,
    H264PROFILE_HIGH444PREDICTIVEPROFILE = 6,
    H264PROFILE_SCALABLEBASELINE = 7,
    H264PROFILE_SCALABLEHIGH = 8,
    H264PROFILE_STEREOHIGH = 9,
    H264PROFILE_MULTIVIEWHIGH = 10,
    H264PROFILE_MAX = H264PROFILE_MULTIVIEWHIGH,
    VP8PROFILE_MIN = 11,
    VP8PROFILE_ANY = VP8PROFILE_MIN,
    VP8PROFILE_MAX = VP8PROFILE_ANY,
    VP9PROFILE_MIN = 12,
    VP9PROFILE_PROFILE0 = VP9PROFILE_MIN,
    VP9PROFILE_PROFILE1 = 13,
    VP9PROFILE_PROFILE2 = 14,
    VP9PROFILE_PROFILE3 = 15,
    VP9PROFILE_MAX = VP9PROFILE_PROFILE3,
    HEVCPROFILE_MIN = 16,
    HEVCPROFILE_MAIN = HEVCPROFILE_MIN,
    HEVCPROFILE_MAIN10 = 17,
    HEVCPROFILE_MAIN_STILL_PICTURE = 18,
    HEVCPROFILE_MAX = HEVCPROFILE_MAIN_STILL_PICTURE,
};

// The enum class of video codec type.
enum class VideoCodecType {
    UNKNOWN,
    H264,
    VP8,
    VP9,
    HEVC,
};

// Structure to store resolution.
struct Size {
    Size() : width(0), height(0) {}
    Size(int w, int h) : width(w), height(h) {}
    bool IsEmpty() const { return width <= 0 || height <= 0; }

    int width;
    int height;
};

class InputFile {
public:
    explicit InputFile(std::string file_path);
    InputFile(std::string file_path, std::ios_base::openmode openmode);

    // Check if the file is valid.
    bool IsValid() const;
    // Get the size of the file.
    size_t GetLength();
    // Set position to the beginning of the file.
    virtual void Rewind();

protected:
    std::ifstream file_;
};

// Wrapper of std::ifstream for reading binary file.
class CachedInputFileStream : public InputFile {
public:
    explicit CachedInputFileStream(std::string file_path);

    // Read the given number of bytes to the buffer. Return the number of bytes
    // read or -1 on error.
    size_t Read(char* buffer, size_t size);

    void Rewind() override;

private:
    std::vector<char> data_;
    size_t position_ = 0;
};

// Wrapper of std::ifstream for reading ASCII file.
class InputFileASCII : public InputFile {
public:
    explicit InputFileASCII(std::string file_path);

    // Read one line from the file. Return false if EOF.
    bool ReadLine(std::string* line);
};

// IVF file writer, can be used to write an encoded VP8/9 video to disk.
class IVFWriter {
public:
    IVFWriter(std::ofstream* output_file, VideoCodecType codec);

    // Write the IVF file header.
    bool WriteHeader(const Size& resolution, uint32_t frame_rate, uint32_t num_frames);
    // Append the specified frame data to the IVF file.
    bool WriteFrame(const uint8_t* data, uint32_t data_size, uint64_t timestamp);
    // Set the number of video frames in the IVF file header.
    bool SetNumFrames(uint32_t num_frames);

private:
    std::ofstream* output_file_;
    VideoCodecType codec_ = VideoCodecType::UNKNOWN;
};

class OutputFile {
public:
    bool Open(const std::string& file_path, VideoCodecType codec);
    void Close();
    bool IsOpen();

    // Write the video file header.
    bool WriteHeader(const Size& resolution, uint32_t frame_rate, uint32_t num_frames);
    // Append the specified frame data to the video file.
    bool WriteFrame(uint32_t data_size, const uint8_t* data);

private:
    std::ofstream output_file_;
    std::unique_ptr<IVFWriter> ivf_writer_;
    uint64_t frame_index_ = 0;
};

struct PerformanceTimeStats {
    PerformanceTimeStats() {}
    explicit PerformanceTimeStats(const std::vector<double>& times);
    double avg_us_ = 0.0;
    double percentile_25_us_ = 0.0;
    double percentile_50_us_ = 0.0;
    double percentile_75_us_ = 0.0;
};

// The helper class to calculate FPS.
class FPSCalculator {
public:
    // Record the time interval of output buffers. Return false if is invalid.
    // This should be called per output buffer ready callback.
    bool RecordFrameTimeDiff();

    // Calucalate FPS value.
    double CalculateFPS() const;

    // Calucalate delivery time stats.
    PerformanceTimeStats CalucalateDeliveryTimeStats() const;

private:
    static constexpr double kMovingAvgWindowUs = 1000000;
    static constexpr double kRegardedPercentile = 95;

    // Return the statistics for the moving average over a window over the
    // cumulative sum. Basically, moves a window from: [0, window] to
    // [sum - window, sum] over the cumulative sum, over ((sum - window)/average)
    // steps, and returns the average value over each window.
    // This method is used to average time-diff data over a window of a constant
    // time.
    std::vector<double> MovingAvgOverSum() const;

    std::vector<double> frame_time_diffs_us_;
    int64_t last_frame_time_us_ = 0;
};

// Helper function to get VideoCodecType from |profile|.
VideoCodecType VideoCodecProfileToType(VideoCodecProfile profile);

// Split the string |src| by the delimiter |delim|.
std::vector<std::string> SplitString(const std::string& src, char delim);

// Get monotonic timestamp for now in microseconds.
int64_t GetNowUs();

// Get Mime type name from video codec type.
const char* GetMimeType(VideoCodecType type);

}  // namespace android
#endif  // C2_E2E_TEST_COMMON_H_
