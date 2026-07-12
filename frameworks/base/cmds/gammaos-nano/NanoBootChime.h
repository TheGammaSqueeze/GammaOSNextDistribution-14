// Direct-to-ALSA boot chime for the DSi boot animation on the RG DS (Rockchip RK3568 / rk817).
//
// The normal nano audio path (NanoAudioPlayer -> AAudio) cannot make a sound during the boot
// animation: on this vendor the AudioPolicyManager constructor blocks audioserver bring-up for
// ~20s (it waits on ActivityManagerService, which is gated behind the cold-boot dex2oat compile),
// so AAudioStreamBuilder_openStream() does not return until ~30s+, long after the 1.45s chime
// mark. The ALSA card, however, is registered ~6s before nano even starts. So the boot chime is
// played by writing the WAV straight to /dev/snd/pcmC0D0p via tinyalsa, bypassing AudioFlinger
// entirely, which lets it sound exactly on its mark.
//
// This is RG-DS specific: it only activates when ALSA card 0 is the rk817 codec. On any other
// device nanoBootChimeIsRkDevice() returns false and the caller keeps the existing AAudio path,
// so no other device's audio behaviour changes.

#pragma once

#include <atomic>
#include <string>

// True only on the RG DS: ALSA card 0 id == "rockchiprk817". Cached after the first probe.
bool nanoBootChimeIsRkDevice();

// True if the direct-PCM path should be attempted on THIS device: a card 0 playback node exists and
// the direct path has not already failed this session. Works on any ALSA device, not just the rk817
// (the speaker-route mixer step is best-effort). The engine marks itself unusable on the first
// pcm_open/pcm_write failure so callers cleanly fall back to the normal AAudio path.
bool nanoDirectAudioUsable();

// Play a 16-bit PCM WAV straight to hw:card,device on a detached thread and return immediately
// (never blocks the caller / render thread). Sets the rk817 speaker route as a best-effort net,
// applies `gain` (0..1, already the master*volume product) to the samples, and closes the PCM as
// soon as playback finishes so it never holds the single substream into audioserver's open window.
// `inFlight`, if non-null, is cleared when the worker finishes (double-dispatch guard). A gain of
// 0 (muted) is a no-op. Only meaningful on the rk817 device; callers gate with nanoBootChimeIsRkDevice().
void nanoDirectChimePlay(const std::string& wavPath, unsigned card, unsigned device,
                         float gain, std::atomic<bool>* inFlight);

// ---- Sequential direct-to-ALSA engine (RG DS pre-boot-complete window) --------------------------
// A single persistent detached worker owns the one rk817 PCM substream ONLY while a voice is
// active. Voices are sequential (the DSi/PS3 boot never overlaps them): a small one-shot queue plus
// one optional looping voice. `gain` is the FINAL linear scalar (per-voice master * dB-mapped system
// volume), already computed by the caller. All calls are non-blocking and render-thread safe. The
// worker self-paces on pcm_write, yields on EBUSY / write error with no retry, and enforces a hard
// wall-time cap on the loop so it can never hold the card indefinitely.
void nanoDirectPlayOneShot(const std::string& wavPath, float gain);  // queue a one-shot
void nanoDirectStartLoop  (const std::string& wavPath, float gain);  // set/replace the looping voice
void nanoDirectSetGain    (float gain);   // live-update the active loop gain (volume changed mid-boot)
void nanoDirectStopLoop   ();             // clear the loop; worker idles + closes once the queue drains
void nanoDirectHoldOpen   (bool on);      // hold card0 open (silent) while true so one-shots mix without a re-open
void nanoDirectShutdown   ();             // stop everything, close the PCM, let the worker exit
bool nanoDirectActive     ();             // true while the worker owns the PCM (handoff gate)
