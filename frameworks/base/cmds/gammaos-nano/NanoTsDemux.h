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
// once (enumerating every audio PID + the video PID + the PCR PID), and on a single
// worker thread routes, from ONE read pointer: the video PID's PES to NanoVideo (fed
// mode -> HW MPEG-2 codec), the SELECTED audio PID's PES through liba52 (NanoAc3) into
// a NanoAudioPlayer fed ring, and (when enabled) the video user_data to a CEA-608
// caption decoder. Because A and V come off the same read pointer they stay in lockstep
// with no second extractor and no byte-position guesswork; audio-track switching is an
// O(1) PID re-route on the shared clock. It ignores the CA_descriptor and transport
// scrambling bits entirely (the payload is clear), so no descramble shim, no binder,
// and no system MPEG2TSExtractor (whose AC-3 parser crashes on these captures).
//
// Lazy + self-contained: nothing is allocated until open(); stop()/close() join the
// worker and free everything.
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
#include "NanoAacDec.h"
#include "NanoCea608.h"

class NanoVideo;   // defined at global scope (NanoVideo.h is outside any namespace)

namespace android {

class NanoAudioPlayer;
class NanoHls;     // in-process HTTP/HLS byte-stream fetcher (live source)

class NanoTsDemux {
public:
    struct AudioTrack {
        int pid = -1;
        int streamType = 0;     // 0x81 ATSC AC-3, 0x0f ISO/IEC 13818-7 AAC-ADTS (others ignored)
        std::string lang;       // ISO-639 (e.g. "eng"), empty if absent
    };
    struct Cue { double startSec = 0, endSec = 0; std::string text; };

    NanoTsDemux() = default;
    ~NanoTsDemux();

    // Parse PAT/PMT (one bounded head read) + estimate duration/bitrate from PCR
    // (head + tail). Fills audioTracks()/videoPid()/duration(). Returns false if the
    // file is not an MPEG-TS with a parseable program.
    bool open(const std::string& path);
    // Live variant: parse PAT/PMT from an HLS byte stream (NanoHls) instead of a file. The
    // HLS segments are MPEG-TS, so the same single-read-pointer demux feeds both the picture
    // and the audio - this is what locks live A/V together (vs the old two-connection path).
    // For H.264 video it also extracts SPS/PPS from the stream head (no system extractor to
    // probe the format on a live URL). Recognizes H.264+AAC and MPEG-2+AC-3. The NanoHls is
    // adopted (this object stops + deletes it in close()). Returns false if the stream is not
    // a parseable muxed MPEG-TS program (so the caller can fall back to the two-connection path).
    bool openFromHls(NanoHls* hls);
    void close();

    const std::vector<AudioTrack>& audioTracks() const { return mAudio; }
    int videoPid() const { return mVideoPid; }
    const char* videoMime() const;          // codec mime for the video stream type (e.g. "video/mpeg2")
    int videoWidth() const { return mVideoW; }    // from the SPS (H.264) / 0 if unknown (hint only)
    int videoHeight() const { return mVideoH; }
    // H.264/HEVC codec-specific data extracted from the stream head (SPS+PPS, each with a
    // 4-byte annexB start code), for configuring the fed HW decoder on a live URL where there
    // is no system extractor to probe the track format. Empty for MPEG-2 (no csd needed).
    bool videoCsd(std::vector<uint8_t>& sps, std::vector<uint8_t>& pps) const;
    bool live() const { return mLive; }
    double duration() const { return mDurationSec; }
    bool isOpen() const { return mFd >= 0 || mHls != nullptr; }

    // Start the single demux worker: feed `video` (NanoVideo in fed mode) the picture and
    // decode the audio track at `audioIndex` via liba52 into `audio` (NanoAudio in fed
    // mode at the track rate/stereo). Either sink may be null. The worker paces itself on
    // the sinks' bounded queues (blocks when full), so it never reads unboundedly ahead.
    // Idempotent stop() joins it.
    // audioPreOpened: the caller already opened the audio fed ring (the recorded-.ts path, where
    // the format is known up front). false (live) defers it - the demuxer opens the fed ring lazily
    // once the audio decoder reports its true rate/channels (AAC: only after OUTPUT_FORMAT_CHANGED).
    bool start(NanoVideo* video, NanoAudioPlayer* audio, int audioIndex, double startSec,
               bool audioPreOpened = true);
    void stop();

    // O(1) audio-track switch: re-route to a different audio PID and flush. The shared
    // read pointer means playback continues at the same position (no re-open).
    void selectAudio(int audioIndex);

    // Live start-together gate: true once the audio fed ring is open + preroll-muted + playing (or
    // immediately when the caller pre-opened it). The host must wait for this before arming the
    // shared origin (armOrigin un-mutes), otherwise on a fast-decoding stream the picture's first
    // frame arms BEFORE the lazy open, and the lazy open's preroll-mute then leaves audio muted
    // with no second arm. Set after preroll so the un-mute always follows it (race-free).
    bool liveAudioReady() const { return mLiveAudioReady.load(); }

