/*
 * Copyright (C) 2026 GammaOS
 *
 * See NanoPmSync.h. One worker thread ("dn-pmsync") serializes every PowerManager request of a DRM game
 * session. The render thread only publishes the latest wanted state (awake or asleep, with the time drastic
 * decided it) and never waits for PowerManager.
 */

#define LOG_TAG "DrasticNano.Pm"

#include "NanoPmSync.h"

#include <pthread.h>
#include <sched.h>
#include <stdlib.h>
#include <string.h>
#include <sys/resource.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <mutex>
#include <thread>

#include <android/os/IPowerManager.h>
#include <binder/IInterface.h>
#include <binder/IServiceManager.h>
#include <cutils/properties.h>
#include <utils/Log.h>
#include <utils/String16.h>
#include <utils/SystemClock.h>

namespace android {
namespace drastic_pm {

namespace {

// Values of android.os.PowerManager (frameworks/base/core/java/android/os/PowerManager.java).
constexpr int32_t kGoToSleepReasonLidSwitch   = 3;   // GO_TO_SLEEP_REASON_LID_SWITCH
constexpr int32_t kGoToSleepReasonPowerButton = 4;   // GO_TO_SLEEP_REASON_POWER_BUTTON
constexpr int32_t kGoToSleepFlagNoDoze        = 1;   // GO_TO_SLEEP_FLAG_NO_DOZE
constexpr int32_t kWakeReasonPowerButton      = 1;   // WAKE_REASON_POWER_BUTTON
constexpr int32_t kWakeReasonLid              = 9;   // WAKE_REASON_LID

constexpr const char* kLidOwnerProp = "sys.gammaos.drastic_nano.lid_owner";
constexpr const char* kAwakeProp    = "sys.gammaos.drastic_nano.awake";

// Every wait of the worker is a condition_variable wait_for (steady clock = CLOCK_MONOTONIC) and holds no
// wakelock: a device that is about to suspend is never kept up by this thread.
constexpr int kTickMs        = 500;    // how often the worker looks at sys.screen.state
constexpr int kRetryAfterMs  = 1000;   // a request that did not take is repeated after this long
constexpr int kSettleMs      = 1500;   // after a call: how long to watch for the state to follow
constexpr int kSettlePollMs  = 20;
constexpr int kStopWaitMs    = 1500;   // stop(): how long to wait for the last wake request

struct State {
    std::mutex mu;
    std::condition_variable cv;       // wakes the worker
    std::condition_variable cvDone;   // the worker finished a pass / exited
    bool wantAwake = true;
    int64_t evMs = 0;                 // uptimeMillis() of the decision
    Cause cause = Cause::Other;
    uint32_t gen = 0;                 // bumped with every new request
    uint32_t doneGen = 0;             // the last request the worker has dealt with
    bool stop = false;
    bool exited = false;
    bool started = false;
    std::thread th;
    sp<os::IPowerManager> pm;         // worker thread only
};

// Never destroyed: a worker stuck in a binder call may outlive stop() and the static destructors.
State& S() {
    static State* s = new State;
    return *s;
}

std::atomic<bool> gOwning{false};

bool screenOn() {
    char v[PROPERTY_VALUE_MAX] = {};
    property_get("sys.screen.state", v, "");
    return strcmp(v, "on") == 0;
}

// persist.gammaos.drastic_nano.own_lid, default 1 on the RG DS Plus and 0 elsewhere. Only in nano mode:
// PhoneWindowManager hands the lid over only there (it reads sys.gammaos.minimal_boot as well).
bool ownLidWanted() {
    char v[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.drastic_nano.own_lid", v, "");
    bool own;
    if (v[0]) {
        own = property_get_bool("persist.gammaos.drastic_nano.own_lid", false);
    } else {
        char dev[PROPERTY_VALUE_MAX] = {};
        property_get("ro.gammaos.device", dev, "");
        own = strcmp(dev, "anbernicrgdsplus") == 0;
    }
    return own && property_get_bool("sys.gammaos.minimal_boot", false);
}

// The old key-injection triggers of gammaos-nano.rc: the fallback when the binder call fails.
void legacyTrigger(bool wantAwake) {
    if (wantAwake) {
        property_set("sys.gammaos.nano.dosleep", "0");
        property_set("sys.gammaos.nano.dowake", "1");
    } else {
        property_set("sys.gammaos.nano.dosleep", "1");
    }
}

// One request to PowerManager. False when the service or the call is not there.
bool callPm(State& s, bool wantAwake, int64_t timeMs, Cause cause) {
    if (s.pm == nullptr) {
        sp<IBinder> b = defaultServiceManager()->checkService(String16("power"));
        if (b == nullptr) return false;
        s.pm = interface_cast<os::IPowerManager>(b);
        if (s.pm == nullptr) return false;
    }
    binder::Status st;
    if (wantAwake) {
        st = s.pm->wakeUp(timeMs, cause == Cause::Lid ? kWakeReasonLid : kWakeReasonPowerButton,
                          String16("drastic-nano"), String16("android"));
    } else {
        st = s.pm->goToSleep(timeMs,
                             cause == Cause::Lid ? kGoToSleepReasonLidSwitch : kGoToSleepReasonPowerButton,
                             kGoToSleepFlagNoDoze);
    }
    if (!st.isOk()) {
        ALOGW("drastic-nano pm: %s failed: %s", wantAwake ? "wakeUp" : "goToSleep",
              st.toString8().c_str());
        s.pm = nullptr;
        return false;
    }
    return true;
}

void reportDone(State& s, uint32_t gen) {
    std::lock_guard<std::mutex> lk(s.mu);
    s.doneGen = gen;
    s.cvDone.notify_all();
}

void workerMain() {
    pthread_setname_np(pthread_self(), "dn-pmsync");
    {
        // The render thread's SCHED_FIFO 80 is inherited: this thread must never compete with the game.
        sched_param sp = {};
        sched_setscheduler(0, SCHED_OTHER, &sp);
        setpriority(PRIO_PROCESS, 0, 0);
    }
    State& s = S();
    uint32_t lastGen = 0;
    int attempt = 0;
    int64_t nextTryMs = 0, mismatchSince = 0;
    // The time of the last goToSleep this worker sent (a retry carries the current time, newer than the
    // decision that asked for it).
    int64_t lastSleepSentMs = 0;
    for (;;) {
        bool want;
        int64_t ev;
        Cause cause;
        uint32_t gen;
        {
            std::unique_lock<std::mutex> lk(s.mu);
            s.cv.wait_for(lk, std::chrono::milliseconds(kTickMs),
                          [&] { return s.stop || s.gen != lastGen; });
            if (s.stop) break;
            want = s.wantAwake;
            ev = s.evMs;
            cause = s.cause;
            gen = s.gen;
        }
        if (gen != lastGen) {
            lastGen = gen;
            attempt = 0;
            nextTryMs = 0;
            mismatchSince = 0;
        }
        const bool on = screenOn();
        if (on == want) {
            // PowerManager is already where drastic wants it (a start, or a request that took).
            mismatchSince = 0;
            if (attempt == 0) attempt = 1;
            reportDone(s, gen);
            continue;
        }
        const int64_t now = android::uptimeMillis();
        if (!mismatchSince) mismatchSince = now;
        // First request at once; after that only when the mismatch has lasted a second and the back-off is
        // over. The same watch re-sleeps a PowerManager that something else woke (a USB plug) and re-wakes
        // one that went to sleep under an awake game.
        const bool act = attempt == 0 || (now - mismatchSince >= kRetryAfterMs && now >= nextTryMs);
        if (!act) continue;
        // A newer request was published after the snapshot: this one is stale, send nothing for it (a retried
        // sleep would carry `now`, newer than the decision of a wake published just before).
        {
            std::lock_guard<std::mutex> lk(s.mu);
            if (s.gen != lastGen) continue;
        }
        // PowerManager rejects an event time in the future, drops a sleep older than the last wake and a wake
        // older than the last sleep: the first request carries drastic's decision time, a repeat the current
        // time. A wake never carries a time older than the last sleep this worker sent: the render thread
        // can take its decision time before a retried sleep reads the clock and publish after the check
        // above, and "latest wins" must hold there too.
        int64_t t = attempt == 0 ? std::min(ev, now) : now;
        if (want) t = std::min(std::max(t, lastSleepSentMs), now);
        const bool ok = callPm(s, want, t, cause);
        if (!want) lastSleepSentMs = t;
        if (!ok) legacyTrigger(want);
        ALOGI("drastic-nano pm: want %s, screen %s, attempt %d via %s (%lld ms after the decision)",
              want ? "awake" : "sleep", on ? "on" : "off", attempt, ok ? "binder" : "trigger",
              (long long)(now - ev));
        attempt++;
        nextTryMs = now + std::min(kRetryAfterMs << std::min(attempt - 1, 3), 8000);
        // Watch the state follow; a new request ends the wait.
        for (int waited = 0; waited < kSettleMs; waited += kSettlePollMs) {
            if (screenOn() == want) break;
            std::unique_lock<std::mutex> lk(s.mu);
            if (s.stop || s.gen != lastGen) break;
            s.cv.wait_for(lk, std::chrono::milliseconds(kSettlePollMs),
                          [&] { return s.stop || s.gen != lastGen; });
        }
        reportDone(s, gen);
    }
    std::lock_guard<std::mutex> lk(s.mu);
    s.exited = true;
    s.cvDone.notify_all();
}

// Publish the latest wish. Returns the generation it got.
uint32_t publish(State& s, bool wantAwake, int64_t ev, Cause cause) {
    std::lock_guard<std::mutex> lk(s.mu);
    s.wantAwake = wantAwake;
    s.evMs = ev;
    s.cause = cause;
    const uint32_t g = ++s.gen;
    s.cv.notify_all();
    return g;
}

}  // namespace

bool start() {
    State& s = S();
    {
        std::lock_guard<std::mutex> lk(s.mu);
        if (s.started) return gOwning.load();
    }
    const bool own = ownLidWanted();
    if (!own) {
        ALOGI("drastic-nano pm: lid left to the framework");
        std::lock_guard<std::mutex> lk(s.mu);
        s.started = true;
        return false;
    }
    // Awake first, then the owner flag the framework reads: when PhoneWindowManager sees lid_owner=1 the
    // rc rules already see awake=1.
    property_set(kAwakeProp, "1");
    property_set(kLidOwnerProp, "1");
    {
        std::lock_guard<std::mutex> lk(s.mu);
        s.started = true;
        s.stop = false;
        s.exited = false;
        s.wantAwake = true;
        s.evMs = android::uptimeMillis();
        s.cause = Cause::Other;
        ++s.gen;
        s.th = std::thread(workerMain);
    }
    gOwning.store(true);
    ALOGI("drastic-nano pm: owning the lid for this session");
    return true;
}

bool owning() {
    return gOwning.load();
}

void wantSleep(int64_t decisionUptimeMs, Cause cause) {
    State& s = S();
    if (!gOwning.load()) {
        // Not our lid: the old key trigger (sys.boot_completed is always 1 in a session, the guard is
        // robustness only).
        if (property_get_bool("sys.boot_completed", false)) property_set("sys.gammaos.nano.dosleep", "1");
        return;
    }
    // The rc rules that stop the vendor suspend loop read awake: it has to be 0 before PowerManager sleeps.
    property_set(kAwakeProp, "0");
    publish(s, false, decisionUptimeMs, cause);
}

void wantAwake(int64_t decisionUptimeMs, Cause cause) {
    State& s = S();
    if (!gOwning.load()) {
        // PowerManager never saw the waking press (read off evdev), so wake it as the home does.
        if (property_get_bool("sys.boot_completed", false)) legacyTrigger(true);
        return;
    }
    property_set(kAwakeProp, "1");
    publish(s, true, decisionUptimeMs, cause);
}

void stop() {
    State& s = S();
    if (!gOwning.load()) return;
    // Hand PowerManager back awake: a session that ends while it is not on would leave the home on a dark
    // screen with nobody to wake it.
    bool sawWakeLast;
    uint32_t g = 0;
    {
        std::lock_guard<std::mutex> lk(s.mu);
        sawWakeLast = s.wantAwake;
    }
    if (sawWakeLast && !screenOn()) g = publish(s, true, android::uptimeMillis(), Cause::Other);
    if (g != 0) {
        std::unique_lock<std::mutex> lk(s.mu);
        s.cvDone.wait_for(lk, std::chrono::milliseconds(kStopWaitMs), [&] { return s.doneGen == g; });
    }
    gOwning.store(false);
    bool joinable = false;
    {
        std::unique_lock<std::mutex> lk(s.mu);
        s.stop = true;
        s.cv.notify_all();
        // A worker stuck in a binder call must not hold the exit: wait a bounded time, else leave it.
        s.cvDone.wait_for(lk, std::chrono::milliseconds(kStopWaitMs), [&] { return s.exited; });
        joinable = s.exited;
    }
    if (s.th.joinable()) {
        if (joinable) {
            s.th.join();
            std::lock_guard<std::mutex> lk(s.mu);
            s.started = false;   // a later start() may run again; not after a worker was left behind
        } else {
            s.th.detach();
        }
    }
    property_set(kLidOwnerProp, "0");
    property_set(kAwakeProp, "0");
    ALOGI("drastic-nano pm: lid handed back");
}

void crashClear() {
    // Async-signal-safe: property_set only.
    property_set(kLidOwnerProp, "0");
    property_set(kAwakeProp, "0");
}

}  // namespace drastic_pm
}  // namespace android
