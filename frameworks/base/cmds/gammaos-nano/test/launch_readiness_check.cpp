#include "NanoLaunchReadiness.h"

#ifdef NDEBUG
#error "This test requires assertions. Compile without -DNDEBUG."
#endif
#include <cassert>
#include <cstdio>
#include <cstring>

using nano_launch::Readiness;
using nano_launch::volumeReady;
using namespace std::chrono_literals;

// Only the volume root exists. A missing ROM must reach the launcher's file check.
static int volumeState = 0; // absent, tmpfs placeholder, mounted, non-directory
static int fakeStat(const char* path, struct stat* st) {
    *st = {};
    st->st_mode = S_IFDIR;
    if (strcmp(path, "/storage") == 0) { st->st_dev = 1; return 0; }
    if (strcmp(path, "/storage/CARD") != 0 || volumeState == 0) return -1;
    st->st_dev = volumeState == 1 ? 1 : 2;
    if (volumeState == 3) st->st_mode = S_IFREG;
    return 0;
}

template<class Predicate> static void await(Predicate done) {
    const auto deadline = std::chrono::steady_clock::now() + 3s;
    while (!done()) {
        assert(std::chrono::steady_clock::now() < deadline);
        std::this_thread::sleep_for(1ms);
    }
}

struct ProbeState {
    std::atomic<bool> entered{false}, ready{false}, returned{false};
};

int main() {
    const std::string rom = "/storage/CARD/ROMs/nds/missing.nds";
    for (const char* path : {"", "/sdcard/game.nds", "/data/media/0/game.nds",
                            "/storage/emulated/0/game.nds", "/storage/self/primary/game.nds"})
        assert(volumeReady(path, fakeStat));
    for (volumeState = 0; volumeState < 4; ++volumeState) {
        const bool mounted = volumeState == 2;
        assert(volumeReady(rom, fakeStat) == mounted);
        assert(volumeReady("/mnt/media_rw/CARD/game.nds", fakeStat) == mounted);
        assert(!volumeReady("/storage/OTHER/game.nds", fakeStat));
    }
    puts("PASS: storage aliases, absent card, placeholder, mount, missing ROM");

    Readiness gate;
    auto state = std::make_shared<ProbeState>();
    const auto uiThread = std::this_thread::get_id();
    gate.start(rom, [state, uiThread](const std::string&) {
        assert(std::this_thread::get_id() != uiThread);
        const bool ready = state->ready.load();
        state->entered = true;
        return ready;
    });
    await([&] { return state->entered.load(); });
    assert(!gate.takeReady());
    gate.start(rom, [](const std::string&) { assert(false); return true; });
    state->ready = true;
    await([&] { return gate.takeReady(); });
    assert(gate.dispatching(rom));
    assert(!gate.dispatching("/storage/CARD/other.nds"));
    assert(!gate.takeReady());
    state->ready = false;
    gate.start(rom, [state](const std::string&) { return state->ready.load(); });
    assert(!gate.takeReady() && !gate.dispatching(rom));
    state->ready = true;
    await([&] { return gate.takeReady(); });
    gate.cancel();
    assert(!gate.dispatching(rom) && !gate.takeReady());
    puts("PASS: off-thread polling, repeated request, one handoff, target identity, requeue");

    // A cancelled in-flight probe must not release a newer request for the same ROM.
    state = std::make_shared<ProbeState>();
    gate.start(rom, [state](const std::string&) {
        state->entered = true;
        await([&] { return state->ready.load(); });
        state->returned = true;
        return true;
    });
    await([&] { return state->entered.load(); });
    gate.cancel();
    auto newer = std::make_shared<ProbeState>();
    gate.start(rom, [newer](const std::string&) { return newer->ready.load(); });
    state->ready = true;
    await([&] { return state->returned.load() && state.use_count() == 1; });
    assert(!gate.takeReady());
    newer->ready = true;
    await([&] { return gate.takeReady(); });
    gate.cancel();
    puts("PASS: cancellation during I/O, stale result ignored, fresh request");

    // Destruction cannot wait for I/O or leave a worker accessing the owner.
    state = std::make_shared<ProbeState>();
    {
        Readiness temporary;
        temporary.start("", [state](const std::string&) {
            state->entered = true;
            await([&] { return state->ready.load(); });
            state->returned = true;
            return true;
        });
        await([&] { return state->entered.load(); });
    }
    state->ready = true;
    await([&] { return state->returned.load() && state.use_count() == 1; });
    gate.start("", [](const std::string&) { return true; });
    await([&] { return gate.takeReady(); });
    assert(gate.dispatching(""));
    puts("PASS: safe destruction and empty-target dispatch");
}
