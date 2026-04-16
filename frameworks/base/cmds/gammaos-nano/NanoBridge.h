/*
 * Copyright (C) 2026 GammaOS
 *
 * NanoBridge -- IPC surface between gammaos-nano (the native early-boot
 * binary that owns DRM master) and the out-of-process drastic app shim
 * (GammaDrasticNanoShim, a separate APK hosting com.dsemu.drastic.nano).
 *
 * Why this exists
 * ---------------
 * When the user picks an NDS game from the XMB while nano is active
 * (minimal boot mode, no SurfaceFlinger, no system_server), nano forks
 * an app_process whose classpath is drastic.apk + shim.apk and whose
 * entry class is gammaos.drastic.NanoDraSticEntry. That process runs
 * the full drastic APK -- DraSticJNI native lifecycle, Java save/load,
 * radial menu, etc. -- but renders into gammaos-nano's existing AHB
 * ring via a smali-level DraSticGlView replacement that installs a
 * custom EGLWindowSurfaceFactory.
 *
 * The shim's factory needs to hand drastic's GL thread an EGL surface
 * whose backing store is an AHardwareBuffer gammaos-nano owns. Since
 * the two processes have independent address spaces, this requires IPC:
 * the shim dlopens libgammaos_nano_bridge.so (which lives in
 * /system/lib64/), the bridge connects to a UNIX socket gammaos-nano
 * listens on at /dev/socket/gammaos_nano_bridge, receives an AHB
 * dma-buf via SCM_RIGHTS, wraps it into a native ANativeWindow, and
 * hands the ANativeWindow pointer back to the Java shim as a jlong
 * for the factory to feed into eglCreateWindowSurface.
 *
 * Symbol convention
 * -----------------
 * The JNI-visible functions in this header use the
 *   Java_gammaos_nano_NanoBridge_<methodName>
 * mangling so the shim's gammaos.drastic.NanoBridge class can register
 * them automatically via System.loadLibrary("gammaos_nano_bridge").
 * Non-JNI helpers retain the nano_* prefix and C linkage for
 * direct-from-native use (e.g. from the gammaos-nano process itself
 * during XMB handoff diagnostics).
 *
 * Threading
 * ---------
 * All JNI entry points are safe to call from drastic's renderer thread
 * (the only one that matters for the factory and the per-frame menu
 * poll). They MUST NOT be called concurrently from multiple shim
 * threads; the bridge is single-connection. If we ever need
 * multi-thread access the server-side can be upgraded to a per-thread
 * socket pool.
 */

#pragma once

#include <jni.h>
#include <stdint.h>

struct AHardwareBuffer;

