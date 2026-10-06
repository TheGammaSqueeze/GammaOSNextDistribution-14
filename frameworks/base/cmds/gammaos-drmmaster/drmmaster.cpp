// GammaOS: transient DRM master for the display composer.
//
// The Qualcomm display composer (SDM/SDE, libdrmutils) relies on being the
// first process to open /dev/dri/card0: the kernel makes that file DRM master
// implicitly and the composer never calls DRM_IOCTL_SET_MASTER itself. On a
// GammaOS Nano boot the nano home (and later drastic-nano) takes master for the
// DRM-direct panel and drops it again when it hands the panel to
// SurfaceFlinger. Nothing in the composer ever re-acquires master after that,
// so every drmModeAtomicCommit returns EACCES ("Permission denied") and both
// panels stay black for the rest of the session for any SurfaceFlinger app.
//
// Loaded into the composer through LD_PRELOAD (vendor init rc), this shim
// interposes ioctl() and holds DRM master only for the duration of each DRM
// ioctl: SET_MASTER, the real ioctl, DROP_MASTER, under one process-wide mutex
// so the composer's threads (which use different card0 fds) never fight each
// other for it. Consequences:
//   - after nano drops master the composer's next commit just works;
//   - while nano or drastic-nano own the panel SET_MASTER returns EBUSY, the
//     composer's commit fails with EACCES exactly as it does today, and
//     SurfaceFlinger keeps running without presenting;
//   - the composer never sits on master between frames, so a DRM home that
//     retries SET_MASTER (drmEarlySplash / drmReacquireForHome) wins within one
//     retry period instead of losing the cold-boot race for the whole session.
// Only ioctls on the DRM character device (type 'd', which covers the core,
// mode and driver command ranges) are touched; everything else passes through.
// The composer runs as user system, so the vendor rc also grants it
// CAP_SYS_ADMIN, which DRM_IOCTL_SET_MASTER requires for a file that was never
// master before.

#define LOG_TAG "gammaos-drmmaster"

#include <dirent.h>
#include <dlfcn.h>
#include <errno.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <unistd.h>

#include <log/log.h>

#ifndef DRM_IOCTL_BASE
#define DRM_IOCTL_BASE 'd'
#endif
#define GAMMA_DRM_IOCTL_SET_MASTER  _IO(DRM_IOCTL_BASE, 0x1e)
#define GAMMA_DRM_IOCTL_DROP_MASTER _IO(DRM_IOCTL_BASE, 0x1f)

typedef int (*ioctl_fn)(int, int, ...);

static ioctl_fn sReal = nullptr;
static pthread_mutex_t sLock = PTHREAD_MUTEX_INITIALIZER;
static pthread_once_t sOnce = PTHREAD_ONCE_INIT;
static int sLoggedSetFail = 0;
static int sLoggedTaken = 0;

static void resolveReal() {
    sReal = (ioctl_fn)dlsym(RTLD_NEXT, "ioctl");
    if (!sReal) {
        ALOGE("cannot resolve the real ioctl: %s", dlerror());
    }
}

// The mode-setting ioctls the kernel gates on DRM master (drm_ioctl flags
// DRM_MASTER): SETCRTC, CURSOR, SETGAMMA, SETPROPERTY, PAGE_FLIP, DIRTYFB,
// OBJ_SETPROPERTY, CURSOR2, ATOMIC. These always run inside the transient
// master window. Everything else (GEM, prime, property reads, WAIT_VBLANK,
// driver commands) passes straight through and is only retried with master
// if the kernel answered EACCES, so a blocking vblank wait never holds the lock.
static bool needsMaster(int request) {
    switch (_IOC_NR((unsigned)request)) {
        case 0xa2: case 0xa3: case 0xa5: case 0xab: case 0xb0:
        case 0xb1: case 0xba: case 0xbb: case 0xbc:
            return true;
        default:
            return false;
    }
}

// The composer opens card0 more than once (a DRMMaster fd for ADDFB, per-display
// fds, ...). Any fd it opens while nobody is master becomes master implicitly
// (drm_open) and then every OTHER fd of the process gets EACCES/EBUSY, exactly
// as if a foreign process held the panel. So on EBUSY, drop master on every
// other card0 fd of this process (a no-op EINVAL on the ones that are not
// master) and retry once. Only runs on the failure path.
static void dropOtherMasters(int keepFd) {
    DIR* d = opendir("/proc/self/fd");
    if (!d) return;
    struct dirent* e;
    char link[64];
    while ((e = readdir(d)) != nullptr) {
        if (e->d_name[0] < '0' || e->d_name[0] > '9') continue;
        const int n = atoi(e->d_name);
        if (n == keepFd || n == dirfd(d)) continue;
        char path[64];
        snprintf(path, sizeof(path), "/proc/self/fd/%d", n);
        const ssize_t len = readlink(path, link, sizeof(link) - 1);
        if (len <= 0) continue;
        link[len] = 0;
        if (strncmp(link, "/dev/dri/card", 13) != 0) continue;
        sReal(n, GAMMA_DRM_IOCTL_DROP_MASTER);
    }
    closedir(d);
}

