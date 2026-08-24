// Host-test stub for <log/log.h>. NanoEsdeTheme.cpp (the only file this test compiles
// from the tree) uses a single ALOGW; on-device the real liblog header is used instead.
// This stub is only on the include path when building the host test (see
// esde_parser_check.cpp's build command, which adds test/ to -I).
#pragma once
#include <cstdio>
#define ALOGW(...) do { fprintf(stderr, "[W] " __VA_ARGS__); fprintf(stderr, "\n"); } while (0)
#define ALOGI(...) do { } while (0)
#define ALOGE(...) do { fprintf(stderr, "[E] " __VA_ARGS__); fprintf(stderr, "\n"); } while (0)
#define ALOGD(...) do { } while (0)
