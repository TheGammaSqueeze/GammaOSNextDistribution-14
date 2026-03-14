/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <android-base/stringprintf.h>
#include <android-base/file.h>
#include <android-base/strings.h>
#include <batteryservice/BatteryService.h>
#include <cutils/klog.h>
#include <cutils/properties.h>
#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <unistd.h>
#include <dirent.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <ctype.h>
#include <linux/fb.h>

#include "healthd_draw.h"


#if !defined(__ANDROID_VNDK__)
#include "charger.sysprop.h"
#endif

#define HARDWARE_MODEL "ro.hardware"

#define LOGE(x...) KLOG_ERROR("charger", x); fprintf(stderr,x);
#define LOGW(x...) KLOG_WARNING("charger", x); fprintf(stderr,x);
#define LOGV(x...) KLOG_DEBUG("charger", x); fprintf(stderr,x);

using ::android::base::ReadFileToString;
using ::android::base::WriteStringToFile;

namespace {

static bool file_exists(const std::string& path) {
    return access(path.c_str(), F_OK) == 0;
}

static std::string to_lower_ascii(std::string s) {
    for (char& c : s) c = static_cast<char>(tolower(static_cast<unsigned char>(c)));
    return s;
}

static bool looks_like_mtk_platform(const std::string& value) {
    if (value.empty()) return false;
    std::string v = to_lower_ascii(value);

    if (v.find("mediatek") != std::string::npos) return true;

    // Common MediaTek platform strings are like "mt6768", "mt6897", etc.
    if (v.rfind("mt", 0) == 0 && v.size() >= 4 && isdigit(static_cast<unsigned char>(v[2]))) {
        return true;
    }

    // Some builds use generic markers.
    if (v == "mtk") return true;

    return false;
}

static bool is_mtk_device() {
    char prop[PROPERTY_VALUE_MAX] = {};

    if (property_get("ro.hardware", prop, "") > 0 && looks_like_mtk_platform(prop)) return true;
    if (property_get("ro.board.platform", prop, "") > 0 && looks_like_mtk_platform(prop)) return true;
    if (property_get("ro.hardware.platform", prop, "") > 0 && looks_like_mtk_platform(prop)) return true;

    return false;
}

static uint32_t read_u32_file(const std::string& path, uint32_t def_value) {
    std::string content;
    if (!path.empty() && ReadFileToString(path, &content)) {
        content = android::base::Trim(content);
        if (!content.empty()) {
            char* endp = nullptr;
            errno = 0;
            unsigned long v = strtoul(content.c_str(), &endp, 10);
            if (errno == 0 && endp != content.c_str()) {
                return static_cast<uint32_t>(v);
            }
        }
    }
    return def_value;
}

static bool write_u32_file(const std::string& path, uint32_t value) {
    if (path.empty()) return false;
    return WriteStringToFile(std::to_string(value), path);
}

static bool write_str_file(const std::string& path, const std::string& value) {
    if (path.empty()) return false;
    return WriteStringToFile(value, path);
}

// Best-effort: discover a usable backlight sysfs entry.
static bool find_backlight_sysfs(std::string* brightness_path, std::string* max_brightness_path,
                                std::string* power_path) {
    brightness_path->clear();
    max_brightness_path->clear();
    power_path->clear();

    // Prefer /sys/class/backlight as it is the standard Linux interface.
    {
        DIR* dir = opendir("/sys/class/backlight");
        if (dir != nullptr) {
            struct dirent* de;
            while ((de = readdir(dir)) != nullptr) {
                if (de->d_name[0] == '.') continue;
                std::string base = std::string("/sys/class/backlight/") + de->d_name;
                std::string b = base + "/brightness";
                std::string mb = base + "/max_brightness";
                std::string p = base + "/bl_power";

                if (file_exists(b) && file_exists(mb)) {
                    *brightness_path = b;
                    *max_brightness_path = mb;
                    if (file_exists(p)) *power_path = p;
                    closedir(dir);
                    return true;
                }
            }
            closedir(dir);
        }
    }

    // Fallback to common LED class entries used by some Android kernels.
    const char* led_candidates[] = {
            "/sys/class/leds/lcd-backlight",
            "/sys/class/leds/lcd_backlight",
            "/sys/class/leds/lcd_backlight0",
            "/sys/class/leds/panel0-backlight",
            "/sys/class/leds/panel-backlight",
    };

    for (const char* base_c : led_candidates) {
        std::string base(base_c);
        std::string b = base + "/brightness";
        std::string mb = base + "/max_brightness";
        if (file_exists(b) && file_exists(mb)) {
            *brightness_path = b;
            *max_brightness_path = mb;
            return true;
        }
    }

    return false;
}

}  // namespace

