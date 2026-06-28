/*
 * Copyright (C) 2026 GammaOS
 *
 * RetroAchievements support for drastic-nano, built on the official rcheevos
 * rc_client (vendored as librcheevos_nano). rc_client owns the achievement
 * runtime, the server protocol, the hardcore policy, session/ping, rich
 * presence and unlock queueing; this object wires it into drastic-nano:
 *
 *   - read_memory: exposes DraStic's emulated DS Main RAM (read directly,
 *     in-process, because libdrastic is loaded into this binary).
 *   - server_call: performs the HTTPS requests rc_client builds, off the
 *     render thread.
 *   - event handler: turns rc_client events (unlocks, challenge indicators,
 *     mastery, leaderboards, reset) into UI events the overlay can draw.
 *
 * All rc_client calls are made from a single dedicated thread so the library
 * never needs to be thread-safe. The render thread only enqueues requests and
 * drains UI events. See RETROACHIEVEMENTS.md in this directory for the full
 * design and how it maps onto the RetroAchievements ruleset.
 */

#pragma once

#include <string>
#include <vector>
#include <deque>
#include <mutex>
#include <condition_variable>
#include <thread>
#include <atomic>
#include <cstdint>
#include <sys/types.h>

#include <rc_client.h>
#include <rc_api_request.h>

namespace android {

class DrasticRunner;

// A UI-facing event produced from an rc_client event, drained by the render
// thread to draw popups / indicators. Strings are owned copies, safe to read
// on the render thread after the rc_client event object is gone.
struct RaUiEvent {
    enum Kind {
        GamePlacard,        // achievements ready: title + "X of Y unlocked"
        Unlock,             // achievement earned
        ChallengeShow,      // challenge indicator on
        ChallengeHide,      // challenge indicator off
        ProgressShow,       // measured-progress indicator on/update
        ProgressHide,       // measured-progress indicator off
        Mastery,            // game completed / mastered
        LeaderboardStarted,
        LeaderboardSubmitted,
        LeaderboardFailed,
        Login,              // login result (title = display name, subtitle = message)
        ServerError,        // server error message in subtitle
    };
    Kind kind;
    uint32_t id = 0;             // achievement / leaderboard id (for keyed indicators)
    uint32_t points = 0;         // achievement point value (for the unlock banner)
    std::string title;
    std::string subtitle;
    std::string badgeName;       // achievement / game badge token, for local cache filename
    std::string badgeUrl;        // absolute URL of the badge image
    bool ok = true;              // for Login: success/failure
};

class NanoRetroAchievements {
public:
    NanoRetroAchievements();
    ~NanoRetroAchievements();

    // Bring up RetroAchievements for a freshly loaded ROM. Verifies the
    // in-process Main RAM read, then (if enabled and credentials are present)
    // starts the dedicated rc_client thread, logs in, identifies and loads the
    // game, and begins per-frame processing. Call once, after
    // dr->isFrameReady() is true. romPath is the absolute path of the .nds.
    // No-op when persist.gammaos.drastic_nano.ra_enabled is 0.
    void onGameLoaded(DrasticRunner* dr, const std::string& romPath);

    // The render loop calls this when gameplay is paused (e.g. the in-game
    // overlay is open). While paused, the client ticks rc_client_idle instead
    // of rc_client_do_frame, as the integration guide requires.
    void setPaused(bool paused);

    // Called once per vblank by the render loop. libdrastic emulates on its own
    // thread (decoupled from us) and the render path deliberately runs free of
    // its producer, so there is no per-emulated-frame callback we can hook. The
    // render loop is vblank-locked at ~60Hz, which matches the DS frame rate, so
    // driving rc_client_do_frame off this tick samples close to once per emulated
    // frame -- far better than a wall clock, which under-sampled (~41Hz) and
    // missed single-frame "edge" achievement triggers (e.g. level-complete).
    void onRenderFrame() { mRenderTick.fetch_add(1, std::memory_order_relaxed); }

    // Stop the client and release all resources. Safe to call more than once.
    void shutdown();

    // True when hardcore restrictions must be enforced: RetroAchievements is
    // active, the loaded game actually has achievements/leaderboards/rich
    // presence, and hardcore is enabled. The render loop gates cheats,
    // fast-forward, save-state loading and the launch auto-resume on this
    // (a hardcore session always boots fresh).
    bool hardcoreActive() const { return mHardcoreActive.load(); }

