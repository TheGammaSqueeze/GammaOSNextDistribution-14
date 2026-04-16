/*
 * Copyright (C) 2026 GammaOS
 *
 * gammaos_drastic_shim_launcher -- a tiny init-service binary that
 * reads ROM/APK args from persist props and execs app_process to
 * bring up the drastic-nano shim. Exists because fork+exec of
 * /system/bin/app_process from gammaos-nano's multi-threaded
 * process hits a bionic linker failure ("libnativeloader.so not
 * found"); spawning via init's own clean single-threaded fork+exec
 * sidesteps that entirely.
 *
 * Flow:
 *   1. Nano decides to hand off to the drastic-nano shim.
 *   2. Nano writes three persist props:
 *        persist.gammaos.nano.drastic_rom   -- absolute ROM path
 *        persist.gammaos.nano.drastic_apk   -- stock drastic APK path
 *        persist.gammaos.nano.drastic_shim  -- shim APK path
 *   3. Nano: setprop ctl.start gammaos_drastic_shim
 *   4. init starts this service, which:
 *        - stats the drastic datadir to get stockUid
 *        - setgid(stockGid), setuid(stockUid)
 *        - drops all capabilities
 *        - execve /system/bin/app_process with the assembled argv
 *
 * Kept under 100 LoC so the single-threaded pre-exec state is
 * obviously clean.
 */

#define LOG_TAG "GammaOSShimLauncher"

#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/capability.h>
#include <linux/capability.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <string>

#include <cutils/properties.h>
#include <utils/Log.h>

static std::string readRomPathFile() {
    // nano_drastic_rom.txt is written by NanoMenu at handoff time and
    // is the shim-specific source of truth. Keep a fallback to the
    // legacy QR path file for forwards compatibility with images where
    // the drastic file hasn't been written yet.
    int fd = open("/data/system/nano_drastic_rom.txt", O_RDONLY);
    if (fd < 0) {
        fd = open("/data/system/nano_qr_rom.txt", O_RDONLY);
    }
    if (fd < 0) return {};
    char buf[4096];
    ssize_t n = read(fd, buf, sizeof(buf) - 1);
    close(fd);
    if (n <= 0) return {};
    buf[n] = 0;
    std::string s(buf);
    while (!s.empty() && (s.back() == '\n' || s.back() == '\r' ||
                          s.back() == ' ' || s.back() == '\t')) {
        s.pop_back();
    }
    return s;
}