static bool get_split_screen() {
#if !defined(__ANDROID_VNDK__)
    return android::sysprop::ChargerProperties::draw_split_screen().value_or(false);
#else
    return false;
#endif
}

static int get_split_offset() {
#if !defined(__ANDROID_VNDK__)
    int64_t value = android::sysprop::ChargerProperties::draw_split_offset().value_or(0);
#else
    int64_t value = 0;
#endif
    if (value < static_cast<int64_t>(std::numeric_limits<int>::min())) {
        LOGW("draw_split_offset = %" PRId64 " overflow for an int; resetting to %d.\n", value,
             std::numeric_limits<int>::min());
        value = std::numeric_limits<int>::min();
    }
    if (value > static_cast<int64_t>(std::numeric_limits<int>::max())) {
        LOGW("draw_split_offset = %" PRId64 " overflow for an int; resetting to %d.\n", value,
             std::numeric_limits<int>::max());
        value = std::numeric_limits<int>::max();
    }
    return static_cast<int>(value);
}

HealthdDraw::HealthdDraw(animation* anim)
    : kSplitScreen(get_split_screen()), kSplitOffset(get_split_offset()) {
    graphics_available = true;
    sys_font = gr_sys_font();
    if (sys_font == nullptr) {
        LOGW("No system font, screen fallback text not available\n");
    } else {
        gr_font_size(sys_font, &char_width_, &char_height_);
    }

    screen_width_ = gr_fb_width() / (kSplitScreen ? 2 : 1);
    screen_height_ = gr_fb_height();

    int res;
    if (!anim->text_clock.font_file.empty() &&
        (res = gr_init_font(anim->text_clock.font_file.c_str(), &anim->text_clock.font)) < 0) {
        LOGE("Could not load time font (%d)\n", res);
    }

    if (!anim->text_percent.font_file.empty() &&
        (res = gr_init_font(anim->text_percent.font_file.c_str(), &anim->text_percent.font)) < 0) {
        LOGE("Could not load percent font (%d)\n", res);
    }

    // Try to find kirin/huawei hardware
    char prop_hardware[PROPERTY_VALUE_MAX] = {};

    is_kirin = false;
    if (property_get(HARDWARE_MODEL, prop_hardware, "") > 0) {
        if (!strcmp(prop_hardware,"hi3660")
            || !strcmp(prop_hardware,"hi3670")
            || !strcmp(prop_hardware,"hi6250")
            || !strcmp(prop_hardware,"kirin"))
        {
            LOGV("Kirin Huawei found\n");
            is_kirin=true;

            mMaxBrightness=4095;
            std::string content_str;

            if (ReadFileToString("/sys/class/leds/lcd_backlight0/max_brightness", &content_str)) {
                mMaxBrightness = std::stoi(content_str);
            }
            else {
                if (ReadFileToString("/sys/class/leds/lcd_backlight/max_brightness", &content_str)) {
                    mMaxBrightness = std::stoi(content_str);
                }
            }

            // Set max brightness
            set_brightness(mMaxBrightness);
        }
    }

    is_mtk = false;
    backlight_max_brightness_ = 0;
    backlight_restore_brightness_ = 0;

    // MediaTek: minui blanking does not reliably power down the panel/backlight on some devices.
    // For those, explicitly toggle the kernel backlight sysfs node when the charger UI is blanked.
    if (!is_kirin && is_mtk_device()) {
        is_mtk = true;
        init_mtk_backlight_paths();
    }
}