    // True (once) when hardcore has just been enabled live and the game must be
    // restarted fresh into hardcore. The render loop polls this each iteration
    // and, when set, drives the same fresh process relaunch as "Restart Game".
    // Set on the client thread when the enable is applied (so it covers both the
    // Achievements menu toggle and the ra_test_hardcore debug hook); consumed
    // here so it fires exactly once.
    bool takeHardcoreRestart() { return mHardcoreRestartPending.exchange(false); }

    // True once a game has been successfully loaded and is being processed.
    bool gameActive() const { return mGameActive.load(); }

    // Hardcore pause-spam guard. The render loop calls this before opening the
    // overlay (which pauses the game); when it returns false the overlay should
    // refuse to open. Thread-safe wrapper queued onto the client thread; returns
    // the last computed answer (false only throttles briefly in hardcore).
    bool canPauseNow();

    // Drain queued UI events (unlock popups, indicators, etc.). Called by the
    // render/overlay thread each frame. Returns false when the queue is empty.
    bool popUiEvent(RaUiEvent* out);

    // Current rich-presence string (best-effort snapshot, updated each tick).
    std::string richPresence();

    // ---- UI support (read/called from the overlay on the render thread) ----
    bool isLoggedIn() const { return mLoggedIn.load(); }
    bool gameLoadAttempted() const { return mGameLoadAttempted.load(); }
    // Monotonic counter bumped whenever the data the Achievements section draws
    // changes (login resolved, game loaded, achievement snapshot rebuilt). The
    // overlay watches it so a section opened mid-load refreshes once the
    // achievements actually arrive, instead of caching a stale "no achievements".
    uint32_t uiGeneration() const { return mUiGen.load(); }
    std::string userDisplayName();
    // Request a login from the in-game UI. Stores the credential, then re-runs
    // login on the client thread; on success the game is identified and loaded.
    void requestLogin(const std::string& user, const std::string& pass);

    // Hardcore toggle from the in-game UI. The user's preference (persisted to
    // persist.gammaos.drastic_nano.ra_hardcore) is applied live on the client
    // thread via rc_client_set_hardcore_enabled. hardcorePref() is the user's
    // setting (what the toggle shows); hardcoreActive() is whether restrictions
    // are actually in force (pref AND a game with achievements is loaded).
    void setHardcorePref(bool on);
    bool hardcorePref() const { return mHardcorePref.load(); }
    // One achievement for the overlay list.
    struct AchievementInfo {
        uint32_t id = 0;
        std::string title;
        std::string description;
        uint32_t points = 0;
        bool unlocked = false;
        std::string bucket;   // category label: "Unlocked", "Active", "Locked"...
        std::string badgeUrl; // unlocked (colour) badge image URL
        std::string measuredProgress;  // measured progress, e.g. "23/50" (live;
                                       // empty for non-measured or from the cache)
    };
    // One leaderboard for the bottom-screen detail panel.
    struct LeaderboardInfo {
        uint32_t id = 0;
        std::string title;
        std::string description;
        std::string value;    // current tracker value (formatted) while active
        int state = 0;        // rc_client leaderboard state
    };
    // Drain a decoded achievement badge (RGBA, w*h*4 bytes) for the unlock
    // banner. Called by the render thread; returns false when none are ready.
    bool popBadge(uint32_t* achId, std::vector<uint8_t>* rgba, int* w, int* h);
    // Snapshot of the loaded achievement set (built on the client thread when the
    // game loads). Empty until a game with achievements is loaded.
    std::vector<AchievementInfo> achievementSnapshot();
    // Snapshot of the game's leaderboards (titles, descriptions, live tracker
    // values), for the bottom-screen panel. Empty when the game has none.
    std::vector<LeaderboardInfo> leaderboardSnapshot();
    // One online leaderboard ranking row.
    struct LeaderboardEntry { uint32_t rank = 0; std::string user; std::string score; };
    // Request the online rankings for a leaderboard (call from the render thread
    // when the user taps one). The fetch runs on the client thread; poll
    // leaderboardEntriesSnapshot for the result.
    void requestLeaderboardEntries(uint32_t lbId);
    // The fetched entries plus the leaderboard id they belong to (0 if none).
    // *outLoading is true while a fetch is in flight.
    std::vector<LeaderboardEntry> leaderboardEntriesSnapshot(uint32_t* outLbId,
                                                             bool* outLoading);
    // Decode a badge PNG from the on-disk cache (render thread) for the bottom
    // panel / detail views. Returns false when the badge is not cached yet.
    bool loadCachedBadge(uint32_t achId, std::vector<uint8_t>* rgba, int* w, int* h);
    // Queue a badge image GET (served from the on-disk cache when present).
    void enqueueBadgeDownload(uint32_t achId, const std::string& url);

private:
    // ---- rc_client C callbacks (dispatched via rc_client_get_userdata) ----
    static uint32_t sReadMemory(uint32_t address, uint8_t* buffer,
                                uint32_t num_bytes, rc_client_t* client);
    static void sServerCall(const rc_api_request_t* request,
                            rc_client_server_callback_t callback,
                            void* callback_data, rc_client_t* client);
    static void sEventHandler(const rc_client_event_t* event, rc_client_t* client);
    static void sLogMessage(const char* message, const rc_client_t* client);
    static void sLoginCallback(int result, const char* error_message,
                               rc_client_t* client, void* userdata);
    static void sLoadCallback(int result, const char* error_message,
                              rc_client_t* client, void* userdata);

