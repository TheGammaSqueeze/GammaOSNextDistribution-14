// Screen brightness for azahar-nano: the shared nano module (NanoBrightness.h), the same table and
// apply path as the home and drastic-nano.

#pragma once

namespace azahar_nano {

// The wanted level, 1..255 (follows the GammaOS brightness property, 128 when unset).
int brightnessLevel();
// The menu row: a level 1..255 through the shared module (sysfs at once, the rest debounced).
void setBrightness(int level);
// Select+Vol: |steps| table steps in the direction of the sign; returns the new level.
int brightnessChord(int steps);

} // namespace azahar_nano