HealthdDraw::~HealthdDraw() {}

void HealthdDraw::redraw_screen(const animation* batt_anim, GRSurface* surf_unknown) {
    if (!graphics_available) return;
    clear_screen();

    /* try to display *something* */
    if (batt_anim->cur_status == BATTERY_STATUS_UNKNOWN || batt_anim->cur_level < 0 ||
        batt_anim->num_frames == 0)
        draw_unknown(surf_unknown);
    else
        draw_battery(batt_anim);
    gr_flip();
}

void HealthdDraw::set_brightness(uint32_t value) {
    LOGV("Kirin - Try to set brightness to %d\n",value)
    if (WriteStringToFile(std::to_string(value), "/sys/class/leds/lcd_backlight0/brightness")==false) {
        LOGW("Kirin - WriteStringToFile failed lcd_backlight0, unable to set brightness (lcd_backlight0)\n");
        if (WriteStringToFile(std::to_string(value), "/sys/class/leds/lcd_backlight/brightness")==false) {
            LOGE("Kirin - WriteStringToFile failed lcd_backlight, unable to set brightness (lcd_backlight)\n");
        }
    }
}

void HealthdDraw::init_mtk_backlight_paths() {
    if (!find_backlight_sysfs(&backlight_brightness_path_, &backlight_max_brightness_path_,
                             &backlight_power_path_)) {
        LOGW("MediaTek: could not discover backlight sysfs paths; falling back to minui blanking\n");
        return;
    }

    backlight_max_brightness_ = read_u32_file(backlight_max_brightness_path_, 255);

    // Cache the current brightness so we can restore it when unblanking.
    // If we cannot read a meaningful value, fall back to max brightness.
    backlight_restore_brightness_ =
            read_u32_file(backlight_brightness_path_, backlight_max_brightness_);
    if (backlight_restore_brightness_ == 0) {
        backlight_restore_brightness_ = backlight_max_brightness_;
    }

    LOGV("MediaTek backlight: brightness=%s max=%s bl_power=%s restore=%u max=%u\n",
         backlight_brightness_path_.c_str(), backlight_max_brightness_path_.c_str(),
         backlight_power_path_.empty() ? "(none)" : backlight_power_path_.c_str(),
         backlight_restore_brightness_, backlight_max_brightness_);
}

// Issue FBIOBLANK directly on the framebuffer device.  On MediaTek SoCs this
// triggers mtkfb_blank → mtkfb_early_suspend → primary_display_suspend, which
// fully powers down the LCM and backlight enable pin.  The DRM backend's CRTC
// disable does NOT trigger this kernel path, so the backlight stays on.
static void mtk_fb_blank(bool blank) {
    const char* fb_paths[] = {"/dev/graphics/fb0", "/dev/fb0"};
    for (const char* path : fb_paths) {
        int fd = open(path, O_RDWR);
        if (fd < 0) continue;
        int arg = blank ? FB_BLANK_POWERDOWN : FB_BLANK_UNBLANK;
        int ret = ioctl(fd, FBIOBLANK, arg);
        if (ret < 0) {
            LOGW("FBIOBLANK(%d) on %s failed: %s\n", arg, path, strerror(errno));
        } else {
            LOGV("FBIOBLANK(%d) on %s succeeded\n", arg, path);
        }
        close(fd);
        return;
    }
    LOGW("Could not open any framebuffer device for FBIOBLANK\n");
}

