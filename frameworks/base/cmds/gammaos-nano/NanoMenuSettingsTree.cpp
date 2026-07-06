/*
 * Copyright (C) 2026 GammaOS
 *
 * Settings tree: hierarchical menu definition, navigation logic,
 * and property read/write for the Nano settings browser.
 *
 * The tree is built once at startup via buildSettingsTree(). Navigation
 * uses a parent-index stack; the render thread reads cached property
 * values that a background thread refreshes.
 *
 * All user-visible strings are in English. The string table approach
 * (id-based lookup) is designed so a future translation layer can
 * override labels by id without changing the tree structure.
 */

#define LOG_TAG "GammaOSNano"

#include <algorithm>
#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <string>
#include <thread>
#include <unistd.h>
#include <vector>

#include <cutils/properties.h>
#include <android-base/properties.h>
#include <log/log.h>

#include "NanoMenu.h"
#include "NanoMenuSettingsTree.h"
#include "NanoMenuStrings.h"
#include "NanoI18n.h"

namespace android {

// ---------------------------------------------------------------------------
// SettingListOption parser
// ---------------------------------------------------------------------------

std::vector<SettingListOption> parseListOptions(const std::string& opts) {
    std::vector<SettingListOption> out;
    size_t p = 0;
    while (p < opts.size()) {
        size_t comma = opts.find(',', p);
        if (comma == std::string::npos) comma = opts.size();
        std::string pair = opts.substr(p, comma - p);
        p = comma + 1;
        size_t colon = pair.find(':');
        if (colon == std::string::npos) continue;
        SettingListOption o;
        o.value = pair.substr(0, colon);
        o.label = pair.substr(colon + 1);
        out.push_back(std::move(o));
    }
    return out;
}

// ---------------------------------------------------------------------------
// SettingsTreeBuilder
// ---------------------------------------------------------------------------

SettingsTreeBuilder::SettingsTreeBuilder(std::vector<SettingNode>& nodes)
    : mNodes(nodes) {}

int SettingsTreeBuilder::parent() const {
    return mParentStack.empty() ? -1 : mParentStack.back();
}

void SettingsTreeBuilder::beginCategory(const char* id, const char* label) {
    int idx = (int)mNodes.size();
    SettingNode n;
    n.id = id;
    n.label = label;
    n.type = SettingNodeType::kCategory;
    n.source = SettingSource::kNone;
    n.selfIdx = idx;
    n.parentIdx = parent();
    n.screenId = -1;
    mNodes.push_back(std::move(n));
    mParentStack.push_back(idx);
}

void SettingsTreeBuilder::endCategory() {
    if (!mParentStack.empty()) mParentStack.pop_back();
}

void SettingsTreeBuilder::toggle(const char* id, const char* label,
                                 SettingSource src, const char* key,
                                 const char* def) {
    int idx = (int)mNodes.size();
    SettingNode n;
    n.id = id;
    n.label = label;
    n.type = SettingNodeType::kToggle;
    n.source = src;
    n.key = key;
    n.defaultVal = def;
    n.selfIdx = idx;
    n.parentIdx = parent();
    n.screenId = -1;
    mNodes.push_back(std::move(n));
}

void SettingsTreeBuilder::text(const char* id, const char* label,
                               SettingSource src, const char* key,
                               const char* def) {
    int idx = (int)mNodes.size();
    SettingNode n;
    n.id = id;
    n.label = label;
    n.type = SettingNodeType::kText;
    n.source = src;
    n.key = key;
    n.defaultVal = def;
    n.selfIdx = idx;
    n.parentIdx = parent();
    n.screenId = -1;
    mNodes.push_back(std::move(n));
}

void SettingsTreeBuilder::list(const char* id, const char* label,
                               SettingSource src, const char* key,
                               const char* def, const char* options) {
    int idx = (int)mNodes.size();
    SettingNode n;
    n.id = id;
    n.label = label;
    n.type = SettingNodeType::kList;
    n.source = src;
    n.key = key;
    n.defaultVal = def;
    n.options = options;
    n.selfIdx = idx;
    n.parentIdx = parent();
    n.screenId = -1;
    mNodes.push_back(std::move(n));
}

void SettingsTreeBuilder::action(const char* id, const char* label) {
    int idx = (int)mNodes.size();
    SettingNode n;
    n.id = id;
    n.label = label;
    n.type = SettingNodeType::kAction;
    n.source = SettingSource::kNone;
    n.selfIdx = idx;
    n.parentIdx = parent();
    n.screenId = -1;
    mNodes.push_back(std::move(n));
}

void SettingsTreeBuilder::screen(const char* id, const char* label,
                                 int screenId) {
    int idx = (int)mNodes.size();
    SettingNode n;
    n.id = id;
    n.label = label;
    n.type = SettingNodeType::kScreen;
    n.source = SettingSource::kNone;
    n.selfIdx = idx;
    n.parentIdx = parent();
    n.screenId = screenId;
    mNodes.push_back(std::move(n));
}

void SettingsTreeBuilder::info(const char* id, const char* label,
                               SettingSource src, const char* key,
                               const char* def) {
    int idx = (int)mNodes.size();
    SettingNode n;
    n.id = id;
    n.label = label;
    n.type = SettingNodeType::kInfo;
    n.source = src;
    n.key = key;
    n.defaultVal = def;
    n.selfIdx = idx;
    n.parentIdx = parent();
    n.screenId = -1;
    mNodes.push_back(std::move(n));
}

// ---------------------------------------------------------------------------
// Tree construction
// ---------------------------------------------------------------------------

void NanoMenu::buildSettingsTree() {
    mSettingsNodes.clear();
    mSettingsNodes.reserve(256);

    SettingsTreeBuilder b(mSettingsNodes);

    // ===== Network & Internet =====
    b.beginCategory("net", "Network & Internet");
      b.screen("wifi", "Wi-Fi", 0);
      b.toggle("airplane", "Airplane Mode",
               SettingSource::kGlobal, "airplane_mode_on", "0");
    b.endCategory();

    // ===== Language =====
    b.beginCategory("lang", "Language");
      b.list("language", "Language / Region",
             SettingSource::kProp, "persist.sys.locale", "en-US",
             "en-US:English,"
             "es-ES:Espa\xC3\xB1ol,"
             "fr-FR:Fran\xC3\xA7""ais,"
             "de-DE:Deutsch,"
             "it-IT:Italiano,"
             "pt-BR:Portugu\xC3\xAAs,"
             "nl-NL:Nederlands,"
             "ru-RU:\xD0\xA0\xD1\x83\xD1\x81\xD1\x81\xD0\xBA\xD0\xB8\xD0\xB9,"
             "ja-JP:\xE6\x97\xA5\xE6\x9C\xAC\xE8\xAA\x9E,"
             "ko-KR:\xED\x95\x9C\xEA\xB5\xAD\xEC\x96\xB4,"
             "zh-CN:\xE7\xAE\x80\xE4\xBD\x93\xE4\xB8\xAD\xE6\x96\x87,"
             "zh-TW:\xE7\xB9\x81\xE9\xAB\x94\xE4\xB8\xAD\xE6\x96\x87,"
             "ar-SA:\xD8\xA7\xD9\x84\xD8\xB9\xD8\xB1\xD8\xA8\xD9\x8A\xD8\xA9,"
             "tr-TR:T\xC3\xBCrk\xC3\xA7""e,"
             "pl-PL:Polski");
    b.endCategory();

    // ===== Connected Devices =====
    b.beginCategory("devices", "Connected Devices");
      b.screen("bt", "Bluetooth", 1);
    b.endCategory();

    // ===== Display =====
    b.beginCategory("display", "Display");
      b.list("timeout", "Screen Timeout",
             SettingSource::kSystem, "screen_off_timeout", "60000",
             "15000:15 seconds,30000:30 seconds,60000:1 minute,"
             "120000:2 minutes,300000:5 minutes,600000:10 minutes,"
             "1800000:30 minutes,-1:Never");
      b.toggle("dark_theme", "Dark Theme",
               SettingSource::kSecure, "ui_night_mode", "2");
      // Screen Orientation is the single control for accelerometer_rotation (its
      // "Auto" value enables the sensor), so no separate Auto-Rotate toggle - two
      // controls on the same key desync (Auto-Rotate On silently overrides a fixed
      // orientation because WMS short-circuits when isAutoRotationEnabled()).
      b.list("nano_orientation", "Screen Orientation",
             SettingSource::kProp, "persist.gammaos.nano.orientation", "landscape",
             "auto:Auto,landscape:Landscape,rev_landscape:Landscape (reverse),"
             "portrait:Portrait,rev_portrait:Portrait (reverse)");
      b.list("font_scale", "Font Size",
             SettingSource::kSystem, "font_scale", "1.0",
             "0.85:Small,1.0:Default,1.15:Large,1.30:Largest");
    b.endCategory();

    // ===== Sound =====
    b.beginCategory("sound", "Sound");
      b.toggle("touch_sounds", "Touch Sounds",
               SettingSource::kSystem, "sound_effects_enabled", "1");
      b.toggle("charging_sounds", "Charging Sounds",
               SettingSource::kGlobal, "charging_sounds_enabled", "1");
      b.toggle("lock_sounds", "Screen Lock Sounds",
               SettingSource::kSystem, "lockscreen_sounds_enabled", "1");
    b.endCategory();

    // ===== Battery =====
    b.beginCategory("battery", "Battery");
      b.toggle("battery_pct", "Battery Percentage",
               SettingSource::kSystem, "status_bar_show_battery_percent", "0");
      b.toggle("battery_saver", "Battery Saver",
               SettingSource::kGlobal, "low_power", "0");
    b.endCategory();

    // ===== Apps =====
    b.beginCategory("apps", "Apps");
      b.info("apps_info", "Manage apps from full Android Settings",
             SettingSource::kNone, "", "");
    b.endCategory();

    // ===== GammaOS Toolbox =====
    b.beginCategory("gammaos", "GammaOS");

      // -- Display --
      b.beginCategory("gos_display", "Display");
        b.toggle("immersive", "Immersive Mode",
                 SettingSource::kProp, "persist.gammaos.immersive", "0");
        b.toggle("refresh_lock", "Refresh Rate Lock",
                 SettingSource::kProp, "persist.gammaos.refresh.lock", "0");
        b.text("refresh_rate", "Refresh Rate",
               SettingSource::kProp, "persist.gammaos.refresh.rate", "0");
        b.toggle("display_tweaks", "Display Tweaks",
                 SettingSource::kProp, "persist.gammaos.display.tweaks", "false");
        b.toggle("force_client_comp", "Force Client Composition",
                 SettingSource::kProp, "persist.gammaos.force_client_comp", "false");
        b.text("rotation_cooldown", "Rotation Cooldown (ms)",
               SettingSource::kProp, "persist.gammaos.rotation_cooldown", "0");
        b.toggle("desktop_fullscreen", "Desktop Fullscreen",
                 SettingSource::kProp, "persist.gammaos.desktop.fullscreen", "false");
        b.toggle("display_unique_names", "Display Unique Names",
                 SettingSource::kProp, "persist.gammaos.display.unique_names", "true");
        b.toggle("keep_underlay", "Keep Underlay on Shade",
                 SettingSource::kProp, "persist.gammaos.sf.keep_underlay_on_shade", "true");
        b.text("renderengine_backend", "RenderEngine Backend",
               SettingSource::kProp, "persist.gammaos.renderengine.backend", "");
        b.text("vsync_period_ns", "Vsync Period (ns)",
               SettingSource::kProp, "persist.gammaos.vsync_period_ns", "0");
        b.text("square_sticky_ms", "Square Sticky (ms)",
               SettingSource::kProp, "persist.gammaos.square.sticky_ms", "1200");
      b.endCategory();

      // -- BFI --
      b.beginCategory("gos_bfi", "BFI");
        b.toggle("bfi_enable", "Enable",
                 SettingSource::kProp, "persist.gammaos.bfi.enable", "false");
        b.list("bfi_mode", "Mode",
               SettingSource::kProp, "persist.gammaos.bfi.mode", "ctm",
               "ctm:CTM,gamma:Gamma,layer:Layer");
        b.text("bfi_preset", "Preset",
               SettingSource::kProp, "persist.gammaos.bfi.preset", "");
        b.text("bfi_pattern", "Pattern",
               SettingSource::kProp, "persist.gammaos.bfi.pattern", "");
        b.text("bfi_black_floor", "Black Floor",
               SettingSource::kProp, "persist.gammaos.bfi.black_floor", "0.0");
        b.text("bfi_polarity_ms", "Polarity Period (ms)",
               SettingSource::kProp, "persist.gammaos.bfi.polarity_period_ms", "1000");
        b.toggle("bfi_subframe", "Subframe Enable",
                 SettingSource::kProp, "persist.gammaos.bfi.subframe.enable", "false");
        b.text("bfi_subframe_phase", "Subframe Phase Step",
               SettingSource::kProp, "persist.gammaos.bfi.subframe.phase_step", "0.5");
        b.text("bfi_subframe_cadence", "Subframe Cadence Min",
               SettingSource::kProp, "persist.gammaos.bfi.subframe.cadence_min", "1.0");
        b.text("bfi_seam_brightness", "Seam Brightness",
               SettingSource::kProp, "persist.gammaos.bfi.seam_brightness", "");
        b.text("bfi_seam_follow", "Seam Follow Brightness",
               SettingSource::kProp, "persist.gammaos.bfi.seam_follow_brightness", "");
      b.endCategory();

      // -- BFI Flip Transition --
      b.beginCategory("gos_bfi_flip", "BFI Flip Transition");
        b.text("flip_out_frames", "Out Frames",
               SettingSource::kProp, "persist.gammaos.bfi.flip.out_frames", "10");
        b.text("flip_in_frames", "In Frames",
               SettingSource::kProp, "persist.gammaos.bfi.flip.in_frames", "10");
        b.text("flip_sat", "Saturation",
               SettingSource::kProp, "persist.gammaos.bfi.flip.sat", "1.0");
        b.text("flip_gamma", "Gamma",
               SettingSource::kProp, "persist.gammaos.bfi.flip.gamma", "1.0");
        b.toggle("flip_use_auto", "Use Auto",
                 SettingSource::kProp, "persist.gammaos.bfi.flip.use_auto", "true");
        b.text("flip_auto_dip", "Auto Dip",
               SettingSource::kProp, "persist.gammaos.bfi.flip.auto_dip", "0.92");
        b.toggle("flip_contrast_enable", "Contrast Enable",
                 SettingSource::kProp, "persist.gammaos.bfi.flip.contrast.enable", "false");
        b.text("flip_contrast", "Contrast",
               SettingSource::kProp, "persist.gammaos.bfi.flip.contrast", "1.0");
        b.text("flip_contrast_pivot", "Contrast Pivot",
               SettingSource::kProp, "persist.gammaos.bfi.flip.contrast_pivot", "0.5");
        b.toggle("flip_contrast_hw", "HW Contrast",
                 SettingSource::kProp, "persist.gammaos.bfi.flip.contrast.hw", "false");
        b.toggle("flip_zero_eps", "Zero Epsilon",
                 SettingSource::kProp, "persist.gammaos.bfi.flip.zero_eps", "true");
        b.text("flip_rgb_r", "RGB Red",
               SettingSource::kProp, "persist.gammaos.bfi.flip.rgb.r", "1.0");
        b.text("flip_rgb_g", "RGB Green",
               SettingSource::kProp, "persist.gammaos.bfi.flip.rgb.g", "1.0");
        b.text("flip_rgb_b", "RGB Blue",
               SettingSource::kProp, "persist.gammaos.bfi.flip.rgb.b", "1.0");
      b.endCategory();

      // -- CRT Shader --
      b.beginCategory("gos_shader", "CRT Shader");
        b.toggle("shader_enable", "Enable",
                 SettingSource::kProp, "persist.gammaos.shader.enable", "0");
        b.text("shader_type", "Shader Type",
               SettingSource::kProp, "persist.gammaos.shader.type", "");
        b.text("shader_bp_grace", "BP Grace Frames",
               SettingSource::kProp, "persist.gammaos.shader.bp_grace_frames", "6");
        b.text("shader_custom_preset", "Custom Preset",
               SettingSource::kProp, "persist.gammaos.shader.custom.preset", "");
        b.text("shader_custom_res", "Custom Res Scale",
               SettingSource::kProp, "persist.gammaos.shader.custom.res_scale", "");
      b.endCategory();

      // -- CRT Simple --
      b.beginCategory("gos_crt_simple", "CRT Simple Shader");
        b.text("crt_scan_px", "Scanline Width (px)",
               SettingSource::kProp, "persist.gammaos.shader.crt-simple.scan_px", "4");
        b.text("crt_scan_strength", "Scanline Strength",
               SettingSource::kProp, "persist.gammaos.shader.crt-simple.scan_strength", "0.4");
        b.text("crt_curvature", "Curvature",
               SettingSource::kProp, "persist.gammaos.shader.crt-simple.curv", "0.03");
        b.text("crt_vignette", "Vignette",
               SettingSource::kProp, "persist.gammaos.shader.crt-simple.vignette", "0.01");
        b.text("crt_edge_soft", "Edge Soft (px)",
               SettingSource::kProp, "persist.gammaos.shader.crt-simple.edge_soft_px", "4");
        b.text("crt_blur", "Blur Intensity",
               SettingSource::kProp, "persist.gammaos.shader.crt-simple.blur_intensity", "0");
        b.toggle("crt_half_res", "Half Resolution",
                 SettingSource::kProp, "persist.gammaos.shader.crt-simple.half_res", "false");
      b.endCategory();

      // -- LCD3x Shader --
      b.beginCategory("gos_lcd3x", "LCD3x Shader");
        b.toggle("lcd3x_half_res", "Half Resolution",
                 SettingSource::kProp, "persist.gammaos.shader.lcd3x.half_res", "false");
        b.text("lcd3x_brighten_scan", "Brighten Scanlines",
               SettingSource::kProp, "persist.gammaos.shader.lcd3x.brighten_scanlines", "4.0");
        b.text("lcd3x_brighten_lcd", "Brighten LCD",
               SettingSource::kProp, "persist.gammaos.shader.lcd3x.brighten_lcd", "4.0");
        b.text("lcd3x_grid_x", "Grid Size X",
               SettingSource::kProp, "persist.gammaos.shader.lcd3x.grid_px_x", "4.0");
        b.text("lcd3x_grid_y", "Grid Size Y",
               SettingSource::kProp, "persist.gammaos.shader.lcd3x.grid_px_y", "4.0");
      b.endCategory();

      // -- LCD Shader --
      b.beginCategory("gos_lcd", "LCD Shader");
        b.toggle("lcd_half_res", "Half Resolution",
                 SettingSource::kProp, "persist.gammaos.shader.lcd.half_res", "false");
        b.text("lcd_response_time", "Response Time",
               SettingSource::kProp, "persist.gammaos.shader.lcd.response_time", "0");
        b.text("lcd_scan_strength", "Scan Strength",
               SettingSource::kProp, "persist.gammaos.shader.lcd.scan_strength", "0.20");
        b.text("lcd_subpixel", "Subpixel Strength",
               SettingSource::kProp, "persist.gammaos.shader.lcd.subpixel_strength", "0.40");
        b.text("lcd_gap_strength", "Gap Strength",
               SettingSource::kProp, "persist.gammaos.shader.lcd.gap_strength", "0.10");
        b.text("lcd_gap_px", "Gap (px)",
               SettingSource::kProp, "persist.gammaos.shader.lcd.gap_px", "0.05");
      b.endCategory();

      // -- Blur Fill Shader --
      b.beginCategory("gos_blurfill", "Blur Fill Shader");
        b.text("blurfill_sigma", "Sigma",
               SettingSource::kProp, "persist.gammaos.shader.blurfill.sigma", "12.0");
        b.text("blurfill_strength", "Strength",
               SettingSource::kProp, "persist.gammaos.shader.blurfill.strength", "1.0");
        b.text("blurfill_edge_px", "Edge (px)",
               SettingSource::kProp, "persist.gammaos.shader.blurfill.edge_px", "160.0");
        b.text("blurfill_feather_px", "Feather (px)",
               SettingSource::kProp, "persist.gammaos.shader.blurfill.feather_px", "40.0");
        b.text("blurfill_res_scale", "Resolution Scale",
               SettingSource::kProp, "persist.gammaos.shader.blurfill.res_scale", "0.5");
        b.text("blurfill_orientation", "Orientation",
               SettingSource::kProp, "persist.gammaos.shader.blurfill.orientation", "auto");
      b.endCategory();

      // -- Dual-Stack Display --
      b.beginCategory("gos_dualstack", "Dual-Stack Display");
        b.toggle("ds_enable", "Enable",
                 SettingSource::kProp, "persist.gammaos.dualstack.enabled", "false");
        b.toggle("ds_swap", "Swap Displays",
                 SettingSource::kProp, "persist.gammaos.dualstack.swap", "false");
        b.text("ds_pkgs", "Packages",
               SettingSource::kProp, "persist.gammaos.dualstack.pkgs", "");
        b.toggle("ds_kill_pkgs", "Kill Packages",
                 SettingSource::kProp, "persist.gammaos.dualstack.killpackages.enabled", "false");
        b.toggle("ds_nn", "Nearest Neighbor",
                 SettingSource::kProp, "persist.gammaos.dualstack.sf.nearest_neighbor", "true");
        b.toggle("ds_sv_only", "SurfaceView Only",
                 SettingSource::kProp, "persist.gammaos.dualstack.sf.surfaceview_only", "true");
        b.toggle("ds_keep_sysui", "Keep SystemUI",
                 SettingSource::kProp, "persist.gammaos.dualstack.sf.surfaceview_only.keep_systemui", "true");
        b.text("ds_max_fb", "Max FB Acquired Buffers",
               SettingSource::kProp, "persist.gammaos.dualstack.sf.max_fb_acquired_buffers", "3");
        b.toggle("ds_no_backpressure", "Disable GL Backpressure",
                 SettingSource::kProp, "persist.gammaos.dualstack.sf.disable_gl_backpressure", "true");
        b.toggle("ds_blast_tune", "Blast Tune",
                 SettingSource::kProp, "persist.gammaos.dualstack.blast.tune", "true");
        b.toggle("ds_blast_async", "Blast Async",
                 SettingSource::kProp, "persist.gammaos.dualstack.blast.async", "true");
      b.endCategory();

      // -- External Display --
      b.beginCategory("gos_external", "External Display");
        b.toggle("ext_primary", "External as Primary",
                 SettingSource::kProp, "persist.gammaos.ext.primary", "false");
        b.toggle("ext_force_mirror", "Force Mirror",
                 SettingSource::kProp, "persist.gammaos.ext.force_mirror", "false");
        b.toggle("ext_mirror_resize", "Mirror Resize",
                 SettingSource::kProp, "persist.gammaos.ext.mirror_resize", "false");
        b.toggle("ext_half_4k", "Half 4K",
                 SettingSource::kProp, "persist.gammaos.ext.half_4k", "false");
        b.toggle("sec_force_on", "Secondary Force On",
                 SettingSource::kProp, "persist.gammaos.sec_force_on", "false");
        b.text("sec_home", "Secondary Home Package",
               SettingSource::kProp, "persist.gammaos.secondary_home", "");
        b.toggle("sec_display_enable", "Secondary Display Enable",
                 SettingSource::kProp, "persist.gammaos.secondary_display.enabled", "false");
        b.text("sec_display_pkgs", "Secondary Display Packages",
               SettingSource::kProp, "persist.gammaos.secondary_display.packages", "");
        b.text("delay_primary_frames", "Display Delay Primary (frames)",
               SettingSource::kProp, "persist.gammaos.display.delay.primary_frames", "0");
        b.text("delay_external_frames", "Display Delay External (frames)",
               SettingSource::kProp, "persist.gammaos.display.delay.external_frames", "0");
      b.endCategory();

      // -- Multi-Display --
      b.beginCategory("gos_multidisplay", "Multi-Display");
        b.toggle("dual_focus", "Dual Focus",
                 SettingSource::kProp, "persist.gammaos.multidisplay.dual_focus", "false");
        b.toggle("split_brightness", "Split Brightness",
                 SettingSource::kProp, "persist.gammaos.multidisplay.split_brightness", "false");
      b.endCategory();

      // -- IME --
      b.beginCategory("gos_ime", "IME");
        b.toggle("ime_pin", "Pin IME to Display",
                 SettingSource::kProp, "persist.gammaos.ime.pin.enabled", "false");
        b.text("ime_display_id", "Display ID",
               SettingSource::kProp, "persist.gammaos.ime.pin.display_id", "0");
        b.toggle("ime_swap", "Swap IME",
                 SettingSource::kProp, "persist.gammaos.ime.pin.swap", "false");
      b.endCategory();

      // -- Audio --
      b.beginCategory("gos_audio", "Audio");
        b.toggle("multivolume", "Multi-Volume",
                 SettingSource::kProp, "persist.gammaos.audio.multivolume", "false");
        b.toggle("unisoc_hdmi", "Unisoc HDMI Audio",
                 SettingSource::kProp, "persist.gammaos.unisoc.hdmi.enable", "false");
        b.toggle("allwinner_hdmi", "Allwinner HDMI Audio",
                 SettingSource::kProp, "persist.gammaos.allwinner.hdmi.enable", "false");
      b.endCategory();

      // -- Gamepad --
      b.beginCategory("gos_gamepad", "Gamepad");
        // On-screen button prompts (the X/O glyphs and A/B/X/Y letters in dialogs
        // and legends). face_glyphs picks the glyph set; face_swap relabels which
        // face button reads as OK vs Cancel (display only, the input is unchanged).
        b.list("btn_glyphs", "Button Prompts",
               SettingSource::kProp, "persist.gammaos.nano.face_glyphs", "letters",
               "letters:A / B / X / Y,playstation:PlayStation");
        b.list("btn_swap", "OK Button",
               SettingSource::kProp, "persist.gammaos.nano.face_swap", "0",
               "0:A / Cross,1:B / Circle");
        // enable/merge/hide_source: the init rc starts gammapad on enable=1 and the
        // daemon reads these via GetIntProperty, so store 0/1 (a "true"/"false" string
        // never matches =1 and fails GetIntProperty), like the transform toggles below.
        b.toggle("gp_enable", "Enable",
                 SettingSource::kProp, "persist.gammaos.gamepad.enable", "0");
        b.toggle("gp_merge", "Merge Controllers",
                 SettingSource::kProp, "persist.gammaos.gamepad.merge", "1");
        b.toggle("gp_hide_source", "Hide Source Device",
                 SettingSource::kProp, "persist.gammaos.gamepad.hide_source", "1");
        b.text("gp_devices", "Device Paths",
               SettingSource::kProp, "persist.gammaos.gamepad.devices", "");
        // Gamepad transform props read by the gammapad daemon via GetIntProperty:
        // store 0/1 (a "true"/"false" string parses to 0 = Off). Default "0".
        b.toggle("gp_abxy_swap", "ABXY Swap",
                 SettingSource::kProp, "persist.gammaos.gamepad.abxy_swap", "0");
        b.toggle("gp_invert_left", "Invert Left Stick",
                 SettingSource::kProp, "persist.gammaos.gamepad.invert_left", "0");
        b.toggle("gp_invert_right", "Invert Right Stick",
                 SettingSource::kProp, "persist.gammaos.gamepad.invert_right", "0");
        b.toggle("gp_analog_dpad", "Analog to D-Pad",
                 SettingSource::kProp, "persist.gammaos.gamepad.analog_to_dpad", "0");
        b.toggle("gp_dpad_analog", "D-Pad to Analog",
                 SettingSource::kProp, "persist.gammaos.gamepad.dpad_to_analog", "0");
        b.text("gp_dpad_threshold", "D-Pad Threshold",
               SettingSource::kProp, "persist.gammaos.gamepad.dpad_threshold", "50");
        b.text("gp_sensitivity", "Global Sensitivity",
               SettingSource::kProp, "persist.gammaos.gamepad.global_sensitivity", "0");
        b.toggle("gp_pwm_enable", "PWM Enable",
                 SettingSource::kProp, "persist.gammaos.gamepad.pwm_enable", "1");
        b.text("gp_pwm_intensity", "PWM Intensity",
               SettingSource::kProp, "persist.gammaos.gamepad.pwm_intensity", "255");
        b.text("gp_device_name", "Device Name",
               SettingSource::kProp, "persist.gammaos.gamepad.device_name", "Xbox Wireless Controller");
        b.text("gp_remap_btn", "Button Remap",
               SettingSource::kProp, "persist.gammaos.gamepad.remap_btn", "");
        b.text("gp_remap_axis", "Axis Remap",
               SettingSource::kProp, "persist.gammaos.gamepad.remap_axis", "");
        b.text("gp_combo_map", "Combo Map",
               SettingSource::kProp, "persist.gammaos.gamepad.combo_map", "");
        b.text("gp_axis_btn", "Axis to Button",
               SettingSource::kProp, "persist.gammaos.gamepad.axis_btn", "");
        b.text("gp_ff_device", "FF Vibrate Device",
               SettingSource::kProp, "persist.gammaos.gamepad.ff_vibrate_device", "");
        b.text("gp_blacklist_pass", "Blacklist Passthrough",
               SettingSource::kProp, "persist.gammaos.gamepad.blacklist_pass", "");
        b.toggle("gp_screenmap", "Screen Map",
                 SettingSource::kProp, "persist.gammaos.screenmap.enabled", "0");
      b.endCategory();

      // -- Mouse Mode --
      b.beginCategory("gos_mouse", "Mouse Mode");
        b.text("mouse_stick_speed", "Stick Speed",
               SettingSource::kProp, "persist.gammaos.gamepad.mouse_stick_speed", "12");
        b.text("mouse_dpad_speed", "D-Pad Speed",
               SettingSource::kProp, "persist.gammaos.gamepad.mouse_dpad_speed", "6");
        b.text("mouse_boost", "Boost",
               SettingSource::kProp, "persist.gammaos.gamepad.mouse_boost", "20");
        b.text("mouse_scroll_speed", "Scroll Speed",
               SettingSource::kProp, "persist.gammaos.gamepad.mouse_scroll_speed", "4");
      b.endCategory();

      // -- RGB LED --
      b.beginCategory("gos_rgb", "RGB LED");
        b.toggle("rgb_enable", "Enable",
                 SettingSource::kProp, "persist.gammaos.rgb.enable", "false");
        b.text("rgb_fps", "FPS",
               SettingSource::kProp, "persist.gammaos.rgb.fps", "6");
        b.text("rgb_brightness", "LED Brightness",
               SettingSource::kProp, "persist.gammaos.rgb.led_brightness", "255");
        b.toggle("rgb_scale", "Scale with Display Brightness",
                 SettingSource::kProp, "persist.gammaos.rgb.scale_with_brightness", "false");
        b.toggle("rgb_fade", "Fade Enable",
                 SettingSource::kProp, "persist.gammaos.rgb.fade.enable", "true");
        b.text("rgb_fade_fps", "Fade FPS",
               SettingSource::kProp, "persist.gammaos.rgb.fade.fps", "60");
        b.toggle("rgb_pre_fx", "Pre-FX Sampling",
                 SettingSource::kProp, "persist.gammaos.rgb.sample.pre_fx", "true");
        b.text("rgb_effect", "Effect Name",
               SettingSource::kProp, "persist.gammaos.rgb.effect", "");
        b.toggle("rgb_split", "Split LEDs",
                 SettingSource::kProp, "persist.gammaos.rgb.split", "false");
        b.text("rgb_sat_boost", "Saturation Boost",
               SettingSource::kProp, "persist.gammaos.rgb.saturation_boost", "1.4");
      b.endCategory();

      // -- Launch Guard --
      b.beginCategory("gos_launch_guard", "Launch Guard");
        b.toggle("lg_enable", "Enable",
                 SettingSource::kProp, "persist.gammaos.launch.guard.enabled", "false");
        b.text("lg_callers", "Callers",
               SettingSource::kProp, "persist.gammaos.launch.guard.callers", "");
        b.text("lg_targets", "Targets",
               SettingSource::kProp, "persist.gammaos.launch.guard.targets", "");
      b.endCategory();

      // -- System --
      b.beginCategory("gos_system", "System");
        b.text("qs_blacklist", "QS Blacklist",
               SettingSource::kProp, "persist.gammaos.qs.blacklist", "");
        b.text("bg_process_limit", "BG Process Limit",
               SettingSource::kProp, "persist.gammaos.bg_process_limit", "");
        b.text("gesture_wake_ignore", "Gesture Wake Ignore",
               SettingSource::kProp, "persist.gammaos.gesture_wake_ignore", "");
        b.text("performance_mode", "Performance Mode",
               SettingSource::kProp, "persist.gammaos.performance_mode", "stock");
        b.text("qs_override_tiles", "QS Override Default Tiles",
               SettingSource::kProp, "persist.gammaos.qs.override_default_tiles", "");
      b.endCategory();

      // -- Power & Performance --
      b.beginCategory("gos_power", "Power & Performance");
        b.text("fan_mode", "Fan Mode",
               SettingSource::kProp, "persist.gammaos.fan_mode", "");
        b.toggle("ultra_low_power", "Ultra Low Power Saving",
                 SettingSource::kProp, "persist.gammaos.ultra_low_power_saving_mode", "false");
        b.text("ulp_exclude", "Exclude Packages",
               SettingSource::kProp, "persist.gammaos.ultra_low_power_saving_freeze_exclude_packages", "");
      b.endCategory();

      // -- RetroArch & Emulation --
      b.beginCategory("gos_retro", "RetroArch & Emulation");
        b.toggle("ra_backbutton", "RetroArch Back Button Override",
                 SettingSource::kProp, "persist.gammaos.retroarchoverride.backbutton", "0");
        b.toggle("startselectled", "Start+Select LED",
                 SettingSource::kProp, "persist.gammaos.startselectled", "0");
      b.endCategory();

      // -- USB & Docking --
      b.beginCategory("gos_usb", "USB & Docking");
        b.toggle("usb_controller_switch", "USB Controller Switch",
                 SettingSource::kProp, "persist.gammaos.usbcontrollerswitch", "false");
        b.toggle("dc_dimming", "DC Dimming Emulation",
                 SettingSource::kProp, "persist.gammaos.dcdimmingemulation", "0");
      b.endCategory();

      // -- Desktop & Taskbar --
      b.beginCategory("gos_desktop", "Desktop & Taskbar");
        b.toggle("taskbar_phone", "Phone Taskbar",
                 SettingSource::kProp, "persist.gammaos.taskbar.phone", "true");
        b.toggle("taskbar_dual", "Dual Taskbar",
                 SettingSource::kProp, "persist.gammaos.taskbar.dual", "false");
        b.toggle("wallpaper_multidisplay", "Force Multi-Display Wallpaper",
                 SettingSource::kProp, "persist.gammaos.wallpaper.force_multidisplay", "false");
      b.endCategory();

    b.endCategory(); // GammaOS

    // ===== System =====
    b.beginCategory("system", "System");
      b.beginCategory("sys_datetime", "Date & Time");
        b.toggle("auto_time", "Automatic Date & Time",
                 SettingSource::kGlobal, "auto_time", "1");
        b.toggle("auto_timezone", "Automatic Time Zone",
                 SettingSource::kGlobal, "auto_time_zone", "1");
        b.toggle("time_24hr", "Use 24-Hour Format",
                 SettingSource::kSystem, "time_12_24", "12");
      b.endCategory();

      b.beginCategory("sys_dev_options", "Developer Options");
        b.toggle("stay_awake", "Stay Awake While Charging",
                 SettingSource::kGlobal, "stay_on_while_plugged_in", "0");
        b.toggle("show_touches", "Show Touches",
                 SettingSource::kSystem, "show_touches", "0");
        b.toggle("pointer_location", "Pointer Location",
                 SettingSource::kSystem, "pointer_location", "0");
        b.list("transition_scale", "Transition Animation Scale",
               SettingSource::kGlobal, "transition_animation_scale", "1.0",
               "0:Off,0.5:0.5x,1.0:1x,1.5:1.5x,2.0:2x");
        b.list("window_scale", "Window Animation Scale",
               SettingSource::kGlobal, "window_animation_scale", "1.0",
               "0:Off,0.5:0.5x,1.0:1x,1.5:1.5x,2.0:2x");
        b.list("animator_scale", "Animator Duration Scale",
               SettingSource::kGlobal, "animator_duration_scale", "1.0",
               "0:Off,0.5:0.5x,1.0:1x,1.5:1.5x,2.0:2x");
        b.toggle("usb_debug", "USB Debugging",
                 SettingSource::kGlobal, "adb_enabled", "0");
      b.endCategory();

      b.beginCategory("sys_reset", "Reset Options");
        b.action("reset_wifi_bt", "Reset Wi-Fi & Bluetooth");
        b.action("factory_reset", "Factory Reset");
      b.endCategory();
    b.endCategory();

    // ===== About Device =====
    b.beginCategory("about", "About Device");
      b.info("about_device_name", "Device Name",
             SettingSource::kProp, "ro.product.model", "");
      b.info("about_android_ver", "Android Version",
             SettingSource::kProp, "ro.build.version.release", "");
      b.info("about_build_num", "Build Number",
             SettingSource::kProp, "ro.build.display.id", "");
      b.info("about_lineage_ver", "LineageOS Version",
             SettingSource::kProp, "ro.lineage.version", "");
      b.info("about_kernel", "Kernel Version",
             SettingSource::kProp, "ro.gammaos.kernel.version", "");
      b.info("about_baseband", "Baseband Version",
             SettingSource::kProp, "gsm.version.baseband", "");
    b.endCategory();

    ALOGI("Settings tree built: %zu nodes", mSettingsNodes.size());
}

// ---------------------------------------------------------------------------
// Navigation
// ---------------------------------------------------------------------------

void NanoMenu::settingsTreeGetChildren(int parentIdx,
                                       std::vector<int>& out) const {
    out.clear();
    for (int i = 0; i < (int)mSettingsNodes.size(); i++) {
        if (mSettingsNodes[i].parentIdx == parentIdx) {
            out.push_back(i);
        }
    }
}

void NanoMenu::openSettingsTree() {
    mMenuState = MENU_SETTINGS;
    mSettingsNavStack.clear();
    mSettingsNavStack.push_back(-1);
    settingsTreeGetChildren(-1, mSettingsTreeVisible);
    mSettingsTreeSelected = 0;
    mSettingsTreeScrollTop = 0;
    mSettingsValuesDirty = true;
    mDisplayDirty = true;
    startSettingsValueRefresh();
}

void NanoMenu::settingsTreePushCategory(int nodeIdx) {
    mSettingsNavStack.push_back(nodeIdx);
    settingsTreeGetChildren(nodeIdx, mSettingsTreeVisible);
    mSettingsTreeSelected = 0;
    mSettingsTreeScrollTop = 0;
    mSettingsValuesDirty = true;
    mDisplayDirty = true;
    startSettingsValueRefresh();
}

bool NanoMenu::settingsTreePop() {
    if (mSettingsNavStack.size() <= 1) return false;
    mSettingsNavStack.pop_back();
    int parentIdx = mSettingsNavStack.back();
    settingsTreeGetChildren(parentIdx, mSettingsTreeVisible);
    mSettingsTreeSelected = 0;
    mSettingsTreeScrollTop = 0;
    mSettingsValuesDirty = true;
    mDisplayDirty = true;
    startSettingsValueRefresh();
    return true;
}

std::string NanoMenu::settingsTreeBreadcrumb() const {
    std::string trail;
    for (size_t i = 1; i < mSettingsNavStack.size(); i++) {
        int idx = mSettingsNavStack[i];
        if (idx >= 0 && idx < (int)mSettingsNodes.size()) {
            if (!trail.empty()) trail += " > ";
            trail += trDyn(mSettingsNodes[idx].label.c_str());
        }
    }
    if (trail.empty()) trail = trDyn("Settings");
    return trail;
}

// ---------------------------------------------------------------------------
// Property read / write
// ---------------------------------------------------------------------------

// Shared with the PS3 binding path (declared in NanoMenuSettingsTree.h).
std::string readSettingValue(SettingSource src, const std::string& key,
                             const std::string& def) {
    if (key.empty()) return def;
    switch (src) {
    case SettingSource::kProp: {
        char buf[PROPERTY_VALUE_MAX] = {};
        property_get(key.c_str(), buf, def.c_str());
        return buf;
    }
    case SettingSource::kGlobal:
    case SettingSource::kSecure:
    case SettingSource::kSystem: {
        const char* ns = (src == SettingSource::kGlobal) ? "global"
                       : (src == SettingSource::kSecure) ? "secure" : "system";
        char cmd[256];
        snprintf(cmd, sizeof(cmd), "settings get %s %s 2>/dev/null", ns, key.c_str());
        FILE* f = popen(cmd, "r");
        if (!f) return def;
        char buf[256] = {};
        if (fgets(buf, sizeof(buf), f)) {
            size_t len = strlen(buf);
            while (len > 0 && (buf[len-1] == '\n' || buf[len-1] == '\r'))
                buf[--len] = '\0';
        }
        pclose(f);
        std::string val(buf);
        if (val.empty() || val == "null") return def;
        return val;
    }
    default:
        return def;
    }
}

void writeSettingValue(SettingSource src, const std::string& key,
                       const std::string& val) {
    if (key.empty()) return;
    switch (src) {
    case SettingSource::kProp:
        property_set(key.c_str(), val.c_str());
        break;
    case SettingSource::kGlobal:
    case SettingSource::kSecure:
    case SettingSource::kSystem: {
        const char* ns = (src == SettingSource::kGlobal) ? "global"
                       : (src == SettingSource::kSecure) ? "secure" : "system";
        char cmd[512];
        snprintf(cmd, sizeof(cmd), "settings put %s %s '%s' 2>/dev/null",
                 ns, key.c_str(), val.c_str());
        (void)system(cmd);
        break;
    }
    default:
        break;
    }
    // GammaOS Nano orientation: the Screen Orientation setting is the SINGLE control
    // for accelerometer_rotation (there is no separate Auto-Rotate toggle). "auto"
    // enables the sensor; any fixed orientation disables it so the nano force
    // (WMS mapOrientationRequest) engages. Recursion is safe - the inner write's key
    // is accelerometer_rotation, which does not match this guard.
    if (src == SettingSource::kProp && key == "persist.gammaos.nano.orientation") {
        writeSettingValue(SettingSource::kSystem, "accelerometer_rotation",
                          (val == "auto") ? "1" : "0");
    }
    // The gammapad daemon watches persist.gammaos.gamepad.config_version (it polls
    // it every ~1s) and does a lightweight transform reload when it changes. Every
    // SystemUI gamepad tile bumps it after writing a transform/calibration prop, so
    // mirror that here for ANY gamepad prop write or the change never applies live.
    // Do NOT touch full_reload (that forces a heavy device release/re-grab); the
    // lightweight bump is what the tiles do for the toggle/cycle path.
    if (src == SettingSource::kProp &&
        key.rfind("persist.gammaos.gamepad.", 0) == 0 &&
        key != "persist.gammaos.gamepad.config_version" &&
        key != "persist.gammaos.gamepad.full_reload") {
        char vb[PROPERTY_VALUE_MAX] = "0";
        property_get("persist.gammaos.gamepad.config_version", vb, "0");
        long v = strtol(vb, nullptr, 10);
        char nb[16]; snprintf(nb, sizeof(nb), "%ld", v + 1);
        property_set("persist.gammaos.gamepad.config_version", nb);
    }
}

void NanoMenu::startSettingsValueRefresh() {
    std::vector<int> visible = mSettingsTreeVisible;
    std::vector<SettingNode> nodes = mSettingsNodes;
    std::thread([this, visible, nodes]() {
        std::unordered_map<int, std::string> cache;
        for (int idx : visible) {
            if (idx < 0 || idx >= (int)nodes.size()) continue;
            const auto& n = nodes[idx];
            if (n.source == SettingSource::kNone) continue;
            if (n.type == SettingNodeType::kCategory) continue;
            cache[idx] = readSettingValue(n.source, n.key, n.defaultVal);
        }
        {
            std::lock_guard<std::mutex> lk(mSettingsValueMutex);
            for (auto& kv : cache) mSettingsValueCache[kv.first] = std::move(kv.second);
        }
        mSettingsValuesDirty = false;
        mDisplayDirty = true;
    }).detach();
}

std::string NanoMenu::getSettingsCachedValue(int nodeIdx) const {
    std::lock_guard<std::mutex> lk(mSettingsValueMutex);
    auto it = mSettingsValueCache.find(nodeIdx);
    if (it != mSettingsValueCache.end()) return it->second;
    if (nodeIdx >= 0 && nodeIdx < (int)mSettingsNodes.size())
        return mSettingsNodes[nodeIdx].defaultVal;
    return "";
}

void NanoMenu::settingsToggleValue(int nodeIdx) {
    if (nodeIdx < 0 || nodeIdx >= (int)mSettingsNodes.size()) return;
    const auto& n = mSettingsNodes[nodeIdx];
    std::string cur = getSettingsCachedValue(nodeIdx);
    bool isOn = (cur == "1" || cur == "true");
    std::string newVal;
    if (n.defaultVal == "true" || n.defaultVal == "false") {
        newVal = isOn ? "false" : "true";
    } else {
        newVal = isOn ? "0" : "1";
    }
    writeSettingValue(n.source, n.key, newVal);
    {
        std::lock_guard<std::mutex> lk(mSettingsValueMutex);
        mSettingsValueCache[nodeIdx] = newVal;
    }
    mDisplayDirty = true;
}

void NanoMenu::settingsCycleListValue(int nodeIdx, int direction) {
    if (nodeIdx < 0 || nodeIdx >= (int)mSettingsNodes.size()) return;
    const auto& n = mSettingsNodes[nodeIdx];
    auto opts = parseListOptions(n.options);
    if (opts.empty()) return;
    std::string cur = getSettingsCachedValue(nodeIdx);
    int curIdx = -1;
    for (int i = 0; i < (int)opts.size(); i++) {
        if (opts[i].value == cur) { curIdx = i; break; }
    }
    int newIdx = curIdx + direction;
    if (newIdx < 0) newIdx = (int)opts.size() - 1;
    if (newIdx >= (int)opts.size()) newIdx = 0;
    writeSettingValue(n.source, n.key, opts[newIdx].value);
    {
        std::lock_guard<std::mutex> lk(mSettingsValueMutex);
        mSettingsValueCache[nodeIdx] = opts[newIdx].value;
    }
    if (n.key == "persist.sys.locale") {
        nanoInitLocaleFromSystem();
        buildMenu();
    }
    mDisplayDirty = true;
}

void NanoMenu::settingsSetTextValue(int nodeIdx, const std::string& val) {
    if (nodeIdx < 0 || nodeIdx >= (int)mSettingsNodes.size()) return;
    const auto& n = mSettingsNodes[nodeIdx];
    writeSettingValue(n.source, n.key, val);
    {
        std::lock_guard<std::mutex> lk(mSettingsValueMutex);
        mSettingsValueCache[nodeIdx] = val;
    }
    mDisplayDirty = true;
}

// ---------------------------------------------------------------------------
// Input handling for MENU_SETTINGS
// ---------------------------------------------------------------------------

void NanoMenu::handleSettingsTreeSelect() {
    if (mSettingsTreeVisible.empty()) return;
    if (mSettingsTreeSelected < 0 ||
        mSettingsTreeSelected >= (int)mSettingsTreeVisible.size()) return;

    int nodeIdx = mSettingsTreeVisible[mSettingsTreeSelected];
    if (nodeIdx < 0 || nodeIdx >= (int)mSettingsNodes.size()) return;
    const auto& node = mSettingsNodes[nodeIdx];

    switch (node.type) {
    case SettingNodeType::kCategory:
        settingsTreePushCategory(nodeIdx);
        break;
    case SettingNodeType::kToggle:
        settingsToggleValue(nodeIdx);
        break;
    case SettingNodeType::kList:
        settingsCycleListValue(nodeIdx, 1);
        break;
    case SettingNodeType::kText: {
        mSettingsEditNodeIdx = nodeIdx;
        std::string prompt = node.label;
        std::string cur = getSettingsCachedValue(nodeIdx);
        openOskForPassword(prompt, [this](const std::string& val) {
            if (mSettingsEditNodeIdx >= 0) {
                settingsSetTextValue(mSettingsEditNodeIdx, val);
                mSettingsEditNodeIdx = -1;
            }
        });
        mOskQuery = cur;
        mOskPlaintext = true;
        mOsk.caret = (int)mOskQuery.size();  // caret at end of the pre-seeded text
        break;
    }
    case SettingNodeType::kScreen:
        if (node.screenId == 0) openWifiScreen();
        else if (node.screenId == 1) openBtScreen();
        break;
    case SettingNodeType::kAction:
        if (node.id == "reset_wifi_bt") {
            (void)system("cmd wifi set-wifi-enabled disabled 2>/dev/null");
            (void)system("cmd bluetooth_manager disable 2>/dev/null");
            usleep(500000);
            (void)system("cmd wifi set-wifi-enabled enabled 2>/dev/null");
            (void)system("cmd bluetooth_manager enable 2>/dev/null");
        } else if (node.id == "factory_reset") {
            property_set("sys.gammaos.nano.factory_reset", "1");
        }
        break;
    case SettingNodeType::kInfo:
        break;
    }
}

void NanoMenu::handleSettingsTreeBack() {
    if (!settingsTreePop()) {
        mMenuState = MENU_MAIN;
        mDisplayDirty = true;
    }
}

void NanoMenu::handleSettingsTreeUp() {
    if (mSettingsTreeSelected > 0) mSettingsTreeSelected--;
    mDisplayDirty = true;
}

void NanoMenu::handleSettingsTreeDown() {
    int maxIdx = (int)mSettingsTreeVisible.size() - 1;
    if (mSettingsTreeSelected < maxIdx) mSettingsTreeSelected++;
    mDisplayDirty = true;
}

void NanoMenu::handleSettingsTreeLeft() {
    if (mSettingsTreeVisible.empty()) return;
    if (mSettingsTreeSelected < 0 ||
        mSettingsTreeSelected >= (int)mSettingsTreeVisible.size()) return;
    int nodeIdx = mSettingsTreeVisible[mSettingsTreeSelected];
    if (nodeIdx < 0 || nodeIdx >= (int)mSettingsNodes.size()) return;
    const auto& node = mSettingsNodes[nodeIdx];
    if (node.type == SettingNodeType::kList) {
        settingsCycleListValue(nodeIdx, -1);
    }
}

void NanoMenu::handleSettingsTreeRight() {
    if (mSettingsTreeVisible.empty()) return;
    if (mSettingsTreeSelected < 0 ||
        mSettingsTreeSelected >= (int)mSettingsTreeVisible.size()) return;
    int nodeIdx = mSettingsTreeVisible[mSettingsTreeSelected];
    if (nodeIdx < 0 || nodeIdx >= (int)mSettingsNodes.size()) return;
    const auto& node = mSettingsNodes[nodeIdx];
    if (node.type == SettingNodeType::kList) {
        settingsCycleListValue(nodeIdx, 1);
    } else if (node.type == SettingNodeType::kCategory) {
        settingsTreePushCategory(nodeIdx);
    }
}

} // namespace android
