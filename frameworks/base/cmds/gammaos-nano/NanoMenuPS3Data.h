/*
 * Copyright (C) 2026 GammaOS
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

// 1:1 transcription of the web app DATA[] tree (/work/ps3/xmb-app/index.html
// lines 1133+). Every category, item, submenu, icon index, description and
// value is copied verbatim. The Users / PlayStation Network / Friends
// categories are intentionally excluded (per the port spec). The Game category
// keeps these firmware items; the nano emulator consoles + Recently Played +
// Applications are appended at runtime in buildPs3Cats (the only nano addition).
//
// icon = xmb_icon index NNN; -1 = special (PlayStation Store, no nano asset).
// action: 0 none, 1 dialog, 2 landing.

#ifndef GAMMAOS_NANO_PS3_DATA_H
#define GAMMAOS_NANO_PS3_DATA_H

#include "NanoMenu.h"

namespace android {

#define PS3D  Ps3DataItem
#define PS3CH(arr)  (arr), (int)(sizeof(arr)/sizeof((arr)[0]))

// Set to 1 to hide PS3-firmware / legacy XMB items that are not relevant on this
// GammaOS handheld. The rows stay in source (between the guards, for reference and
// easy re-enable) but are excluded from the binary. PS3CH() recomputes childCount
// from sizeof, so gating an element automatically shrinks its parent list.
#ifndef NANO_XMB_HIDE_LEGACY
#define NANO_XMB_HIDE_LEGACY 1
#endif

// ---- Settings submenus --------------------------------------------------
// Boxart / cover scraper (GammaOS addition). Leaves bind to persist.gammaos.scraper.*
// via kPs3Bindings (matched by label); "Scrape All Systems" is an action leaf.
// Defined before kGameSettingsCh because Game Settings nests it as a sub-category.
static const Ps3DataItem kScraperSettingsCh[] = {
  {"Scraper",22,"Choose the online service used to fetch box art and background art for your games.","ScreenScraper",1,nullptr,0},
  {"Replace Icons with Boxart",22,"Show scraped cover art in place of the generic game icon in the Game menu.","On",1,nullptr,0},
  {"Hover Background Art",22,"Fade the game's background art in behind the menu while it is highlighted.","On",1,nullptr,0},
  {"Scrape Region",22,"Preferred region for box art when a game has more than one regional release.","USA",1,nullptr,0},
  {"Overwrite Existing",22,"Re-download art for games that have already been scraped.","Off",1,nullptr,0},
  {"ScreenScraper Username",22,"Optional ScreenScraper account for higher download limits. Register free at screenscraper.fr.",nullptr,1,nullptr,0},
  {"ScreenScraper Password",22,"Password for the ScreenScraper account above.",nullptr,1,nullptr,0},
  // ScreenScraper developer credentials (ssdevid/ssdevpw) ship as build defaults,
  // like ES-DE, so scraping works out of the box and users never enter them - only
  // the optional account above (ssuser/sspass) for a higher quota. Not in the UI.
  {"TheGamesDB API Key",22,"API key for TheGamesDB. Request one at thegamesdb.net.",nullptr,1,nullptr,0},
  {"Scrape All Systems",22,"Fetch box art and background art for every enabled game system now.",nullptr,1,nullptr,0},
};
// Game Settings now holds the two relevant editors (Game Systems, injected at
// runtime, and Boxart Scraper below); the PSP-Remaster firmware rows are hidden.
static const Ps3DataItem kGameSettingsCh[] = {
#if !NANO_XMB_HIDE_LEGACY
  {"View Mode (PSP Remasters)",22,"Configure the screen size for PSP® Remasters.","Normal",0,nullptr,0},
  {"3D Display (PSP Remasters)",22,"Display PSP® Remasters in 3D.","Off",0,nullptr,0},
  {"Ad Hoc Channel (PSP Remasters)",22,"Set the Ad Hoc Mode channel for PSP® Remasters.",nullptr,0,nullptr,0},
  {"Ad Hoc Mode (PSP Remasters)",22,"Configure Ad Hoc Mode settings for PSP® Remasters.","Off",0,nullptr,0},
  {"PS Upscaler",22,"Apply upscaling to PlayStation® format software.","Off",0,nullptr,0},
  {"PS/PS2 Smoothing",22,"Smooth out the rough edges of images of PlayStation® and PlayStation®2 format software.","Off",0,nullptr,0},
#endif
  {"Rescan Games",8,"Searches your ROM folders again and rebuilds the game list. Games you have deleted are removed, including from Recently Played.",nullptr,1,nullptr,0},
  {"DraStic Data Folder",62,"Point the built-in DS core at your DraStic data folder (saves, config and BIOS). Set this if you moved DraStic's storage to the SD card or a scoped folder, so DS games keep launching and use the same saves. Pick Use Default Folder to reset.","Default",1,nullptr,0},
  {"Boxart Scraper",25,"Downloads box art and background art for your games and replaces the game icons.",nullptr,0,PS3CH(kScraperSettingsCh)},
};
static const Ps3DataItem kVideoSettingsCh[] = {
#if !NANO_XMB_HIDE_LEGACY
  {"BD/DVD Auto-start",22,"Configure BD/DVD auto-start settings.","On",0,nullptr,0},
#endif
  {"IPTV Channels",22,"Show the IPTV live channel browser in the Video menu.","On",1,nullptr,0},
  {"IPTV Playlist URL",22,"Use your own IPTV playlist (m3u/m3u8) URL instead of the built-in Free-TV list. Leave blank for the default.","",1,nullptr,0},
};
static const Ps3DataItem kMusicSettingsCh[] = {
  {"Internet Radio",22,"Show the Internet Radio station browser in the Music menu.","On",1,nullptr,0},
  {"Internet Radio Playlist URL",22,"Use your own radio playlist (m3u/pls) URL instead of the built-in list. Leave blank for the default.","",1,nullptr,0},
};
static const Ps3DataItem kSystemSettingsCh[] = {
#if !NANO_XMB_HIDE_LEGACY
  {"Automatic Update",22,"Starts the system automatically and downloads game patches, uploads new saved data to online storage and syncs trophy information with the server.","Off",0,nullptr,0},
#endif
  {"System Name",22,"Sets the name used to identify this system on the network.","GammaOS",1,nullptr,0},
  {"System Language",22,nullptr,"English (United Kingdom)",0,nullptr,0},
#if !NANO_XMB_HIDE_LEGACY
  {"Character Set",22,nullptr,nullptr,0,nullptr,0},
  {"Dictionary Type",22,nullptr,"English (UK)",0,nullptr,0},
  {"Add/Edit Term",22,nullptr,nullptr,1,nullptr,0},
  {"Delete Predictive Text Dictionary",22,"Deletes words that were added automatically to the dictionary when using the on-screen keyboard.",nullptr,1,nullptr,0},
#endif
#if !NANO_XMB_HIDE_LEGACY
  {"Trophy Notifications",22,nullptr,"Display",0,nullptr,0},
  {"Display [What's New]",22,nullptr,"On",0,nullptr,0},
  {"Disc Auto-Start",22,nullptr,"On",0,nullptr,0},
  {"Control for HDMI",22,nullptr,"Off",0,nullptr,0},
  {"DivX® VOD Registration Code",22,"Displays the registration code that is required to play DivX® VOD content.",nullptr,1,nullptr,0},
  {"Enable ATRAC",22,nullptr,nullptr,0,nullptr,0},
  {"Enable WMA Playback",22,nullptr,nullptr,0,nullptr,0},
  {"Connect PS Vita System Using Network",22,nullptr,"On",0,nullptr,0},
  {"List of Registered PS Vita Systems",22,nullptr,nullptr,1,nullptr,0},
  {"Delete PS Vita System's Backup Files",22,"Deletes backup files for the PS Vita system saved on this system.",nullptr,1,nullptr,0},
#endif
  // Format / Backup / Data Transfer / Restore GammaOS System were PS3 dialogs with no handler
  // behind them: selecting "Format System Storage" or a restore did nothing at all, which is worse
  // than not offering it. Only the reset below is real (it drives the same factory-reset path as
  // the settings tree), alongside the live System Information page.
  {"Storage",22,"Shows how much space is used and free on this system.",nullptr,1,nullptr,0},
  {"Restore Default Settings",22,"Resets this system to its default settings. All apps, saves and settings on internal storage are erased.",nullptr,1,nullptr,0},
  {"System Information",22,nullptr,nullptr,1,nullptr,0},
};
static const Ps3DataItem kThemeSettingsCh[] = {
#if !NANO_XMB_HIDE_LEGACY
  {"Theme",22,"Sets for use of a preset combination of elements such as colour, background or icons.",nullptr,1,nullptr,0},
#endif
  {"Colour",22,"Accent colour for the home screen: the GammaOS XMB (wave and menus), the Minima theme, and the DSi Menu chrome. Leave on Original for each theme's signature colour.",nullptr,1,nullptr,0},
  {"Background",22,"Sets the background of the home screen.",nullptr,1,nullptr,0},
  {"Wallpaper",22,"Sets the moving background effect (wave, particles and others) shown behind the home screen.",nullptr,1,nullptr,0},
  {"Wallpaper Image",22,"Choose a photo from your device to use as the home background. Browse and pick from your images.",nullptr,1,nullptr,0},
  {"Bottom Wallpaper",22,"Choose a separate wallpaper for the bottom screen. Dual-screen devices only (e.g. the RG DS); no effect on a single-screen device.",nullptr,1,nullptr,0},
  {"Video Wallpaper",22,"Choose a video from your device to play as a live background on the top screen. Browse and pick from your videos.",nullptr,1,nullptr,0},
  {"Clear Wallpaper",22,"Removes the custom wallpaper (both screens) and brings the moving wave back.",nullptr,1,nullptr,0},
  {"Background Colour",22,"Minima theme only: fills the Minima home background with a solid colour instead of black. Overrides the wave; a photo or video wallpaper still takes priority.","Black",1,nullptr,0},
  {"Long Names",22,"Minima theme only: how a game or app name too long for its row is shown. Scroll keeps the normal font size, scrolling the focused name and clipping the others at the edge; Shrink to Fit reduces the font so the whole name fits.","Scroll",1,nullptr,0},
  {"Wallpaper Dimming",22,"Darkens a custom photo or video wallpaper so a bright image does not wash out the icons or menu text. Higher = darker.","25%",1,nullptr,0},
  {"XMB Wave",22,"Shows the moving PS3 wave behind the home screen. Off by default when a custom wallpaper is set.","On",1,nullptr,0},
  {"Font",22,"Sets the font displayed on the home screen.",nullptr,1,nullptr,0},
  {"Day/Night",22,"Sets the time-of-day lighting (day, dusk, night or automatic).",nullptr,1,nullptr,0},
  {"Home Theme",22,"Switches the home screen between GammaOS XMB, DSi Menu and Minima. Applying restarts the home screen.","GammaOS XMB",1,nullptr,0},
  {"DSi Dark Theme",22,"DSi Menu theme only: switches the DSi home to a dark variant (dark field, light text and icons) for low-light use. Applies immediately.","Off",1,nullptr,0},
  {"Navigation Sounds",22,"Plays the UI sound effects (cursor, select, back, launch) as you move through the home menu. Turn off for silent navigation in any theme. The boot sound is not affected.","On",1,nullptr,0},
  {"Bottom Clock",73,"Shows a PSP-style analog clock on the bottom screen. Dual-screen devices only (e.g. the RG DS); no effect on a single-screen device.","On",1,nullptr,0},
  {"Bottom Clock FPS",73,"Frame rate of the bottom-screen clock. 30 FPS saves power; 60 FPS is smoother.","30 FPS",1,nullptr,0},
  {"Dual Screen",73,"DSi theme only: on a tall single screen, show both DS screens stacked (top + bottom) or just the bottom launcher. Auto stacks on a tall (portrait) screen. Real dual-screen devices always use both panels.","Auto",1,nullptr,0},
  {"Screen Gap",73,"DSi theme only: the gap between the stacked top and bottom screens (the DS hinge). Larger values push each screen toward its edge (snap to top/bottom).","Medium",1,nullptr,0},
};
static const Ps3DataItem kDateTimeCh[] = {
  {"Date and Time",22,"Sets the date and time for this system.",nullptr,1,nullptr,0},
  {"Date Format",22,"Sets the order of display for year, month and day.","DD/MM/YYYY",1,nullptr,0},
  {"Time Format",22,"Sets the time display to either a 12-hour or 24-hour clock.","24-Hour Clock",1,nullptr,0},
  {"Time Zone",22,nullptr,"GMT",0,nullptr,0},
  {"Daylight Saving",22,"Sets for daylight saving time.","Off",1,nullptr,0},
  {"Set via Internet",22,"Obtains the correct date and time automatically via the Internet, and sets them on your system.",nullptr,1,nullptr,0},
  {"Set Manually",22,nullptr,nullptr,1,nullptr,0},
};
static const Ps3DataItem kPowerSaveCh[] = {
  // "System Auto-Off" was an unbound PS3 row, and a sleep-after-screen-off row is deliberately
  // not offered in its place: the platform folds that setting into the screen-off timeout, so it
  // would silently override the Screen Timeout in Display Settings. That one is the real control.
#if !NANO_XMB_HIDE_LEGACY
  {"Controller Auto-Off",22,"Sets whether or not to automatically turn off controllers. If you do not use a controller for a set amount of time, it will turn off automatically.","After 10 min.",0,nullptr,0},
  {"Power Indicator",22,nullptr,"Bright",0,nullptr,0},
  {"Turn Off System Automatically After Background Download",22,nullptr,"Off",0,nullptr,0},
  {"Battery Percentage",22,"Show the battery charge percentage in the status bar.","Off",1,nullptr,0},
#endif
  {"Battery Saver",22,"Reduce power usage to extend battery life.","Off",1,nullptr,0},
};
static const Ps3DataItem kAccessoryCh[] = {
#if !NANO_XMB_HIDE_LEGACY
  {"Calibrate Motion Controller",22,"Calibrates the magnetic sensor of a motion controller. Use this setting when the motion controller does not control on-screen movement as expected.",nullptr,1,nullptr,0},
  {"Reassign Controllers",22,"Change the number assigned to the controller that is currently in use.",nullptr,1,nullptr,0},
#endif
  // "Controller Vibration Function" was unbound here and duplicated the working PWM rumble rows in
  // Gamepad Settings, so it is retired rather than shown twice.
#if !NANO_XMB_HIDE_LEGACY
  {"BD Remote Control Registration",22,"Register the BD Remote Control.",nullptr,1,nullptr,0},
#endif
  {"Manage Bluetooth® Devices",22,"Manage Bluetooth® devices.",nullptr,1,nullptr,0},
#if !NANO_XMB_HIDE_LEGACY
  {"Camera Device Settings",22,"Tests the image from a camera that is connected to the system using a USB cable. You can adjust settings to reduce flickering for some cameras.",nullptr,1,nullptr,0},
  {"Audio Device Settings",22,"Sets the audio input and output devices for voice/video chat and other communication features.",nullptr,1,nullptr,0},
  {"Stereo Headset Audio Extension",22,"Sets whether or not to output all audio to the headset.","Off",0,nullptr,0},
  {"Voice Changer",22,"You can change the voice that is input using an audio input device.",nullptr,0,nullptr,0},
  {"Keyboard Type",22,"Select the keyboard type.","English (US)",0,nullptr,0},
  {"Keyboard Entry Method",22,"Sets the text entry method for a connected keyboard.",nullptr,0,nullptr,0},
  {"Key Repeat Delay",22,"Sets the delay before key repeat starts.",nullptr,0,nullptr,0},
  {"Key Repeat Rate",22,"Sets the rate at which a held key repeats.",nullptr,0,nullptr,0},
  {"Mouse Type",22,"Sets the mouse type. Set this option based on which hand you use to operate the mouse.","Right-handed",0,nullptr,0},
  {"Pointer Speed",22,"Sets the speed at which the mouse pointer moves. The mouse pointer is displayed in the Internet browser and in games and other software that support use of a mouse.","Normal",0,nullptr,0},
#endif
};
// LiveDisplay (LineageSettings.System, applied live by LiveDisplayService). Only the features
// this hardware actually reports are offered: colour temperature (via night display), RGB colour
// calibration and reading mode. CABC, auto contrast, colour enhancement, picture adjustment,
// anti-flicker, display modes and outdoor mode need a vendor HAL that is not present, so they are
// deliberately NOT listed rather than shown as rows that do nothing. Saturation rides the AOSP
// colour matrix (cmd color_display set-saturation) and is re-applied by nano on boot.
// The panel calibration is stored by LiveDisplay as a single "R G B" string, so each channel gets
// its own percentage row here and nano recomposes the three into that setting on any change
// (see writeSettingValue). 100% is the untouched panel output.
static const Ps3DataItem kColorCalCh[] = {
  {"Red",16,"Red level of the panel.","100",0,nullptr,0},
  {"Green",16,"Green level of the panel.","100",0,nullptr,0},
  {"Blue",16,"Blue level of the panel.","100",0,nullptr,0},
};
static const Ps3DataItem kLiveDisplayCh[] = {
  // Colour temperature is Night Light's job here, not LiveDisplay's: LiveDisplay switches its own
  // temperature control off whenever the system provides night display
  // (ColorTemperatureController: mUseTemperatureAdjustment = !mNightDisplayAvailable && ...),
  // which this system does. Offering LiveDisplay's day/night temperature rows would therefore have
  // shown settings that never apply.
  {"Night Light",16,"Tints the screen warmer so it is easier on the eyes in the dark.","Off",0,nullptr,0},
  {"Night Light Temperature",16,"How warm the screen becomes while Night Light is on. Lower is warmer.","2850",0,nullptr,0},
  {"Colour Calibration",16,"Fine-tunes the red, green and blue levels of the panel.",nullptr,0,PS3CH(kColorCalCh)},
  {"Saturation",16,"Adjusts how vivid colours are across the whole screen. 0 is greyscale, 100 is normal.","100",0,nullptr,0},
  {"Reading Mode",16,"Drains the colour out of the screen for comfortable reading.","Off",0,nullptr,0},
};
static const Ps3DataItem kDisplayCh[] = {
  // The PS3 firmware video-output rows (connector chooser, SCART cross-colour filter, 50 Hz,
  // RGB range, Super-White, Deep Colour, 1080p24, BD/DVD colour format, HDMI control) were all
  // unbound decoration: none of them touched anything on Android, and most describe hardware this
  // system does not have. Replaced with the display settings that do work here.
  // No Adaptive Brightness row: this hardware has no ambient light sensor, so it could never do
  // anything. Brightness itself is nano's own backlight level (it drives the panel directly and
  // only mirrors the value into Settings), so the row is bound to that rather than to
  // Settings.System screen_brightness, which nano writes but never reads back.
  {"Brightness",22,"Sets the screen brightness.","128",1,nullptr,0},
  {"LiveDisplay",16,"Adjusts the colour of the screen: colour temperature, colour calibration, saturation and reading mode.",nullptr,0,PS3CH(kLiveDisplayCh)},
  {"Screen Saver",22,"Shows a screen saver while the system is idle and charging.","On",1,nullptr,0},
  {"Screen Timeout",22,"Sets how long the screen stays on while idle.","1 minute",1,nullptr,0},
  {"Font Size",22,"Sets the size of text shown on the screen.","Default",1,nullptr,0},
  {"Dark Theme",22,"Use a dark colour scheme across the system.","On",1,nullptr,0},
  {"Screen Orientation",70,"Sets the orientation of the home menu and apps. Choose Auto to follow the accelerometer, or lock to a fixed orientation.","Landscape",1,nullptr,0},
  {"Force Orientation",22,"When on, every app is forced to this orientation and cannot override it. Off keeps each app's own orientation and any per-app override.","Off",1,nullptr,0},
};
// Sound: the PS3 firmware rows (connector chooser, multi-output, remote-play device, BD/DVD/HDD
// audio languages, BD output format) were decorative leftovers of the 1:1 port - none of them was
// bound to anything on Android, and the disc-format ones have no meaning on this hardware at all.
// What remains is backed by real Android settings, plus the media volume nano already owns.
static const Ps3DataItem kSoundCh[] = {
  {"Touch Sounds",22,"Play a sound when you make a selection on the screen.","On",1,nullptr,0},
  {"Charging Sounds",22,"Play a sound when the charger is connected.","On",1,nullptr,0},
  {"Screen Lock Sounds",22,"Play a sound when the screen locks or unlocks.","On",1,nullptr,0},
};
// kSecurityCh / kRemotePlayCh are only referenced by their (now hidden) top-level
// entries; guard the definitions too so they are not unused under -Werror.
#if !NANO_XMB_HIDE_LEGACY
static const Ps3DataItem kSecurityCh[] = {
  {"Change Password",22,nullptr,nullptr,1,nullptr,0},
  {"Parental Control",22,nullptr,"Off",0,nullptr,0},
  {"Internet Browser Start Control",22,nullptr,"Off",0,nullptr,0},
  {"BD Parental Control",22,nullptr,"Off",0,nullptr,0},
  {"DVD Parental Control",22,nullptr,"Off",0,nullptr,0},
};
static const Ps3DataItem kRemotePlayCh[] = {
  {"Remote Start",22,"Sets whether or not to turn on this system automatically when a registered device is connected for remote play.","Off",0,nullptr,0},
  {"Register Device",22,"Register a remote play device with this system.",nullptr,1,nullptr,0},
  {"Status of Registered Devices",22,nullptr,nullptr,1,nullptr,0},
  {"Delete Registered Device",22,nullptr,nullptr,1,nullptr,0},
};
#endif
static const Ps3DataItem kNetworkSettingsCh[] = {
  {"Settings and Connection Status List",22,"Displays current network settings and connection status.",nullptr,1,nullptr,0},
  {"Internet Connection",22,"Sets whether or not to connect this system to the Internet. Select this option if you want to temporarily disable the Internet connection.","Enabled",0,nullptr,0},
  {"Internet Connection Settings",22,"Sets the method for connecting the system to the Internet. Select this option to connect to a wireless LAN or to change the settings.",nullptr,1,nullptr,0},
  {"Internet Connection Test",22,"Tests the Internet connection and displays the results.",nullptr,1,nullptr,0},
  // "Media Server Connection" (DLNA discovery) was an unbound PS3 row with no backend here.
};
static const Ps3DataItem kDevOptionsCh[] = {
  {"USB Debugging",22,"Enable debug mode when a USB device is connected.","Off",1,nullptr,0},
  {"Stay Awake While Charging",22,"The screen will never sleep while charging.","Off",1,nullptr,0},
  {"Show Touches",22,"Show visual feedback for taps on the screen.","Off",1,nullptr,0},
  {"Pointer Location",22,"Show the pointer position and touch data on screen.","Off",1,nullptr,0},
  {"Transition Animation Scale",22,"Adjusts the speed of screen transition animations.","1x",1,nullptr,0},
  {"Window Animation Scale",22,"Adjusts the speed of window animations.","1x",1,nullptr,0},
  {"Animator Duration Scale",22,"Adjusts the speed of animations.","1x",1,nullptr,0},
};
static const Ps3DataItem kGamepadCh[] = {
  {"Controller Enable",22,"Enable the GammaOS gamepad input layer.","Off",1,nullptr,0},
  {"Merge Controllers",22,"Combine all connected controllers into one virtual gamepad.","On",1,nullptr,0},
  {"Hide Source Device",22,"Hide the original controller device from apps.","On",1,nullptr,0},
  {"ABXY Swap",22,"Swap the A/B and X/Y face buttons.","Off",1,nullptr,0},
  {"Invert Left Stick",22,"Invert the left analog stick axes.","Off",1,nullptr,0},
  {"Invert Right Stick",22,"Invert the right analog stick axes.","Off",1,nullptr,0},
  {"Analog to D-Pad",22,"Map the analog stick to the D-Pad.","Off",1,nullptr,0},
  {"D-Pad to Analog",22,"Map the D-Pad to the analog stick.","Off",1,nullptr,0},
  {"Global Sensitivity",22,"Adjust analog stick sensitivity for all controllers.","Off",1,nullptr,0},
  {"PWM Enable",22,"Enable PWM rumble output for controllers.","On",1,nullptr,0},
  {"PWM Intensity",22,"Set the strength of the PWM rumble output.","255",1,nullptr,0},
  {"D-Pad Threshold",22,"Set how far the stick must move to register as a D-Pad press.","50",1,nullptr,0},
  {"Screen Map",22,"Enable touchscreen mapping for controllers.","Off",1,nullptr,0},
};
// Slide Behaviour: the hardware swivel/slide sensor. Each leaf is a bound value row
// (makeDataItem binds by label to kPs3Bindings); the OSK-editable ones use action=1.
// Mirrors the TvSettings "Slide behaviour" screen so the same config is in the XMB too.
static const Ps3DataItem kSlideCh[] = {
  {"Slide Enable",70,"React to the configured slide button.","On",0,nullptr,0},
  {"Slide Device",5,"Choose the input device that reports the slide (drills into a picker).","gpio-keys",0,nullptr,0},
  {"Slide Button Code",5,"Choose the slide event from the ones that device supports (drills into a picker).","88",0,nullptr,0},
  {"Slide Event Type",70,"evdev event type of the slide trigger (Key or Switch).","Key (EV_KEY)",0,nullptr,0},
  {"Slide Active Value",70,"Which event value means 'engaged' (High for most, Low for an inverted switch).","High (1)",0,nullptr,0},
  {"On Slide Down",70,"Actions when the button goes down (slid). Pick one or more (rotate, sleep, wake, launch, PSP clock); none = do nothing.","Rotate",0,nullptr,0},
  {"On Slide Up",70,"Actions when the button releases (returned). Pick one or more; none = do nothing.","Restore Natural",0,nullptr,0},
  {"Sleep Delay",70,"How long to wait before the screen sleeps for the Sleep action.","Immediate",0,nullptr,0},
  {"Rotation Angle",70,"Angle used by the Rotate action.","90 degrees",0,nullptr,0},
  {"Slide Launch Target",74,"For the Launch action: a package, component, or nano:<mode>.",nullptr,1,nullptr,0},
  {"Show Clock On Slide",73,"Show the PSP clock overlay while the button is slid.","On",0,nullptr,0},
  {"Clock Live Backdrop",73,"Refract and blur the running game behind the clock.","On",0,nullptr,0},
  {"Parallax Calibration",73,"gain,rot,sx,sy - gyro parallax throw, orientation and per-axis sign.",nullptr,1,nullptr,0},
};
// kMouseCh (data-driven Mouse Mode) is retired: Mouse Mode now opens the code-built
// buildMouseSubmenu via QA_MOUSE_MENU under Gamepad Settings. Guard so it is not unused.
#if !NANO_XMB_HIDE_LEGACY
static const Ps3DataItem kMouseCh[] = {
  {"Stick Speed",22,"Set the mouse pointer speed when using the analog stick.","12",1,nullptr,0},
  {"D-Pad Speed",22,"Set the mouse pointer speed when using the D-Pad.","6",1,nullptr,0},
  {"Boost",22,"Set the pointer speed multiplier when the boost button is held.","2x",1,nullptr,0},
  {"Scroll Speed",22,"Set the scroll-wheel speed in mouse mode.","4",1,nullptr,0},
};
#endif
static const Ps3DataItem kToolboxCh[] = {
  {"Immersive Mode",22,"Hide the status and navigation bars for a fullscreen experience.","Off",1,nullptr,0},
  {"Refresh Rate Lock",22,"Lock the display to a fixed refresh rate.","Off",1,nullptr,0},
  {"Display Tweaks",22,"Enable additional display tuning options.","Off",1,nullptr,0},
  {"Force Client Composition",22,"Force GPU composition of all display layers.","Off",1,nullptr,0},
  {"Desktop Fullscreen",22,"Run desktop applications in fullscreen.","Off",1,nullptr,0},
  {"Multi-Volume",22,"Use independent volume control per audio output.","Off",1,nullptr,0},
  {"Ultra Low Power Saving",22,"Aggressively reduce power usage to extend battery life.","Off",1,nullptr,0},
  {"Virtual Memory",22,"Set the swap file size used to extend RAM. Larger values let more apps stay open but use more storage.","Off",1,nullptr,0},
  {"RetroArch Back Button Override",22,"Override the back button behaviour inside RetroArch.","Off",1,nullptr,0},
  {"Start+Select LED",22,"Flash the LED when Start and Select are pressed together.","Off",1,nullptr,0},
  {"Scan ROM Subfolders",22,"Also scan folders inside each system's ROM directory (recursively). Turn off to scan only the top level.","Off",1,nullptr,0},
  {"Group Multi-Disc (.m3u)",22,"Show a single entry for multi-disc games listed in an .m3u playlist and hide the individual disc files.","On",1,nullptr,0},
  {"USB Controller Switch",22,"Switch the USB port between host and device mode for controllers.","Off",1,nullptr,0},
  {"DC Dimming Emulation",22,"Emulate DC dimming to reduce screen flicker at low brightness.","Off",1,nullptr,0},
  {"Phone Taskbar",22,"Show the phone-style taskbar.","On",1,nullptr,0},
  {"Dual Taskbar",22,"Show the taskbar on both displays.","Off",1,nullptr,0},
  {"Black Frame Insertion",22,"Insert black frames to reduce motion blur (BFI).","Off",1,nullptr,0},
  {"CRT Shader",22,"Apply a CRT-style display shader.","Off",1,nullptr,0},
  {"Dual-Stack Display",22,"Enable the dual-stack display compositor.","Off",1,nullptr,0},
  {"RGB LED",22,"Enable RGB LED lighting effects.","Off",1,nullptr,0},
  {"Launch Guard",22,"Guard against unintended application launches.","Off",1,nullptr,0},
  {"Widevine L3 Compatibility Mode",22,"Request L3 DRM licenses so streaming apps that reject uncertified devices can play. Video quality may be limited.","On",1,nullptr,0},
};

// ---- GammaRGB (persist.gammaos.rgb.* + persist.gammargb.control) ---------
// Mirrors the JoystickLedPicker app: the Effect chooser drives gammargb.control
// (Off) plus rgb.effect (Follow Screen / Solid Colour / numbered effects 1-5 that
// are gated by rgb.effectN.supported); LED Colour is the solid colour for the
// Solid effect (primary.rgb_hex_custom). The sampler polls all of these live.
static const Ps3DataItem kGammaRgbCh[] = {
  {"Effect",22,"Choose the LED lighting mode: off, follow the screen, a solid colour or a numbered effect.","Follow Screen",1,nullptr,0},
  {"LED Colour",22,"The solid LED colour used by the Solid effect.","-",1,nullptr,0},
  {"LED Brightness",22,"Overall brightness of the RGB LEDs.","255",1,nullptr,0},
  {"Scale with Brightness",22,"Scale the LED brightness together with the display brightness.","On",1,nullptr,0},
  {"Effect Speed",22,"Animation speed for the numbered lighting effects.","10",1,nullptr,0},
  {"Saturation Boost",22,"Boost the colour saturation of the LEDs.","1.4",1,nullptr,0},
  {"Fade Enable",22,"Smoothly fade between colours instead of switching instantly.","On",1,nullptr,0},
  {"Fade FPS",22,"Frame rate used for the colour fade animation.","60",1,nullptr,0},
  {"Sampling FPS",22,"How often the screen is sampled for the follow-screen effect.","6",1,nullptr,0},
  {"Pre-FX Sampling",22,"Sample the screen before post-processing effects are applied.","On",1,nullptr,0},
  {"Split LEDs",22,"Use separate left and right LED zones (if supported by the hardware).","Off",1,nullptr,0},
  {"Split Colours",22,"Give the left and right LED zones independent colours.","Off",1,nullptr,0},
  {"Left Colour",22,"Solid colour for the left LED zone when Split Colours is on.","-",1,nullptr,0},
  {"Right Colour",22,"Solid colour for the right LED zone when Split Colours is on.","-",1,nullptr,0},
};

// ---- GammaEQ module submenus (persist.sys.spk.*) ------------------------
// Ranges mirror the GammaEQ app sliders; every write bumps the .seq props so the
// FastMixer re-reads immediately (see ps3BumpEqSeqs in NanoMenuPS3Menu.cpp).
static const Ps3DataItem kEqCrystCh[] = {
  {"Crystalizer",22,"Enhance audio clarity and dynamics.","Off",1,nullptr,0},
  {"Cryst Amount",22,"Strength of the crystalizer enhancement.","0.5",1,nullptr,0},
  {"Cryst Mix",22,"Blend between the original and the crystalized audio.","1.0",1,nullptr,0},
  {"Cryst Frequency",22,"Centre frequency of the crystalizer band.","11500",1,nullptr,0},
  {"Cryst Limit",22,"Output limiter ceiling for the crystalizer.","0.30",1,nullptr,0},
  {"Cryst Pre-gain",22,"Gain applied before the crystalizer.","0",1,nullptr,0},
  {"Cryst Post-gain",22,"Gain applied after the crystalizer.","0",1,nullptr,0},
};
static const Ps3DataItem kEqLbpCh[] = {
  {"Bass Limiter",22,"Limit excessive bass to protect the speakers.","Off",1,nullptr,0},
  {"Bass Threshold",22,"Level at which the bass limiter engages.","0.69",1,nullptr,0},
  {"Bass Attack",22,"How quickly the bass limiter responds (ms).","4",1,nullptr,0},
  {"Bass Release",22,"How quickly the bass limiter recovers (ms).","110",1,nullptr,0},
  {"Bass Frequency",22,"Crossover frequency for the bass limiter (Hz).","160",1,nullptr,0},
};
static const Ps3DataItem kEqMpCh[] = {
  {"Mid Protector",22,"Protect the midrange from distortion.","Off",1,nullptr,0},
  {"Mid High-Pass",22,"High-pass corner for the midrange protector (Hz).","220",1,nullptr,0},
  {"Mid Low-Pass",22,"Low-pass corner for the midrange protector (Hz).","5800",1,nullptr,0},
  {"Mid Threshold",22,"Level at which the midrange protector engages.","0.92",1,nullptr,0},
  {"Mid Attack",22,"How quickly the midrange protector responds (ms).","2",1,nullptr,0},
  {"Mid Release",22,"How quickly the midrange protector recovers (ms).","120",1,nullptr,0},
};
static const Ps3DataItem kEqWideCh[] = {
  {"Stereo Widener",22,"Widen the stereo image.","Off",1,nullptr,0},
  {"Widener Amount",22,"Strength of the stereo widening.","1.0",1,nullptr,0},
  {"Widener Mix",22,"Blend between the original and the widened audio.","0.35",1,nullptr,0},
  {"Widener Pre-gain",22,"Gain applied before the widener.","0",1,nullptr,0},
  {"Widener Limit",22,"Output limiter ceiling for the widener.","0.5",1,nullptr,0},
  {"Widener High-Pass",22,"High-pass corner for the widener (Hz).","500",1,nullptr,0},
  {"Widener Centre",22,"Centre frequency for the widener (Hz).","2000",1,nullptr,0},
};
static const Ps3DataItem kEqPeq1Ch[] = {
  {"Parametric EQ 1",22,"Enable the first parametric EQ band.","Off",1,nullptr,0},
  {"PEQ1 Band 0",22,"First parametric EQ b0 coefficient.","1.0",1,nullptr,0},
  {"PEQ1 Band 1",22,"First parametric EQ b1 coefficient.","0",1,nullptr,0},
  {"PEQ1 Band 2",22,"First parametric EQ b2 coefficient.","0",1,nullptr,0},
};
static const Ps3DataItem kEqPeq2Ch[] = {
  {"Parametric EQ 2",22,"Enable the second parametric EQ band.","Off",1,nullptr,0},
  {"PEQ2 Band 0",22,"Second parametric EQ b0 coefficient.","1.0",1,nullptr,0},
  {"PEQ2 Band 1",22,"Second parametric EQ b1 coefficient.","0",1,nullptr,0},
  {"PEQ2 Band 2",22,"Second parametric EQ b2 coefficient.","0",1,nullptr,0},
};

// ---- GammaEQ (persist.sys.gammaeq.* master + module submenus) -----------
static const Ps3DataItem kGammaEqCh[] = {
  {"Enable EQ",22,"Enable the GammaEQ equalizer and speaker enhancements.","Off",1,nullptr,0},
  {"Speaker Only",22,"Apply the equalizer only to the built-in speakers, not headphones.","On",1,nullptr,0},
  {"Audio Preview",22,"Play a looping sample so you can hear the equalizer while you adjust it.","Off",1,nullptr,0},
  {"Preamp (dB)",22,"Input gain applied before the equalizer.","0",1,nullptr,0},
  {"Postgain (dB)",22,"Output gain applied after the equalizer.","0",1,nullptr,0},
  {"Crystalizer",22,"Clarity and dynamics enhancement.",nullptr,0,PS3CH(kEqCrystCh)},
  {"Bass Limiter",22,"Bass protection and limiting.",nullptr,0,PS3CH(kEqLbpCh)},
  {"Mid Protector",22,"Midrange distortion protection.",nullptr,0,PS3CH(kEqMpCh)},
  {"Stereo Widener",22,"Stereo image widening.",nullptr,0,PS3CH(kEqWideCh)},
  {"Parametric EQ 1",22,"First parametric EQ band.",nullptr,0,PS3CH(kEqPeq1Ch)},
  {"Parametric EQ 2",22,"Second parametric EQ band.",nullptr,0,PS3CH(kEqPeq2Ch)},
};

// ---- Settings top-level items -------------------------------------------
static const Ps3DataItem kSettingsItems[] = {
  {"User Guide",22,"How the launcher works: themes, accent colour, hiding game systems, box art scraping, multi-disc games and where saves go.",nullptr,1,nullptr,0},
  {"System Update",8,"Update the GammaOS system software.",nullptr,1,nullptr,0},
  {"Game Settings",5,"Adjusts settings for games.",nullptr,0,PS3CH(kGameSettingsCh)},
  // Boxart Scraper moved under Game Settings (kGameSettingsCh); the top-level entry is retired.
#if !NANO_XMB_HIDE_LEGACY
  {"Boxart Scraper",25,"Downloads box art and background art for your games and replaces the game icons.",nullptr,0,PS3CH(kScraperSettingsCh)},
#endif
  {"Video Settings",9,"Adjusts settings for video.",nullptr,0,PS3CH(kVideoSettingsCh)},
  {"Music Settings",3,"Adjusts settings for music.",nullptr,0,PS3CH(kMusicSettingsCh)},
#if !NANO_XMB_HIDE_LEGACY
  {"Chat Settings",42,"Adjusts settings for chat.",nullptr,0,nullptr,0},
#endif
  {"System Settings",74,"Adjusts settings for this GammaOS system.",nullptr,0,PS3CH(kSystemSettingsCh)},
  {"Developer Options",78,"Adjusts advanced settings for software developers.",nullptr,0,PS3CH(kDevOptionsCh)},
  {"Theme Settings",79,"Adjusts settings related to the appearance of the home screen.",nullptr,0,PS3CH(kThemeSettingsCh)},
  {"Date and Time Settings",14,"Adjusts date and time settings.",nullptr,0,PS3CH(kDateTimeCh)},
  {"Power Save Settings",27,"Adjusts settings to reduce power usage by this system.",nullptr,0,PS3CH(kPowerSaveCh)},
  {"Accessory Settings",76,"Adjusts settings for accessories that are connected to this system.",nullptr,0,PS3CH(kAccessoryCh)},
  // Gamepad Settings opens the rich buildGamepadSubmenu (dispatch special-case in
  // ps3XmbSelect); Mouse Mode is now nested inside it, so the top-level entry is retired.
  {"Gamepad Settings",5,"Adjusts settings for game controllers.",nullptr,0,PS3CH(kGamepadCh)},
  {"Slide Behaviour",70,"Configure the swivel/slide sensor: the input device and button to watch, and the slide down/up actions (rotate, sleep, wake, launch, or the PSP clock).",nullptr,0,PS3CH(kSlideCh)},
#if !NANO_XMB_HIDE_LEGACY
  {"Mouse Mode",15,"Adjusts mouse-mode pointer settings for controllers.",nullptr,0,PS3CH(kMouseCh)},
#endif
  {"GammaOS Toolbox",1,"Adjusts GammaOS-specific tweaks and enhancements.",nullptr,0,PS3CH(kToolboxCh)},
  {"File Explorer",62,"Browse the file system and copy, move, rename or delete files and folders.",nullptr,0,nullptr,0},
  {"Network Shares",6,"Connect to shared folders on your network over SMB, NFS, WebDAV or FTP. A connected share can be browsed in the File Explorer and added to your photo, music and video libraries.",nullptr,0,nullptr,0},
  {"GammaRGB",80,"Adjusts the RGB LED lighting effects.",nullptr,0,PS3CH(kGammaRgbCh)},
  {"GammaEQ",17,"Adjusts the audio equalizer and speaker enhancements.",nullptr,0,PS3CH(kGammaEqCh)},
#if !NANO_XMB_HIDE_LEGACY
  {"Printer Settings",10,"Adjusts settings for printers that are connected to this system.",nullptr,0,nullptr,0},
#endif
  {"Display Settings",16,"Adjusts settings for video output.",nullptr,0,PS3CH(kDisplayCh)},
  {"Sound Settings",17,"Adjusts settings for audio output.",nullptr,0,PS3CH(kSoundCh)},
#if !NANO_XMB_HIDE_LEGACY
  {"Security Settings",18,"Adjusts parental control settings.",nullptr,0,PS3CH(kSecurityCh)},
  {"Remote Play Settings",20,"Adjusts settings for remote play. Remote play enables you to use a device that supports the remote play feature (such as a PSP™ system) to operate this system over a network.",nullptr,0,PS3CH(kRemotePlayCh)},
#endif
  {"Network Settings",6,"Adjusts settings for the Internet connection.",nullptr,0,PS3CH(kNetworkSettingsCh)},
};

// ---- Photo --------------------------------------------------------------
static const Ps3DataItem kPhotoItems[] = {
  {"Search for Media Servers",35,"Scans the network and connects to a media server. To use this function, a media server must be set up to allow connections from the GammaOS system.",nullptr,0,nullptr,0},
#if !NANO_XMB_HIDE_LEGACY
  {"Photo Gallery",64,"",nullptr,0,nullptr,0},   // no subtitle: the cinfo hover overlay provides the description (1:1 with the web, whose Photo Gallery item has no description)
#endif
  {"Playlists",37,nullptr,nullptr,0,nullptr,0},
};

// ---- Music --------------------------------------------------------------
static const Ps3DataItem kMusicItems[] = {
  {"Internet Radio",3,"Browse and listen to free live Internet radio stations from a community station list. Streams play over the Internet and are not hosted or verified by GammaOS.",nullptr,0,nullptr,0},
  {"Search for Media Servers",35,"Scans the network and connects to a media server. To use this function, a media server must be set up to allow connections from the GammaOS system.",nullptr,0,nullptr,0},
  {"Playlists",37,nullptr,nullptr,0,nullptr,0},
};

// ---- Video --------------------------------------------------------------
static const Ps3DataItem kVideoItems[] = {
  {"IPTV",4,"Browse and watch free live IPTV channels from the community Free-TV/IPTV project. Channels are streamed over the Internet and are not hosted or verified by GammaOS.",nullptr,0,nullptr,0},
  {"Search for Media Servers",35,"Scans the network and connects to a media server. To use this function, a media server must be set up to allow connections from the GammaOS system.",nullptr,0,nullptr,0},
#if !NANO_XMB_HIDE_LEGACY
  {"Video Editor & Uploader",67,"You can edit a video that you like, upload it to a video sharing website, and then invite your friends to view the video.",nullptr,0,nullptr,0},
#endif
  {"Playlists",37,nullptr,nullptr,0,nullptr,0},
};

// ---- Game (PS3 firmware items; nano consoles appended at runtime) --------
// These firmware utilities appear in the Game column below the emulator systems.
// Hidden on this handheld (the Game column is then just the nano consoles /
// Recently Played / Applications, prepended at runtime in buildPs3Cats).
#if !NANO_XMB_HIDE_LEGACY
static const Ps3DataItem kMemCardCh[] = {
  {"Internal Memory Card",64,"Internal memory card for PS/PS2 saved data.",nullptr,0,nullptr,0},
  {"Create New Internal Memory Card",64,"Create a new internal memory card.",nullptr,0,nullptr,0},
};
static const Ps3DataItem kGameItems[] = {
  {"PS Vita System Application Utility",62,"*User",nullptr,0,nullptr,0},
  {"Game Data Utility",62,"Manages game data installed on the GammaOS system. To delete a game data item, select it and then press the @T button.",nullptr,0,nullptr,0},
  {"Memory Card Utility (PS/PS2)",64,"Manages internal memory cards for use with PlayStation® and PlayStation®2 format software.",nullptr,0,PS3CH(kMemCardCh)},
  {"Saved Data Utility (PS2)",63,"*User",nullptr,0,nullptr,0},
  {"Saved Data Utility (minis/PSP™)",66,"*User",nullptr,0,nullptr,0},
  {"Saved Data Utility (GammaOS)",63,"*User",nullptr,0,nullptr,0},
  {"PlayStation®Store",-1,"FREE* to access, PlayStation®Store is the only place to download new and exclusive GammaOS games, FREE playable demos, add-on packs, and high-definition videos.\n\nGet more for your GammaOS and visit PlayStation®Store today.\n\n* Broadband Internet connection required.",nullptr,2,nullptr,0},
  {"Software Instruction Manuals",30,"Displays manuals for the software installed on the GammaOS system.",nullptr,0,nullptr,0},
  {"Corrupted Data",22,nullptr,nullptr,0,nullptr,0},
};
#endif

// ---- Network ------------------------------------------------------------
static const Ps3DataItem kNetworkItems[] = {
#if !NANO_XMB_HIDE_LEGACY
  {"Online Instruction Manuals",30,"View the online instruction manuals.\nThe latest version of the manual will be available.",nullptr,0,nullptr,0},
  {"Play Remote Devices",36,"Operate other devices on the network from this system.",nullptr,0,nullptr,0},
#endif
  {"Internet Browser",40,"View Web pages on the Internet.",nullptr,0,nullptr,0},
  {"Internet Search",55,"Search the Internet.",nullptr,0,nullptr,0},
  {"Default Browser",40,"Choose which browser app opens Web pages.",nullptr,0,nullptr,0},
};

// ---- Category table (Users / PSN / Friends excluded) --------------------
// Category icons: settings=1, photo=2, music=3, video=4, game=5, network=6.
static const Ps3DataCat kPs3DataCats[] = {
  {"settings","Settings",1,kSettingsItems,(int)(sizeof(kSettingsItems)/sizeof(kSettingsItems[0]))},
  {"photo",   "Photo",   2,kPhotoItems,   (int)(sizeof(kPhotoItems)/sizeof(kPhotoItems[0]))},
  {"music",   "Music",   3,kMusicItems,   (int)(sizeof(kMusicItems)/sizeof(kMusicItems[0]))},
  {"video",   "Video",   4,kVideoItems,   (int)(sizeof(kVideoItems)/sizeof(kVideoItems[0]))},
#if NANO_XMB_HIDE_LEGACY
  {"game",    "Game",    5,nullptr,       0},   // firmware items hidden; nano consoles are prepended at runtime
#else
  {"game",    "Game",    5,kGameItems,    (int)(sizeof(kGameItems)/sizeof(kGameItems[0]))},
#endif
  {"network", "Network", 6,kNetworkItems, (int)(sizeof(kNetworkItems)/sizeof(kNetworkItems[0]))},
};
static const int kPs3DataCatCount = (int)(sizeof(kPs3DataCats)/sizeof(kPs3DataCats[0]));

#undef PS3CH
#undef PS3D

} // namespace android

#endif // GAMMAOS_NANO_PS3_DATA_H