void HealthdDraw::mtk_set_backlight_blank(bool blank) {
    if (blank) {
        // Save the latest non-zero brightness before blanking.
        if (!backlight_brightness_path_.empty()) {
            uint32_t cur = read_u32_file(backlight_brightness_path_, backlight_restore_brightness_);
            if (cur != 0) backlight_restore_brightness_ = cur;
        }

        // Issue FBIOBLANK first — this triggers the MTK display driver's
        // full power-down sequence (LCM off + backlight enable pin deasserted).
        mtk_fb_blank(true);

        // Belt-and-suspenders: also write sysfs nodes.
        if (!backlight_power_path_.empty()) {
            (void)write_str_file(backlight_power_path_, "4");
        }
        if (!backlight_brightness_path_.empty()) {
            (void)write_u32_file(backlight_brightness_path_, 0);
        }
    } else {
        // Unblank: restore fbdev first, then sysfs brightness.
        mtk_fb_blank(false);

        if (!backlight_power_path_.empty()) {
            (void)write_str_file(backlight_power_path_, "0");
        }
        uint32_t restore = backlight_restore_brightness_;
        if (restore == 0) restore = (backlight_max_brightness_ ? backlight_max_brightness_ : 255);
        if (!backlight_brightness_path_.empty()) {
            (void)write_u32_file(backlight_brightness_path_, restore);
        }
    }
}

void HealthdDraw::blank_screen(bool blank, int drm) {

    if (!graphics_available) return;

    bool bmulti=gr_has_multiple_connectors();

    if (bmulti && (drm==1)) {
        KLOG_WARNING("charger", "minui graphic backend don't support multi-connector for blank screen\n");
    }

    if (is_kirin) {
        if (blank==true) {
            LOGV("Kirin - clear screen\n")
            //clear_screen();
            //gr_flip();
            set_brightness(0);
        }
        else {
            set_brightness(mMaxBrightness);
        }
    }
    else if (is_mtk) {
        // Ensure the panel backlight is not left on after the UI has timed out.
        // This is gated to MediaTek only to avoid interfering with other platforms.
        mtk_set_backlight_blank(blank);
        gr_fb_blank(blank, drm);
    }
    else {
        LOGV("Blank screen with minui api)\n");
        gr_fb_blank(blank, drm);
    }
}

// support screen rotation for foldable phone
void HealthdDraw::rotate_screen(int drm) {
    if (!graphics_available) return;
    if (drm == 0)
        gr_rotate(GRRotation::RIGHT /* landscape mode */);
    else
        gr_rotate(GRRotation::NONE /* Portrait mode */);
}

// detect dual display
bool HealthdDraw::has_multiple_connectors() {
    return graphics_available && gr_has_multiple_connectors();
}

void HealthdDraw::clear_screen(void) {
    if (!graphics_available) return;
    gr_color(0, 0, 0, 255);
    gr_clear();
}

int HealthdDraw::draw_surface_centered(GRSurface* surface) {
    if (!graphics_available) return 0;

    int w = gr_get_width(surface);
    int h = gr_get_height(surface);
    int x = (screen_width_ - w) / 2 + kSplitOffset;
    int y = (screen_height_ - h) / 2;

    LOGV("drawing surface %dx%d+%d+%d\n", w, h, x, y);
    gr_blit(surface, 0, 0, w, h, x, y);
    if (kSplitScreen) {
        x += screen_width_ - 2 * kSplitOffset;
        LOGV("drawing surface %dx%d+%d+%d\n", w, h, x, y);
        gr_blit(surface, 0, 0, w, h, x, y);
    }

    return y + h;
}

int HealthdDraw::draw_text(const GRFont* font, int x, int y, const char* str) {
    if (!graphics_available) return 0;
    int str_len_px = gr_measure(font, str);

    if (x < 0) x = (screen_width_ - str_len_px) / 2;
    if (y < 0) y = (screen_height_ - char_height_) / 2;
    gr_text(font, x + kSplitOffset, y, str, false /* bold */);
    if (kSplitScreen) gr_text(font, x - kSplitOffset + screen_width_, y, str, false /* bold */);

    return y + char_height_;
}

