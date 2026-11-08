#include "Brightness.h"

#include <log/log.h>

#include "NanoBrightness.h"

#undef LOG_TAG
#define LOG_TAG "azahar-nano"

namespace azahar_nano {

int brightnessLevel() {
    return android::nanobr::currentLevel();
}

void setBrightness(int level) {
    const int clamped = android::nanobr::clampLevel(level);
    android::nanobr::brightnessSet(clamped);
    ALOGI("brightness %d", clamped);
}

int brightnessChord(int steps) {
    const int cur = android::nanobr::currentLevel();
    const int next = android::nanobr::brightnessStepN(cur, steps);
    if (next != cur) {
        android::nanobr::brightnessSet(next);
        ALOGI("brightness %d -> %d (%d%%)", cur, next, android::nanobr::brightnessPercent(next));
    }
    return next;
}

} // namespace azahar_nano