    // ---- instance handlers ----
    void onEvent(const rc_client_event_t* event);
    void onLoginResult(int result, const char* error_message);
    void onLoadResult(int result, const char* error_message);
    uint32_t readMemory(uint32_t address, uint8_t* buffer, uint32_t num_bytes);
    void enqueueServerCall(const rc_api_request_t* request,
                           rc_client_server_callback_t callback,
                           void* callback_data);
    // Achievement unlock banner: emit (UI event + badge download + chime),
    // queue a badge image GET, and play the synthesized chime.
    void emitUnlock(uint32_t id, const char* title, const char* description,
                    uint32_t points, const char* badgeUrl);
    // Queue every achievement's badge for download into the on-disk cache once
    // the set has loaded (called from the game-load callback). Cached badges
    // survive flaky wifi and reboots and make the unlock banner show instantly.
    void prefetchAllBadges();
    void playTwinkle();
    // Rebuild mAchList from rc_client and bump mUiGen (client thread only).
    void refreshAchievementSnapshot();
    // Rebuild mLbList (the game's leaderboards + live tracker values) from
    // rc_client (client thread only). Cheap; refreshed on a short timer so the
    // bottom-screen panel shows up-to-date values.
    void refreshLeaderboardSnapshot();
    // Online leaderboard rankings fetch (client thread).
    static void sLbEntriesCallback(int result, const char* error_message,
                                   rc_client_leaderboard_entry_list_t* list,
                                   rc_client_t* client, void* userdata);
    void onLbEntries(int result, rc_client_leaderboard_entry_list_t* list);
    // Per-game on-disk cache of the achievement snapshot so the Achievements
    // section can show the set (titles, descriptions, points, last-known unlock
    // state) on a poor or absent network instead of waiting on the live load.
    // Keyed by a hash of the ROM path. Written after every live snapshot,
    // pre-loaded at startup; live evaluation/unlocks still come from rc_client.
    std::string achSetCachePath() const;
    void writeAchSetCache();
    void loadAchSetCache();
    // Begin a login from stored credentials (client thread); retried on a timer.
    void attemptStoredLogin();
    // Begin the game identify+load (client thread); retried on a timer.
    void attemptLoadGame();

    // ---- worker threads ----
    void clientThreadMain();     // owns rc_client; do_frame / idle pacing
    void httpThreadMain();       // performs the HTTPS requests via curl

    // ---- helpers ----
    void verifyMainRamOnDevice(const std::string& romPath);
    bool loadStoredCredentials(std::string* user, std::string* token,
                               std::string* password);
    void storeToken(const std::string& user, const std::string& token);
    void pushUiEvent(const RaUiEvent& ev);
    void updateHardcoreActive();
    void buildUserAgent(char* out, size_t outSize);

    DrasticRunner* mRunner = nullptr;
    std::string mRomPath;
    rc_client_t* mClient = nullptr;

    // Cached DS Main RAM window for the read_memory callback. base is resolved
    // once (stable mmap for the session); mask is the 4 MB retail-DS mask.
    uint8_t* mRamBase = nullptr;
    uint32_t mRamMask = 0x3FFFFF;
    uint32_t mRamSize = 0x400000;

    // Render-loop vblank tick that drives do_frame at ~60Hz (see onRenderFrame).
    std::atomic<int> mRenderTick{0};
    int mLastRenderTick = 0;