void HealthdDraw::determine_xy(const animation::text_field& field,
                               const int length, int* x, int* y) {
  *x = field.pos_x;
  screen_width_ = gr_fb_width() / (kSplitScreen ? 2 : 1);
  screen_height_ = gr_fb_height();

  int str_len_px = length * field.font->char_width;
  if (field.pos_x == CENTER_VAL) {
    *x = (screen_width_ - str_len_px) / 2;
  } else if (field.pos_x >= 0) {
    *x = field.pos_x;
  } else {  // position from max edge
    *x = screen_width_ + field.pos_x - str_len_px - kSplitOffset;
  }

  *y = field.pos_y;

  if (field.pos_y == CENTER_VAL) {
    *y = (screen_height_ - field.font->char_height) / 2;
  } else if (field.pos_y >= 0) {
    *y = field.pos_y;
  } else {  // position from max edge
    *y = screen_height_ + field.pos_y - field.font->char_height;
  }
}

void HealthdDraw::draw_clock(const animation* anim) {
    static constexpr char CLOCK_FORMAT[] = "%H:%M";
    static constexpr int CLOCK_LENGTH = 6;

    const animation::text_field& field = anim->text_clock;

    if (!graphics_available || field.font == nullptr || field.font->char_width == 0 ||
        field.font->char_height == 0)
        return;

    time_t rawtime;
    time(&rawtime);
    tm* time_info = localtime(&rawtime);

    char clock_str[CLOCK_LENGTH];
    size_t length = strftime(clock_str, CLOCK_LENGTH, CLOCK_FORMAT, time_info);
    if (length != CLOCK_LENGTH - 1) {
        LOGE("Could not format time\n");
        return;
    }

    int x, y;
    determine_xy(field, length, &x, &y);

    LOGV("drawing clock %s %d %d\n", clock_str, x, y);
    gr_color(field.color_r, field.color_g, field.color_b, field.color_a);
    draw_text(field.font, x, y, clock_str);
}

void HealthdDraw::draw_percent(const animation* anim) {
    if (!graphics_available) return;
    int cur_level = anim->cur_level;
    if (anim->cur_status == BATTERY_STATUS_FULL) {
        cur_level = 100;
    }

    if (cur_level < 0) return;

    const animation::text_field& field = anim->text_percent;
    if (field.font == nullptr || field.font->char_width == 0 || field.font->char_height == 0) {
        return;
    }

    std::string str = base::StringPrintf("%d%%", cur_level);

    int x, y;
    determine_xy(field, str.size(), &x, &y);

    LOGV("drawing percent %s %d %d\n", str.c_str(), x, y);
    gr_color(field.color_r, field.color_g, field.color_b, field.color_a);
    draw_text(field.font, x, y, str.c_str());
}

void HealthdDraw::draw_battery(const animation* anim) {
    if (!graphics_available) return;
    const animation::frame& frame = anim->frames[anim->cur_frame];

    if (anim->num_frames != 0) {
        draw_surface_centered(frame.surface);
        LOGV("drawing frame #%d min_cap=%d time=%d\n", anim->cur_frame, frame.min_level,
             frame.disp_time);
    }
    draw_clock(anim);
    draw_percent(anim);
}

void HealthdDraw::draw_unknown(GRSurface* surf_unknown) {
  int y;
  if (surf_unknown) {
      draw_surface_centered(surf_unknown);
  } else if (sys_font) {
      gr_color(0xa4, 0xc6, 0x39, 255);
      y = draw_text(sys_font, -1, -1, "Charging!");
      draw_text(sys_font, -1, y + 25, "?\?/100");
  } else {
      LOGW("Charging, level unknown\n");
  }
}

std::unique_ptr<HealthdDraw> HealthdDraw::Create(animation *anim) {
    if (gr_init() < 0) {
        LOGE("gr_init failed\n");
        return nullptr;
    }
    return std::unique_ptr<HealthdDraw>(new HealthdDraw(anim));
}
