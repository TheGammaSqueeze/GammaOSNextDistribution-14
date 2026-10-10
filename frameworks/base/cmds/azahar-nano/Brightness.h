// Screen brightness for the azahar-nano menu, the same path as drastic-nano's Brightness row:
// the sysfs backlight right away, then (on a worker) the lights HAL, the GammaOS brightness
// property and Settings.System.screen_brightness so the level survives leaving the game and a
// reboot.

#pragma once

namespace azahar_nano {

// Current level 0..255 (the GammaOS brightness property, 128 when unset).
int brightnessLevel();
// Applies a level, clamped to 8..255 (never fully dark from the menu).
void setBrightness(int level);

} // namespace azahar_nano