    std::atomic<bool> mPaused{false};
    std::atomic<bool> mStop{false};
    std::atomic<bool> mHardcoreActive{false};
    std::atomic<bool> mHardcoreRestartPending{false};  // enable->restart fresh
    std::atomic<bool> mGameActive{false};
    std::atomic<bool> mPendingReset{false};
    std::atomic<bool> mLoggedIn{false};
    std::atomic<bool> mGameLoadAttempted{false};
    std::atomic<uint32_t> mUiGen{0};   // bumped when the Achievements UI data changes
    std::atomic<bool> mSnapshotDirty{false};   // unlock asked for a snapshot rebuild
    std::atomic<bool> mHardcorePref{false};    // user's hardcore setting (UI toggle)
    std::atomic<bool> mHardcoreChange{false};  // apply mHardcorePref on the client thread
    std::atomic<bool> mLoginInFlight{false};   // a login request is outstanding
    std::atomic<bool> mLoadInFlight{false};    // a game-load request is outstanding
    int mLoginFailures = 0;   // consecutive login failures (client thread only)
    int mLoadFailures = 0;    // consecutive game-load failures (client thread only)
    bool mRaDebug = false;    // sys.gammaos.drastic_nano.ra_debug: log trigger bytes
    int  mDbgLast369 = -1;    // last 0x369ac8 value for edge-logging

    // Login requested from the UI, applied on the client thread.
    std::mutex mLoginMutex;
    std::string mPendingUser, mPendingPass;
    std::atomic<bool> mPendingLogin{false};

    // User display name + achievement-list snapshot for the overlay.
    std::mutex mUiDataMutex;
    std::string mDisplayName;
    std::vector<AchievementInfo> mAchList;
    std::vector<LeaderboardInfo> mLbList;   // guarded by mUiDataMutex
    std::vector<LeaderboardEntry> mLbEntries;  // guarded by mUiDataMutex
    uint32_t mLbEntriesId = 0;              // leaderboard mLbEntries belong to
    std::atomic<uint32_t> mPendingLbFetch{0};  // render -> client: lb to fetch
    std::atomic<bool> mLbFetchInFlight{false};
    std::atomic<uint32_t> mLbFetchingId{0};    // lb currently being fetched

    // Client thread (owns rc_client) and HTTP worker thread.
    std::thread mClientThread;
    std::thread mHttpThread;
    // Diagnostic thread that verifies the Main RAM read once the cartridge has
    // finished loading (retries, since the data is not all present on frame 1).
    std::thread mProofThread;
    bool mStarted = false;

    // A pending HTTPS request handed from the client thread to the HTTP worker.
    struct HttpJob {
        std::string url;
        std::string postData;
        bool isPost = false;
        std::string contentType;
        rc_client_server_callback_t callback = nullptr;
        void* callbackData = nullptr;
        bool isBadge = false;        // badge image GET (decoded, not an rc_client call)
        uint32_t badgeAchId = 0;     // achievement id the badge belongs to
        bool prefetchOnly = false;   // cache the badge PNG to disk, do not decode/display
    };
    std::deque<HttpJob> mHttpQueue;
    std::mutex mHttpMutex;
    std::condition_variable mHttpCv;

    // PID of the curl child the HTTP worker is currently running (0 = none).
    // Guarded by mCurlMutex so shutdown() can interrupt an in-flight curl
    // (flaky wifi can otherwise hold the exit-time thread join for the full
    // curl timeout, stalling game-exit for up to a minute).
    std::atomic<pid_t> mCurlChild{0};
    std::mutex mCurlMutex;

    // A decoded achievement badge handed to the render thread for GL upload.
    struct BadgeReady {
        uint32_t achId = 0;
        int w = 0, h = 0;
        std::vector<uint8_t> rgba;
    };
    std::deque<BadgeReady> mBadgeQueue;
    std::mutex mBadgeMutex;

    // A completed HTTPS response handed back to the client thread, which is the
    // only thread allowed to re-enter rc_client via the stored callback.
    struct HttpDone {
        std::string body;
        int httpStatus = 0;
        rc_client_server_callback_t callback = nullptr;
        void* callbackData = nullptr;
    };
    std::deque<HttpDone> mDoneQueue;
    std::mutex mDoneMutex;

    // UI events for the render/overlay thread.
    std::deque<RaUiEvent> mUiQueue;
    std::mutex mUiMutex;

    // Pause-spam guard answer, recomputed on the client thread only when the
    // render thread actually requests a pause (mPauseQueryWanted), because
    // rc_client_can_pause counts do_frame calls between successive queries.
    std::atomic<bool> mCanPause{true};
    std::atomic<bool> mPauseQueryWanted{false};

    // User-Agent string for the RA server (built once on the client thread,
    // read by the HTTP worker). Guarded by mHttpMutex.
    std::string mUserAgent;

    std::string mRichPresence;
    std::mutex mRichMutex;

    // RA state directory: /data/system/nano_ra
    std::string mStateDir;
};

} // namespace android
