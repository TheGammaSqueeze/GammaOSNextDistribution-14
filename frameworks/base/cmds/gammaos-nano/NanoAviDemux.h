// NanoAviDemux - a self-contained RIFF/AVI container demuxer for the PS3 XMB video
// player. Android's NDK AMediaExtractor has no AVI support on this device, so AVI
// files (typically DivX / Xvid / MPEG-4 ASP or MJPEG video with MP3 / PCM / AC-3
// audio) will not play through the normal path. The Allwinner SoC DOES have HW
// decoders for those codecs (OMX.allwinner.video.decoder.{divx,xvid,mpeg4,mjpeg,...}),
// so the only missing piece is the container parse + demux.
//
// This mirrors NanoTsDemux: parse the container ONCE, enumerate the streams, then a
// single worker reads the interleaved 'movi' chunks from one read pointer and routes
// the video elementary stream to NanoVideo (fed mode -> HW codec) and the audio to a
// decoder feeding NanoAudio - so A and V stay in lockstep with no second extractor.
//
// This header is deliberately Android-free (pure POSIX fd + byte parsing) so the
// parser can be unit-tested on the host; the NanoVideo/NanoAudio worker wiring is a
// thin layer added on top.
#ifndef GAMMAOS_NANO_AVI_DEMUX_H
#define GAMMAOS_NANO_AVI_DEMUX_H

#include <atomic>
#include <cstdint>
#include <string>
#include <thread>
#include <vector>

class NanoVideo;        // global scope (NanoVideo.h is outside any namespace), like NanoTsDemux
struct AMediaCodec;     // NDK MP3 decoder (opaque, C type)
namespace android {
class NanoAudioPlayer;  // fed-mode PCM sink for the audio track (android namespace)
class NanoAc3;          // liba52 AC-3 decoder (forward-declared so the host parser test stays Android-free)
}

class NanoAviDemux {
public:
    enum StreamKind { STREAM_NONE = 0, STREAM_VIDEO, STREAM_AUDIO };

    struct VideoInfo {
        bool present = false;
        int streamIndex = -1;        // AVI stream number (the NN in 'NNdc')
        char fourcc[5] = {0};        // biCompression / fccHandler, lowercased (e.g. "xvid")
        int width = 0, height = 0;
        double fps = 0.0;            // dwRate / dwScale
        int64_t frames = 0;          // dwLength (stream sample count)
        std::vector<uint8_t> csd;    // codec-specific data (MPEG-4 VOL) from strf extradata
    };
    struct AudioInfo {
        bool present = false;
        int streamIndex = -1;
        int formatTag = 0;           // WAVEFORMATEX wFormatTag (0x0055 MP3, 0x2000 AC3, 1 PCM, 0xFF AAC)
        int channels = 0;
        int sampleRate = 0;
        int bitsPerSample = 0;
        int blockAlign = 0;
        int avgBytesPerSec = 0;
        std::vector<uint8_t> csd;    // extradata (e.g. AAC AudioSpecificConfig), may be empty
    };

    // One movi data chunk located by the parser / index.
    struct Sample {
        StreamKind kind = STREAM_NONE;
        int streamIndex = -1;
        int64_t offset = 0;          // absolute file offset of the chunk PAYLOAD
        uint32_t size = 0;           // payload size (bytes)
        bool keyframe = false;       // from idx1 AVIIF_KEYFRAME (video)
        int64_t frameIndex = 0;      // running per-stream sample index
        double ptsSec = 0.0;         // presentation time (video: idx/fps; audio: bytes/avgBytesPerSec)
    };

    NanoAviDemux() = default;
    ~NanoAviDemux() { close(); }

    // Parse the container: RIFF/hdrl (avih + per-stream strh/strf) + locate movi + read
    // idx1 (if present, else scan movi). Returns false if not a parseable AVI. Cheap:
    // header reads + the index; the bulk 'movi' payload is read lazily during demux.
    bool open(const std::string& path);
    void close();
    bool isOpen() const { return mFd >= 0; }

    const VideoInfo& video() const { return mVideo; }
    const AudioInfo& audio() const { return mAudio; }
    double durationSec() const { return mDurationSec; }
    int sampleCount() const { return (int) mSamples.size(); }
    const Sample& sample(int i) const { return mSamples[i]; }

    // Read the payload of sample i into out (resized). Returns false on IO error.
    bool readSample(int i, std::vector<uint8_t>& out);

