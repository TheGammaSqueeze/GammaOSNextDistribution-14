#pragma once
#include <cstdio>
// Minimal host stub for Android's <log/log.h> so the GL-free ES-DE parser
// (NanoEsdeTheme.cpp) links off-device. Routes ALOG* to stderr.
#define ALOGW(...) do { fprintf(stderr, "[W] "); fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n"); } while (0)
#define ALOGE(...) do { fprintf(stderr, "[E] "); fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n"); } while (0)
#define ALOGI(...) do { } while (0)
#define ALOGD(...) do { } while (0)
#define ALOGV(...) do { } while (0)
