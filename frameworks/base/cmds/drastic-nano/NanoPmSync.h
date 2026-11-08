/*
 * Copyright (C) 2026 GammaOS
 *
 * drastic-nano: how PowerManager learns about the device sleep and wake of a DRM game session.
 *
 * While a DRM session runs on a device where drastic-nano owns the lid (persist.gammaos.drastic_nano.own_lid,
 * on by default on the RG DS Plus), PhoneWindowManager leaves the lid and the short power press to it
 * (sys.gammaos.drastic_nano.lid_owner=1). drastic then puts PowerManager to sleep and wakes it itself,
 * through ONE serialized worker with "latest wins" semantics: it calls IPowerManager::goToSleep / wakeUp with
 * the time drastic decided, and keeps asking until sys.screen.state (written by PowerManagerService) matches
 * the latest wanted state. A late goToSleep(t1) that reaches PowerManager after a wake at t2 > t1 is dropped by
 * PowerManager itself, which is what the old key injection (event time = injection time) could not do.
 *
 * Where drastic does not own the lid, wantSleep / wantAwake keep the old sys.gammaos.nano.dosleep / dowake
 * init triggers (the home uses the same ones).
 */
#pragma once

#include <stdint.h>

namespace android {
namespace drastic_pm {

// Why the device sleeps or wakes: picks the PowerManager reason (lid switch or power button).
enum class Cause { Power, Lid, Other };

// DRM session start (render thread). Owning = persist.gammaos.drastic_nano.own_lid (default 1 on
// anbernicrgdsplus, 0 elsewhere) in nano mode. Owning: sets lid_owner=1 and awake=1 and starts the worker.
// Idempotent. Returns whether this session owns the lid.
bool start();
// Whether this session owns the lid (false before start() and after stop()). Any thread.
bool owning();
// Full sleep decided, called after the panels are off and audio is stopped. Owning: sets awake=0
// synchronously, then hands the request to the worker and returns. Not owning: sys.gammaos.nano.dosleep=1
// when sys.boot_completed, as before. decisionUptimeMs is android::uptimeMillis() at the decision.
void wantSleep(int64_t decisionUptimeMs, Cause cause);
// Wake decided. Owning: sets awake=1 synchronously, then hands the request to the worker. Not owning:
// dosleep=0 + dowake=1, as before.
void wantAwake(int64_t decisionUptimeMs, Cause cause);
// Session end (render thread): when the last want was awake and sys.screen.state is not "on", one wakeUp
// on the worker (bounded wait); then the worker is stopped and joined (a worker stuck in a binder call is
// left behind after a bounded wait); lid_owner=0, awake=0. Idempotent.
void stop();
// Async-signal-safe (property_set only): lid_owner=0, awake=0. For the crash handlers.
void crashClear();

}  // namespace drastic_pm
}  // namespace android