// SET_MASTER, the ioctl, DROP_MASTER under the process lock. Returns the
// ioctl's result with errno preserved.
static int withMaster(int fd, int request, void* arg) {
    pthread_mutex_lock(&sLock);
    int setRc = sReal(fd, GAMMA_DRM_IOCTL_SET_MASTER);
    int setErr = errno;
    if (setRc != 0 && setErr == EBUSY) {
        dropOtherMasters(fd);
        setRc = sReal(fd, GAMMA_DRM_IOCTL_SET_MASTER);
        setErr = errno;
    }
    int rc = sReal(fd, request, arg);
    const int savedErr = errno;
    if (setRc == 0) {
        sReal(fd, GAMMA_DRM_IOCTL_DROP_MASTER);
        if (!sLoggedTaken) {
            sLoggedTaken = 1;
            ALOGI("composer takes DRM master transiently per ioctl (first fd %d)", fd);
        }
        sLoggedSetFail = 0;
    } else if (rc != 0 && savedErr == EACCES && !sLoggedSetFail) {
        // EBUSY is the expected answer while the nano home or drastic-nano own
        // the panel; anything else means the capability grant in the rc is
        // missing. Logged once per ownership change.
        sLoggedSetFail = 1;
        ALOGW("DRM ioctl 0x%x on fd %d failed EACCES and SET_MASTER failed too "
              "(errno %d %s); the panel is owned by another DRM master",
              request, fd, setErr, strerror(setErr));
    }
    pthread_mutex_unlock(&sLock);
    errno = savedErr;
    return rc;
}

// A card0 file opened while nobody is master becomes master implicitly
// (drm_open). The composer opens card0 several times over its life (one more
// after every nano hand-off was observed), and such an fd then sits on master
// while idle: the composer's own commits on its other fds fail, and nano /
// drastic-nano cannot take the panel back (SET_MASTER EACCES/EBUSY). Drop the
// implicit master right at open so a composer fd only ever holds master inside
// withMaster(). Plain open() is what libdrm's drmOpen uses; openat covers the
// rest of bionic's spellings.
typedef int (*open_fn)(const char*, int, ...);
typedef int (*openat_fn)(int, const char*, int, ...);
static open_fn sRealOpen = nullptr;
static openat_fn sRealOpenat = nullptr;

static void dropImplicitMaster(int fd, const char* path) {
    if (fd < 0 || !path || strncmp(path, "/dev/dri/card", 13) != 0) return;
    pthread_once(&sOnce, resolveReal);
    if (sReal) sReal(fd, GAMMA_DRM_IOCTL_DROP_MASTER);
}

extern "C" int open(const char* path, int flags, ...) {
    va_list ap;
    va_start(ap, flags);
    const mode_t mode = (mode_t)va_arg(ap, int);
    va_end(ap);
    if (!sRealOpen) sRealOpen = (open_fn)dlsym(RTLD_NEXT, "open");
    const int fd = sRealOpen(path, flags, mode);
    dropImplicitMaster(fd, path);
    return fd;
}

extern "C" int openat(int dirfd, const char* path, int flags, ...) {
    va_list ap;
    va_start(ap, flags);
    const mode_t mode = (mode_t)va_arg(ap, int);
    va_end(ap);
    if (!sRealOpenat) sRealOpenat = (openat_fn)dlsym(RTLD_NEXT, "openat");
    const int fd = sRealOpenat(dirfd, path, flags, mode);
    dropImplicitMaster(fd, path);
    return fd;
}

extern "C" int ioctl(int fd, int request, ...) {
    va_list ap;
    va_start(ap, request);
    void* arg = va_arg(ap, void*);
    va_end(ap);

    pthread_once(&sOnce, resolveReal);
    if (!sReal) {
        errno = ENOSYS;
        return -1;
    }

    if (_IOC_TYPE((unsigned)request) != (unsigned)DRM_IOCTL_BASE ||
        request == (int)GAMMA_DRM_IOCTL_SET_MASTER ||
        request == (int)GAMMA_DRM_IOCTL_DROP_MASTER) {
        return sReal(fd, request, arg);
    }
    if (needsMaster(request)) {
        return withMaster(fd, request, arg);
    }
    int rc = sReal(fd, request, arg);
    if (rc != 0 && errno == EACCES) {
        rc = withMaster(fd, request, arg);
    }
    return rc;
}