    // Seek near targetSec (byte estimate from the PCR bitrate), repositioning the single
    // read pointer and flushing BOTH sinks so video + audio resume together (in sync) at
    // the new position.
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
    void flushVideoPes();                  // feed whatever video PES is pending
    void emitVideoPes(const uint8_t* pes, size_t len);
    void scanVideoUserData(const uint8_t* es, size_t len, double ptsSec);
    void ccReorderFlush(size_t keep);        // feed buffered cc_data to the decoder in PTS (display) order
    off64_t estimateByteForTime(double sec) const;
    // Read len bytes at byte offset `pos` from the active source (file fd or, for live, the
    // HLS byte stream). Returns bytes read, 0/-1 on EOF/teardown. The live read blocks until
    // bytes arrive at the live edge (NanoHls::readAt), so the worker paces to the stream.
    ssize_t readSrc(uint8_t* buf, size_t len, off64_t pos);
    // Shared PAT/PMT parse over a head buffer (file + live). Fills video/audio PIDs + the PCR
    // PID. Returns false if no parseable program. extractVideoCsd() additionally pulls H.264
    // SPS/PPS + dimensions out of the head for the live (no-system-extractor) fed open.
    bool parsePsi(const uint8_t* head, size_t n);
    void extractVideoCsd(const uint8_t* head, size_t n);
    // Live: open the audio fed ring lazily once the decoder reports its true rate/channels
    // (AAC: only known after OUTPUT_FORMAT_CHANGED; HE-AAC SBR doubles the ADTS base rate).
    // No-op when the sink ring is already open (the recorded-.ts path pre-opens it).
    void ensureAudioFed(int rate, int channels);

    std::string mPath;
    int mFd = -1;
    NanoHls* mHls = nullptr;         // live byte source (adopted; closed in close()); null for files
    bool mLive = false;              // live HLS (no seek, no duration, lazy audio fed ring)
    off64_t mFileSize = 0;
    int mAlign = 0;                  // byte offset of the first 0x47 sync

    // program model (from PMT)
    int mPmtPid = -1;
    int mPcrPid = -1;
    int mVideoPid = -1;
    int mVideoStreamType = 0;                // PMT stream_type of the video ES (0x02 MPEG-2, etc)
    int mVideoW = 0, mVideoH = 0;            // SPS-derived dimensions (H.264); 0 = hint/unknown
    std::vector<uint8_t> mVidSps, mVidPps;   // H.264 csd (annexB, with start codes) from the head
    int64_t mPtsBaseUs = -1;                 // first video PTS (us); subtracted so position starts at 0
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
    NanoVideo* mVideoSink = nullptr;         // picture sink (fed mode); null = audio-only
    NanoAc3 mAc3;
    NanoAacDec mAac;                         // AAC decoder (live .ts AAC PID); idle for AC-3 streams
    PidPes mAudioPes;
    std::vector<uint8_t> mAc3Buf;            // AC-3 ES accumulator across PES
    std::vector<uint8_t> mAacBuf;            // AAC (ADTS) ES accumulator across PES
    bool mAudioFedOpen = false;              // worker-only: the fed ring has been opened (eager or lazy)
    std::atomic<bool> mLiveAudioReady{false};// set after the fed ring is open+prerolled+playing (arm gate)
    int mStreamRate = 48000;

    // CEA-608
    std::atomic<bool> mCcEnable{false};
    std::atomic<int> mCcChannel{0};
    PidPes mVideoPes;                        // video PES reassembly (picture + caption probe)
    bool mVideoStartedFeed = false;          // worker-only: a sequence header has been fed (clean codec start)
    std::mutex mCueMx;
    std::vector<Cue> mCues;                  // shared snapshot (guarded by mCueMx); worker writes, UI reads
    std::atomic<bool> mCcSeen{false};
    int mCcProbe = 0;                        // worker-only: frames probed for CC presence (bounded)
    NanoCea608 mCea608;                      // worker-only: the line-21 decoder
    std::atomic<int> mCcGen{0};              // bumped by setCea608 (enable/disable/channel change)
    int mCcGenApplied = -1;                  // worker-only: last mCcGen applied to mCea608
    // cc_data rides in each coded picture in DECODE order, but line-21 captions must be
    // processed in DISPLAY order (B-frame reordering otherwise scrambles the characters).
    // Buffer each picture's cc_data with its PTS and drain the oldest in PTS order once the
    // window exceeds the max MPEG-2 reorder depth. Worker-only.
    struct CcUnit { double pts; std::vector<uint8_t> data; };
    std::vector<CcUnit> mCcReorder;          // worker-only: pending cc_data, drained in PTS order
};

} // namespace android

#endif // GAMMAOS_NANO_TS_DEMUX_H
