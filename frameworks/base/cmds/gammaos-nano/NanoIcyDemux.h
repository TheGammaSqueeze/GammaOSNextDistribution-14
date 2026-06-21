/*
 * Copyright (C) 2026 GammaOS
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

// NanoIcyDemux - in-process streaming demuxer for Internet Radio codecs the platform
// AMediaExtractor cannot open over a streaming (size==-1) custom data source.
//
// The platform extractor enumerates ZERO tracks for raw Ogg (Vorbis/FLAC/Opus), native
// FLAC and ADTS HE-AAC icecast streams fed through a custom AMediaDataSource (verified on
// the Brick: setDataSourceCustom succeeds but getTrackCount()==0; local files are fine, so
// it is specific to the streaming source). MP3 and HLS work through the extractor and are
// NOT handled here.
//
// This demuxer bypasses the extractor: it fetches the stream with NanoHls (the same curl ->
// temp-file fetcher the IPTV/radio player uses, in directHint continuous mode), parses the
// container framing itself (Ogg pages + packet reassembly, or ADTS frames), builds the codec
// specific data (CSD) the way the AOSP extractors do, configures an AMediaCodec decoder, and
// pushes the decoded PCM into a NanoAudioPlayer in "fed" mode (openFed/feedPcm). The real
// sample rate / channel count come from the codec's OUTPUT_FORMAT_CHANGED, exactly like the
// deferred-AAC path in NanoAudio. One worker thread owns the whole pipeline.

#ifndef GAMMAOS_NANO_ICY_DEMUX_H
#define GAMMAOS_NANO_ICY_DEMUX_H

#include <atomic>
#include <cstdint>
#include <deque>
#include <string>
#include <thread>
#include <vector>

namespace android {

class NanoHls;
class NanoAudioPlayer;

class NanoIcyDemux {
public:
    enum Codec { kUnknown = 0, kOgg, kFlac, kAac };   // detected container/codec

    // Sniff the first bytes of `url` (a short standalone fetch) to decide whether this stream
    // is one NanoIcyDemux handles (Ogg/native-FLAC/ADTS-AAC). Returns kUnknown for MP3 / HLS /
    // anything else (those stay on the AMediaExtractor path). Cheap: grabs ~a few KB.
    static Codec probe(const std::string& url);

    NanoIcyDemux(const std::string& url, NanoAudioPlayer* sink, Codec codec);
    ~NanoIcyDemux();

    bool start();              // launch the worker; false only on immediate setup failure
    void requestStop();        // ask the worker to stop (unblocks the fetch + decode), then join in dtor

private:
    void workerFunc();
    // Sequential reader over the NanoHls byte stream. Returns false on stop/EOS.
    bool readBytes(uint8_t* dst, size_t n);
    // Ogg: read one page (resync to "OggS", header + lacing + data) into mPageData/mLace.
    bool oggReadPage(bool& bos, bool& eos, uint32_t& serial);
    // Ogg: yield the next reassembled packet (spans pages via the lacing table). False on stop/EOS.
    bool oggNextPacket(std::vector<uint8_t>& out);
    // The decode pipelines (each owns the AMediaCodec loop; push PCM via feedPcm).
    void runOgg();
    void runAac();
    // Configure + start AMediaCodec for `mime` with up to three CSD buffers; returns the codec.
    void* openCodec(const char* mime, int sampleRate, int channels,
                    const uint8_t* csd0, size_t csd0n,
                    const uint8_t* csd1, size_t csd1n,
                    const uint8_t* csd2, size_t csd2n);
    // Feed one access unit through the codec and drain any ready PCM to the sink. Returns false
    // on a fatal codec error. Manages the deferred openFed on the first OUTPUT_FORMAT_CHANGED.
    bool codecFeed(void* codec, const uint8_t* au, size_t n, int64_t ptsUs, bool eos);
    void drainCodec(void* codec, bool toEos);
    void pcmToSink(const uint8_t* data, size_t bytes, int pcmEncoding);

    std::string mUrl;
    NanoAudioPlayer* mSink = nullptr;
    Codec mCodec = kUnknown;
    NanoHls* mHls = nullptr;
    std::thread mThread;
    std::atomic<bool> mStop{false};
    bool mStarted = false;

    int64_t mReadOff = 0;          // sequential read cursor into the NanoHls stream

    // Ogg page + packet-reassembly scratch (worker-thread only)
    std::vector<uint8_t> mPageData;
    uint8_t mLace[255] = {0};
    int mNumSeg = 0;
    std::deque<std::vector<uint8_t>> mOggPkts;   // completed packets queued from a page
    std::vector<uint8_t> mOggAccum;              // packet carried across page boundaries
    uint32_t mOggSerial = 0;                     // first logical stream serial (others ignored)
    bool mOggSerialLocked = false;

    // Codec output format (learned from OUTPUT_FORMAT_CHANGED), and whether the sink ring is up.
    int mOutRate = 0;
    int mOutChans = 0;
    int mPcmEnc = 2;               // AudioFormat.ENCODING_PCM_16BIT=2, PCM_FLOAT=4
    bool mFedOpen = false;
    int64_t mFramesFed = 0;        // coarse pts source (live stream; absolute pts unimportant)
    std::vector<int16_t> mConv;    // float->int16 scratch
    const char* mCodecLabel = "";  // badge text for the Now-Playing screen (FLAC/OGG/OPUS/AAC)
};

} // namespace android

#endif // GAMMAOS_NANO_ICY_DEMUX_H
