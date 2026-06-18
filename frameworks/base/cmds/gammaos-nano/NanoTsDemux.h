// NanoTsDemux - a single-pass, in-process MPEG-TS demuxer for the .ts video path.
//
// ATSC / HDHomeRun captures carry MULTIPLE elementary streams in one program (one
// MPEG-2 video, several AC-3 audio services, CEA-608 captions inside the video
// user_data) and flag the program scrambled with a CA_descriptor even though the
// payload is clear. Android's system MPEG2TSExtractor exposes only the first audio
// track, discards the in-band captions, and (through our binder-bridged descramble
// data source) re-parses the whole container on every open/seek - slow and heavy.
//
// NanoTsDemux reads the file ONCE with its own buffered fd reader, parses PAT/PMT
// once (enumerating every audio PID + the video PID + the PCR PID), and on a worker
// thread routes the SELECTED audio PID's PES through liba52 (NanoAc3) into a
// NanoAudioPlayer fed ring. It ignores the CA_descriptor and transport scrambling
// bits entirely (the payload is clear), so no descramble shim, no binder, no system
// extractor. Audio-track switching is an O(1) PID re-route on the same PCR clock.
// Captions (CEA-608 from the video PES user_data) hook into the same single pass.
//
// Lazy + self-contained: nothing is allocated until open(); stop()/close() join the
// worker and free everything. The video picture still decodes through NanoVideo for
// now; this engine owns the .ts audio (+ captions) only.
#ifndef GAMMAOS_NANO_TS_DEMUX_H
#define GAMMAOS_NANO_TS_DEMUX_H

#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <sys/types.h>
#include <thread>
#include <vector>

#include "NanoAc3.h"

namespace android {

class NanoAudioPlayer;
class NanoCea608;

class NanoTsDemux {
public:
    struct AudioTrack {
        int pid = -1;
        int streamType = 0;     // 0x81 ATSC AC-3 (others ignored for now)
        std::string lang;       // ISO-639 (e.g. "eng"), empty if absent
    };
    struct Cue { double startSec = 0, endSec = 0; std::string text; };

    NanoTsDemux() = default;
    ~NanoTsDemux();

    // Parse PAT/PMT (one bounded head read) + estimate duration/bitrate from PCR
    // (head + tail). Fills audioTracks()/videoPid()/duration(). Returns false if the
    // file is not an MPEG-TS with a parseable program.
    bool open(const std::string& path);
    void close();

    const std::vector<AudioTrack>& audioTracks() const { return mAudio; }
    int videoPid() const { return mVideoPid; }
    double duration() const { return mDurationSec; }
    bool isOpen() const { return mFd >= 0; }

    // Start the audio worker: decode the audio track at `audioIndex` (into mAudio) via
    // liba52 and feed `sink` (which must be put into fed mode by the caller at the
    // track's rate, stereo). The worker paces itself on the sink's ring (blocks when
    // full), so it never reads unboundedly ahead. Idempotent stop() joins it.
    bool start(NanoAudioPlayer* sink, int audioIndex, double startSec);
    void stop();

    // O(1) audio-track switch: re-route to a different audio PID and flush. The PCR
    // timeline is shared, so playback continues at the same position (no re-open).
    void selectAudio(int audioIndex);

    // Seek the audio worker near targetSec (byte estimate from the PCR bitrate) and
    // flush the decoder + sink ring. The picture (NanoVideo) seeks independently.
    void seek(double targetSec);

    // CEA-608 captions: enable extraction from the video PES user_data and decode the
    // given line-21 channel (0=CC1..3=CC4). Disabled by default (zero cost when off).
    // Cues accumulate; copyCues() returns a snapshot for the renderer.
    void setCea608(bool enable, int ccChannel);
    void copyCea608Cues(std::vector<Cue>& out);
    // Whether the stream actually carries CEA-608 on the named channel (probe result).
    bool hasCea608(int ccChannel);

private:
    // ---- demux internals ----
    struct PidPes {                 // per-PID PES reassembly state
        std::vector<uint8_t> buf;   // accumulated PES bytes since the last PUSI
        bool started = false;
    };
    void workerFunc(double startSec);
    void handlePacket(const uint8_t* p);   // route one 188-byte packet
    void flushAudioPes();                  // decode whatever audio PES is pending
    void emitAudioPes(const uint8_t* pes, size_t len, int64_t ptsUs);
    void scanVideoUserData(const uint8_t* es, size_t len, int64_t ptsUs);
    off64_t estimateByteForTime(double sec) const;

    std::string mPath;
    int mFd = -1;
    off64_t mFileSize = 0;
    int mAlign = 0;                  // byte offset of the first 0x47 sync

    // program model (from PMT)
    int mPmtPid = -1;
    int mPcrPid = -1;
    int mVideoPid = -1;
    std::vector<AudioTrack> mAudio;
    double mDurationSec = 0.0;
    double mFirstPcr = -1.0;
    double mBytesPerSec = 0.0;

    // worker
    std::thread mWorker;
    std::atomic<bool> mStop{false};
    std::atomic<int> mSelPid{-1};            // currently routed audio PID
    std::atomic<int> mPendSelPid{-1};        // requested switch (-1 = none)
    std::atomic<double> mPendSeek{-1.0};     // requested seek sec (-1 = none)
    NanoAudioPlayer* mSink = nullptr;
    NanoAc3 mAc3;
    PidPes mAudioPes;
    std::vector<uint8_t> mAc3Buf;            // AC-3 ES accumulator across PES
    int mStreamRate = 48000;

    // CEA-608
    std::atomic<bool> mCcEnable{false};
    std::atomic<int> mCcChannel{0};
    PidPes mVideoPes;                        // only reassembled while CC is on
    std::mutex mCueMx;
    std::vector<Cue> mCues;
    std::atomic<bool> mCcSeen{false};
};

} // namespace android

#endif // GAMMAOS_NANO_TS_DEMUX_H
