// Host stand-in for <log/log.h> (tools/ctrrom test harness only).
#pragma once
#include <cstdio>
#define ALOGI(...) (fprintf(stderr, "I " LOG_TAG ": " __VA_ARGS__), fputc('\n', stderr))
#define ALOGW(...) (fprintf(stderr, "W " LOG_TAG ": " __VA_ARGS__), fputc('\n', stderr))
#define ALOGE(...) (fprintf(stderr, "E " LOG_TAG ": " __VA_ARGS__), fputc('\n', stderr))
#define ALOGD(...) ((void)0)
#ifndef LOG_TAG
#define LOG_TAG "host"
#endif
