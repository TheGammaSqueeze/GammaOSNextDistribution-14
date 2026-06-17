#pragma once

// NanoDvbSub - self-contained DVB subtitle decoder (ETSI EN 300 743) for the
// GammaOS Nano video player.
//
// Reads a whole MPEG-TS file, runs a minimal TS demuxer (PAT -> PMT -> the DVB
// subtitle elementary stream), reassembles the PES packets on the subtitle PID,
// decodes the DVB subtitle segments inside them and produces a timeline of
// ready-to-blit RGBA8 regions, each with an on-screen position and a time range.
//
// Pure C++17 + the standard library only. No Android, no GL, no third-party
// headers. Compiles both in the Android tree and standalone with g++.
//
// The bitstream parsing (segment layout, the 2/4/8-bit run-length pixel decode,
// the default CLUTs and the field interlacing) is a faithful translation of
// VLC's modules/codec/dvbsub.c. The CLUT YCbCr->RGB conversion uses BT.601.

#include <atomic>
#include <cstdint>
#include <string>
#include <vector>

class NanoDvbSub {
public:
    // One decoded subtitle region: an RGBA8 bitmap (straight alpha, row-major,
    // top to bottom) of size w*h placed at absolute screen position (x, y) on a
    // canvas of displayWidth() x displayHeight(), visible for [startSec, endSec).
    struct Region {
        double startSec = 0, endSec = 0;
        int x = 0, y = 0, w = 0, h = 0;
        std::vector<uint8_t> rgba;
    };

    NanoDvbSub() = default;
    ~NanoDvbSub();

    NanoDvbSub(const NanoDvbSub&) = delete;
    NanoDvbSub& operator=(const NanoDvbSub&) = delete;

    // Cheap detection: does this file carry a DVB subtitle elementary stream?
    // Parses only PAT/PMT from a bounded prefix (no subtitle decode, no whole-file
    // load), filling outPid with the subtitle PID. Use for track listing so the
    // full timeline is only decoded when the user actually selects the track.
    static bool probe(const std::string& path, int& outPid);

    // Decode the whole file. If pid >= 0 it is used as the subtitle PID directly
    // (auto-detect is skipped); otherwise the PAT/PMT are parsed to find the DVB
    // subtitle stream. Returns false if no DVB subtitle track could be decoded.
    // If abort is non-null it is polled during the (potentially long) streaming
    // read of a large .ts; once set, the decode bails out early and returns false.
    bool open(const std::string& path, int pid = -1, const std::atomic<bool>* abort = nullptr);

    // Release every buffer. After this the object holds no allocated memory.
    void close();

    bool isOpen() const { return mOpen; }

    int displayWidth() const { return mDisplayWidth; }
    int displayHeight() const { return mDisplayHeight; }

    // The decoded regions, sorted by startSec.
    const std::vector<Region>& regions() const { return mRegions; }

private:
    bool mOpen = false;
    int mDisplayWidth = 720;
    int mDisplayHeight = 576;
    std::vector<Region> mRegions;
};