namespace android {

// ----- Shim-side JNI surface -----
//
// These symbols are exported from libgammaos_nano_bridge.so. The
// shim's NanoBridge Java class calls them via System.loadLibrary +
// native method declarations. Signatures match the Java side:
//
//   package gammaos.nano;
//   public class NanoBridge {
//     public static native long    getCurrentAhbNativeWindow();
//     public static native boolean menuChordRequested();
//     public static native void    notifyAppEvent(String type,
//                                                 String payload);
//     public static native void    notifyQuit();
//   }
//
// notifyAppEvent is the generic per-app event channel. `type` is a
// short event name ("rom_loaded", "save_state", "load_state", or any
// app-specific string); `payload` is opaque UTF-8 the app and nano
// agree on. The bridge is reusable across emulator shims (drastic,
// future PPSSPP-nano, Flycast-nano, etc.) -- only the shim's event
// payloads change, nothing in the JNI ABI.

extern "C" {

// Returns a jlong that can be reinterpret_cast to ANativeWindow*.
// The native window wraps the AHB ring slot nano currently owns.
// Guaranteed non-zero after a successful handshake; returns 0 on
// bridge-connect failure (shim should treat that as fatal).
//
// The ANativeWindow is owned by the bridge; the shim must NOT call
// ANativeWindow_release on it. It lives until notifyQuit() or
// process exit.
JNIEXPORT jlong JNICALL
Java_gammaos_nano_NanoBridge_getCurrentAhbNativeWindow(
        JNIEnv* env, jclass cls);

// Returns JNI_TRUE iff the user's nano-menu chord has fired since the
// last poll. Clears the latch on read. The shim's renderer thread
// polls this once per frame; when true, it stops calling
// $j.onDrawFrame until notifyMenuClosed is observed.
JNIEXPORT jboolean JNICALL
Java_gammaos_nano_NanoBridge_menuChordRequested(
        JNIEnv* env, jclass cls);

// Generic app-to-nano event channel. `type` identifies the event
// ("rom_loaded", "save_state", "load_state", or any app-specific
// string). `payload` is opaque UTF-8. Nano routes known types
// (rom_loaded -> XMB title, save/load_state -> quick-resume
// metadata) and logs unknown ones. Future emulator shims reuse
// this verbatim without needing new JNI methods.
JNIEXPORT void JNICALL
Java_gammaos_nano_NanoBridge_notifyAppEvent(
        JNIEnv* env, jclass cls, jstring type, jstring payload);

// Shim signalling "user wants to quit". Nano tears down the bridge
// AHB slot, transitions XMB back into focus, and expects the shim
// process to exit shortly after.
JNIEXPORT void JNICALL
Java_gammaos_nano_NanoBridge_notifyQuit(
        JNIEnv* env, jclass cls);

} // extern "C"

// ----- Server-side (in-process) helpers -----
//
// These run inside gammaos-nano and service the bridge socket. Not
// exposed to the shim.

namespace nano_bridge {

// Start the UNIX-socket listener. Spawns a service thread that
// accepts one connection at a time from the shim process, marshals
// requests, and replies with either inline data or SCM_RIGHTS fd
// passes. Returns false if the socket cannot be bound (duplicate
// start, permissions). Idempotent after the first success.
bool startServer();

// Stop the listener and close any active client connection. Called
// from gammaos-nano teardown (rarely; nano normally runs until
// reboot).
void stopServer();

// Nano-side setters for chord state and ROM path. Called from
// NanoMenu.cpp when the user's chord fires and when a tile launch
// commits.
void setMenuChordLatch();
void setCurrentRomPath(const char* absPath);

// Per-slot scanout resources. The shim renders a dual-DS portrait
// canvas (e.g. 640x960) containing the top DS screen stacked above
// the bottom DS screen. On dual-display hardware (RG DS) we want
// panel 0 to show the top half and panel 1 to show the bottom half.
// We therefore import each AHB as two DRM framebuffers over the same
// dma-buf, differing only by byte offset. On single-display hardware
// bottomFbId is zero and flipShimFb just uses topFbId everywhere.
struct ShimFb {
    uint32_t topFbId = 0;
    uint32_t bottomFbId = 0;
    uint32_t gemHandle = 0;
};

// DRM helpers implemented in NanoMenu.cpp (access to the file-static
// DRM globals sDrmFd / sDrmDisplays lives there). The bridge server
// calls these to import each shim-visible AHB as DRM scanout
// framebuffers at handshake time, then page-flip to the shim's
// chosen slot on each OP_QUEUE.
//
// All are best-effort: if DRM isn't up (HWC/SF still hold master)
// importAhbAsFb returns false with outFb fully zeroed, and
// flipShimFb/releaseAhbFb silently no-op. That keeps the bridge
// pipeline intact for diagnostics even when the display consumer
// can't fire.
bool importAhbAsFb(AHardwareBuffer* ahb, ShimFb* outFb);
void releaseAhbFb(const ShimFb& fb);
void flipShimFb(const ShimFb& fb);

// Ownership gate: returns true once the bridge has driven at least
// one successful shim flip. Nano's XMB / QR render loops use this to
// stop issuing their own page flips so the shim owns the panel.
bool shimOwnsDisplay();

} // namespace nano_bridge

} // namespace android
