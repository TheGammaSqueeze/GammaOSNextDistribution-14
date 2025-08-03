#pragma once

#define _GNU_SOURCE

#include <stdint.h>

#include <sys/param.h>
// Ensure we use a BSD powerof2 that works in static_assert (unlike glibc's).
#undef powerof2
#define powerof2(x) ((((x)-1)&(x))==0)
// This is in BSD's <sys/param.h>.
#define nitems(x) (sizeof((x))/sizeof((x)[0]))

// This is used as the size of the write buffer of sectors.
#define MAXPHYS (1024 * 1024)

// On glibc, these headers use `__unused` as an identifier, so drag them in
// first.
#include <sys/stat.h>
#if __has_include(<sys/sysctl.h>)
#include <sys/sysctl.h>
#endif
// Bionic, like the BSDs, has __unused. glibc and musl don't.
#if defined(__GLIBC__) || defined(ANDROID_HOST_MUSL)
#define __unused __attribute__((__unused__))
#endif
// Neither macOS, glibc nor musl has __packed.
#if defined(__APPLE__) || defined(__GLIBC__) || defined(ANDROID_HOST_MUSL)
#define __packed __attribute__((__packed__))
#endif

// The BSDs (including Android and macOS) have getprogname(), but glibc and musl don't.
#if defined(__GLIBC__) || defined(ANDROID_HOST_MUSL)
#include <errno.h>
static inline char* getprogname() { return program_invocation_short_name; }
#endif