    // Index of the first video keyframe sample at or before targetSec (for seeking),
    // or 0 if none. Uses the sample list + fps.
    int seekSampleForTime(double targetSec) const;

    // PTS (seconds) of a sample (precomputed at index time).
    double samplePts(const Sample& s) const { return s.ptsSec; }

    bool indexFromIdx1() const { return mHadIdx1; }   // true if idx1 was used (vs scanned)

    // The AMediaCodec mime for the video stream's fourcc, or nullptr if we have no
    // decoder route for it. MPEG-4 ASP family (xvid/divx/dx50/mp4v/...) -> video/mp4v-es.
    const char* videoMime() const;
    // Codec-specific data (MPEG-4 VOL) for the HW decoder: the strf extradata if present,
    // else the VOL prefix extracted from the first video frame (before the first VOP start
    // code). Empty if none can be found. Reads from the file, so call after open().
    bool videoCsd(std::vector<uint8_t>& out);

    // True if the audio stream is one we can decode/feed (PCM / MP3 / AC-3).
    bool audioDecodable() const;
    int  audioFormatTag() const { return mAudio.formatTag; }
    // Sample rate to open the fed ring at (AC-3 always decodes to 48k; PCM/MP3 use strf).
    int  audioFedRate() const;

    // ---- playback worker (feeds NanoVideo fed mode + NanoAudio fed mode) ----
    // Start a worker that reads the interleaved movi samples in order from startSec: each
    // coded video frame goes to `video` (NanoVideo fed mode) with its PTS, and each audio
    // chunk is decoded (PCM passthrough / MP3 via AMediaCodec / AC-3 via liba52) to int16
    // stereo and pushed to `audio` (NanoAudio fed mode). Either sink may be null. Back-
    // pressured by the sinks' bounded queues. Idempotent stop() joins it.
    bool start(NanoVideo* video, android::NanoAudioPlayer* audio, double startSec);
    void stop();
    void seekWorker(double sec);   // reposition the worker to a keyframe near sec

private:
    bool parseHdrl(int64_t pos, uint32_t size);
    bool parseStrl(int64_t pos, uint32_t size, int streamIndex);
    bool parseIdx1(int64_t pos, uint32_t size);
    bool scanMovi();                  // fallback: walk movi chunks when idx1 is absent/bad
    bool readAt(int64_t off, void* buf, size_t n) const;

    int mFd = -1;
    int64_t mFileSize = 0;
    int64_t mMoviPos = 0;             // file offset of the 'movi' LIST payload (after the 'movi' fourcc)
    uint32_t mMoviSize = 0;
    bool mHadIdx1 = false;
    bool mIdxOffsetsAbsolute = false; // idx1 offsets: absolute vs relative-to-movi (auto-detected)
    int mStreamCount = 0;
    // Per-stream kind, captured while parsing strl, so movi chunk ids map to streams even
    // when an id's two-letter suffix is ambiguous.
    StreamKind mStreamKind[64] = {};

    VideoInfo mVideo;
    AudioInfo mAudio;
    double mDurationSec = 0.0;
    std::vector<Sample> mSamples;     // all demuxable chunks in file order

    // worker
    void workerFunc(double startSec);
    std::thread mWorker;
    std::atomic<bool> mStop{false};
    std::atomic<double> mPendSeek{-1.0};
    NanoVideo* mVideoSink = nullptr;

    // audio decode (worker side) - all guarded NANOAVI_TEST-free in the .cpp
    void audioOpenDecoder();                                   // lazy: MP3 codec / AC-3 state
    void audioCloseDecoder();                                  // free codec/state + clear buffers
    void audioDecodeSample(const uint8_t* data, size_t len);   // one movi audio chunk -> PCM -> sink
    void audioFeedStereo(const int16_t* pcm, size_t nSamples, int chIn);  // up-mix mono, then feed
    void feedPcmBlocking(const int16_t* pcm, size_t nSamples); // feed with back-pressure
    android::NanoAudioPlayer* mAudioSink = nullptr;
    android::NanoAc3* mAc3 = nullptr;    // AC-3 path (heap, lazy)
    std::vector<uint8_t> mAc3Buf;        // AC-3 ES accumulator across chunks
    AMediaCodec* mMp3Codec = nullptr;    // MP3 path
    int mMp3Ch = 0;                      // MP3 output channels (from OUTPUT_FORMAT_CHANGED, else strf)
};

#endif // GAMMAOS_NANO_AVI_DEMUX_H