int main() {
    ALOGI("ShimLauncher: starting");

    char drasticApk[PROPERTY_VALUE_MAX] = {};
    char shimApk[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.drastic_apk", drasticApk, "");
    property_get("persist.gammaos.nano.drastic_shim", shimApk, "");

    // ROM path can exceed PROPERTY_VALUE_MAX (92 bytes) for
    // external-SD paths like
    // /storage/<UUID>/nds/<long name>.nds -- property_set silently
    // fails or truncates in that case, leaving a stale value from
    // a prior session (e.g. an old cache path). Prefer the file
    // written by NanoMenu (/data/system/nano_qr_rom.txt) which is
    // not length-limited. Fall back to the prop only when the file
    // is absent.
    std::string romStr = readRomPathFile();
    char romProp[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.drastic_rom", romProp, "");
    if (romStr.empty()) {
        romStr = romProp;
        ALOGI("ShimLauncher: rom path from prop (file absent)");
    } else {
        ALOGI("ShimLauncher: rom path from file");
    }
    const char* rom = romStr.c_str();

    if (!*rom || !*drasticApk || !*shimApk) {
        ALOGE("ShimLauncher: missing rom/apk rom='%s' drasticApk='%s' shimApk='%s'",
              rom, drasticApk, shimApk);
        return 1;
    }
    ALOGI("ShimLauncher: rom=%s", rom);
    ALOGI("ShimLauncher: drasticApk=%s", drasticApk);
    ALOGI("ShimLauncher: shimApk=%s", shimApk);

    struct stat st = {};
    if (stat("/data/data/com.dsemu.drastic", &st) != 0) {
        ALOGE("ShimLauncher: cannot stat stock drastic datadir: %s",
              strerror(errno));
        return 2;
    }
    ALOGI("ShimLauncher: stock drastic uid=%u gid=%u",
          st.st_uid, st.st_gid);

    // Write the libdir prop BEFORE setuid. After we drop to the
    // stock drastic UID, we lose write access to /data/property/
    // and property_set against a persist.gammaos.* key silently
    // fails, leaving the shim to load the patched (audio-disabled)
    // variant from /system/etc/drastic_nano. The destination path
    // is a fixed constant so we can commit the prop up front.
    {
        const char* stagedLibDir =
            "/data/data/com.dsemu.drastic/drastic_nano_libs_audio";
        property_set("persist.gammaos.nano.drastic_libdir", stagedLibDir);
        ALOGI("ShimLauncher: prepared libdir prop -> %s", stagedLibDir);
    }

    // Open the ROM fd BEFORE setuid while we still have root perms.
    // /storage/<UUID>/nds/*.nds is mode 0770:root:media_rw; stock
    // drastic's UID does NOT inherit the media_rw supplementary group
    // after a bare setuid (only zygote-forked processes get that
    // group), so a direct open from the post-setuid shim fails with
    // EACCES. Solution: open here as root, clear FD_CLOEXEC so the
    // fd survives exec, pass the fd number via a prop the shim reads,
    // shim uses /proc/self/fd/<N> as the ROM path for
    // DraSticJNI.startGame.
    int romFd = open(rom, O_RDONLY);
    if (romFd < 0) {
        ALOGE("ShimLauncher: open(rom=%s) failed: %s",
              rom, strerror(errno));
        return 6;
    }
    int fdFlags = fcntl(romFd, F_GETFD);
    if (fdFlags >= 0) {
        fcntl(romFd, F_SETFD, fdFlags & ~FD_CLOEXEC);
    }
    {
        char fdBuf[16];
        snprintf(fdBuf, sizeof(fdBuf), "%d", romFd);
        property_set("persist.gammaos.nano.drastic_rom_fd", fdBuf);
        ALOGI("ShimLauncher: opened rom as fd=%d (as root, pre-setuid)",
              romFd);
    }

    if (setgid(st.st_gid) != 0) {
        ALOGE("ShimLauncher: setgid(%u) failed: %s",
              st.st_gid, strerror(errno));
        return 3;
    }
    if (setuid(st.st_uid) != 0) {
        ALOGE("ShimLauncher: setuid(%u) failed: %s",
              st.st_uid, strerror(errno));
        return 4;
    }

    // Drop all capabilities post-setuid.
    {
        struct __user_cap_header_struct hdr = {};
        hdr.version = _LINUX_CAPABILITY_VERSION_3;
        hdr.pid = 0;
        struct __user_cap_data_struct data[2] = {};
        (void)capset(&hdr, data);
    }

    // Stage the .so files into a writable path inside drastic's data
    // dir. The classloader namespace refuses to load from /system/etc;
    // /data/data/com.dsemu.drastic/ is writable by us (stock drastic
    // UID) and IS inside the namespace permitted.paths via the /data
    // parent rule.
    //
    // libdrastic_cpu.so and libdrastic_nano_shim.so come from
    // /system/etc/drastic_nano/ (prebuilt_etc install location).
    //
    // libdrastic_arm64.so is sourced from the STOCK drastic APK's
    // native lib dir (unpatched), NOT from /system/etc/drastic_nano/
    // where the boot-speed QR patch at 0x1d760 disables slCreateEngine
    // and therefore kills audio. The QR/DrasticRunner path inside
    // gammaos-nano still uses the patched variant for its silent
    // preview; the shim path runs full gameplay and needs audio, so
    // it picks up the unpatched variant. Staged to a separate dir
    // (drastic_nano_libs_audio) so the two can coexist without one
    // overwriting the other.
    {
        const char* dst = "/data/data/com.dsemu.drastic/drastic_nano_libs_audio";
        mkdir(dst, 0755);

        // Derive the stock drastic APK's native lib dir by stripping
        // the trailing base.apk from drasticApk and appending
        // /lib/arm64. e.g.
        //   /data/app/~~XYZ==/com.dsemu.drastic-ABC==/base.apk
        //   -> /data/app/~~XYZ==/com.dsemu.drastic-ABC==/lib/arm64
        std::string apkLibDir(drasticApk);
        size_t slash = apkLibDir.find_last_of('/');
        if (slash != std::string::npos) {
            apkLibDir.resize(slash);
        }
        apkLibDir += "/lib/arm64";

        struct StageEntry { const char* name; const char* srcDir; };
        std::string systemSrc = "/system/etc/drastic_nano";
        StageEntry entries[] = {
            { "libdrastic_cpu.so",       systemSrc.c_str() },
            { "libdrastic_arm64.so",     apkLibDir.c_str() },
            { "libdrastic_nano_shim.so", systemSrc.c_str() },
        };
        for (const auto& e : entries) {
            char sp[512], dp[512];
            snprintf(sp, sizeof(sp), "%s/%s", e.srcDir, e.name);
            snprintf(dp, sizeof(dp), "%s/%s", dst, e.name);
            int sfd = open(sp, O_RDONLY);
            if (sfd < 0) {
                ALOGW("ShimLauncher: stage open(%s) failed: %s", sp, strerror(errno));
                continue;
            }
            int dfd = open(dp, O_WRONLY | O_CREAT | O_TRUNC, 0644);
            if (dfd < 0) {
                ALOGW("ShimLauncher: stage open(%s) failed: %s", dp, strerror(errno));
                close(sfd);
                continue;
            }
            char buf[65536];
            ssize_t got;
            while ((got = read(sfd, buf, sizeof(buf))) > 0) {
                write(dfd, buf, got);
            }
            close(sfd);
            close(dfd);
            chmod(dp, 0755);
            ALOGI("ShimLauncher: staged %s <- %s", e.name, e.srcDir);
        }
        // libdir prop was already set above while we had system
        // privileges; we cannot re-set it here after setuid without
        // hitting silent selinux denials.
        ALOGI("ShimLauncher: staged libs to %s (arm64 from apk lib dir)",
              dst);
    }

    // Ensure ANDROID_DATA / ANDROID_ROOT are set (they are in init's
    // inherited env but belt-and-braces).
    setenv("ANDROID_ROOT", "/system", 1);
    setenv("ANDROID_DATA", "/data", 1);
    setenv("ANDROID_ART_ROOT", "/apex/com.android.art", 1);
    setenv("ANDROID_I18N_ROOT", "/apex/com.android.i18n", 1);
    setenv("ANDROID_TZDATA_ROOT", "/apex/com.android.tzdata", 1);

    std::string classpath = "-Djava.class.path=";
    classpath += shimApk;
    classpath += ":";
    classpath += drasticApk;

    const char* argv[] = {
        "app_process",
        classpath.c_str(),
        "/system/bin",
        "gammaos.drastic.NanoDraSticEntry",
        rom,
        drasticApk,
        nullptr,
    };
    extern char** environ;
    ALOGI("ShimLauncher: exec app_process ...");
    execve("/system/bin/app_process",
           const_cast<char**>(argv), environ);
    ALOGE("ShimLauncher: execve failed: %s", strerror(errno));
    return 5;
}
