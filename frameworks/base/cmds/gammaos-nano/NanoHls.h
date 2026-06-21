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

// NanoHls - in-process HTTP(S) / HLS byte-stream fetcher for the IPTV player.
//
// The platform NDK AMediaExtractor cannot open http(s) URLs from a native process
// (no Java MediaHTTPService -> setDataSource(url) returns UNSUPPORTED). NanoHls works
// around that: a worker thread fetches the stream with device curl and concatenates the
// raw transport-stream bytes into a temp file, while an AMediaDataSource (custom source)
// serves those bytes to AMediaExtractor with blocking reads (so the system MPEG2TS
// extractor + HW codecs decode the stream exactly as for a local .ts).
//
// It transparently handles:
//   - HLS master playlists (#EXT-X-STREAM-INF -> pick a variant)
//   - HLS media playlists (VOD via #EXT-X-ENDLIST, or live by re-polling for new segments)
//   - direct progressive streams (a non-playlist URL is streamed straight through)
//
// One NanoHls feeds ONE consumer (its single AMediaDataSource); the video and audio
// engines each own their own NanoHls so teardown stays self-contained and lifetime-safe
// with the player's async decoder release. AES-128 encrypted and fMP4/CMAF HLS are not
// supported (those channels fail to open and the UI falls back gracefully).

#ifndef GAMMAOS_NANO_HLS_H
#define GAMMAOS_NANO_HLS_H

#include <atomic>
#include <string>
#include <thread>

#include <media/NdkMediaDataSource.h>

namespace android {

class NanoHls {
public:
    explicit NanoHls(const std::string& url);
    ~NanoHls();   // stops the fetcher, deletes the data source, removes the temp file

    // Launch the fetcher + create the backing temp file and the AMediaDataSource. Returns
    // false only on local setup failure (temp file). Network failures surface later as the
    // source returning EOS with no bytes (so the extractor open fails cleanly).
    bool start();

    // The custom data source to hand to AMediaExtractor_setDataSourceCustom. Valid until
    // this NanoHls is destroyed; the owning extractor MUST be deleted before this object.
    AMediaDataSource* dataSource() const { return mSource; }

    // Unblock readAt immediately (returns EOS) and tell the fetcher to stop. MUST be called
    // before joining a decode worker that reads this source, or release() deadlocks: the
    // worker blocks in readAt at the live frontier while release() waits on the worker.
    void requestStop() { mStop.store(true); }

    // Internal (public for the C data-source trampolines). Do not call from app code.
    ssize_t readAt(off64_t offset, void* buffer, size_t size);
    ssize_t getSize() { return -1; }   // unknown / streaming

private:
    void fetchThread();
    // Stream a non-playlist URL straight to the temp file (direct progressive source).
    void streamDirect(const std::string& url);
    // Fetch one segment URL, appending its bytes to the temp file. Returns false on failure.
    bool fetchSegment(const std::string& url);
    void appendBytesFromCurl(const std::string& url);   // popen curl, write stdout -> temp file
    void setDone() { mDone.store(true); }

    std::string mUrl;
    std::string mTmpPath;
    int mWriteFd = -1;                  // append writer (fetcher thread)
    int mReadFd = -1;                   // pread reader (data-source callback)
    AMediaDataSource* mSource = nullptr;

    std::atomic<int64_t> mProduced{0};  // bytes written + visible to readAt
    std::atomic<bool> mDone{false};     // no more bytes (VOD end / fatal / cap reached)
    std::atomic<bool> mStop{false};     // teardown requested
    std::thread mThread;
    bool mStarted = false;
};

} // namespace android

#endif // GAMMAOS_NANO_HLS_H
