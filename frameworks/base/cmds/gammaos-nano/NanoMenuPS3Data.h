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

// ---- Settings submenus --------------------------------------------------
static const Ps3DataItem kGameSettingsCh[] = {
  {"View Mode (PSP Remasters)",22,"Configure the screen size for PSP® Remasters.","Normal",0,nullptr,0},
  {"3D Display (PSP Remasters)",22,"Display PSP® Remasters in 3D.","Off",0,nullptr,0},
  {"Ad Hoc Channel (PSP Remasters)",22,"Set the Ad Hoc Mode channel for PSP® Remasters.",nullptr,0,nullptr,0},
  {"Ad Hoc Mode (PSP Remasters)",22,"Configure Ad Hoc Mode settings for PSP® Remasters.","Off",0,nullptr,0},
  {"PS Upscaler",22,"Apply upscaling to PlayStation® format software.","Off",0,nullptr,0},
  {"PS/PS2 Smoothing",22,"Smooth out the rough edges of images of PlayStation® and PlayStation®2 format software.","Off",0,nullptr,0},
};
static const Ps3DataItem kVideoSettingsCh[] = {
  {"BD/DVD Auto-start",22,"Configure BD/DVD auto-start settings.","On",0,nullptr,0},
};
static const Ps3DataItem kSystemSettingsCh[] = {
  {"Automatic Update",22,"Starts the system automatically and downloads game patches, uploads new saved data to online storage and syncs trophy information with the server.","Off",0,nullptr,0},
  {"System Name",22,nullptr,"PS3-625",0,nullptr,0},
  {"System Language",22,nullptr,"English (United Kingdom)",0,nullptr,0},
  {"Character Set",22,nullptr,nullptr,0,nullptr,0},
  {"Dictionary Type",22,nullptr,"English (UK)",0,nullptr,0},
  {"Add/Edit Term",22,nullptr,nullptr,1,nullptr,0},
  {"Delete Predictive Text Dictionary",22,"Deletes words that were added automatically to the dictionary when using the on-screen keyboard.",nullptr,1,nullptr,0},
  {"Notification Messages",22,nullptr,"Display",0,nullptr,0},
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
  {"Format Utility",22,nullptr,nullptr,1,nullptr,0},
  {"Backup Utility",22,nullptr,nullptr,1,nullptr,0},
  {"Data Transfer Utility",22,nullptr,nullptr,1,nullptr,0},
  {"Restore Default Settings",22,nullptr,nullptr,1,nullptr,0},
  {"Restore PS3™ System",22,nullptr,nullptr,1,nullptr,0},
  {"System Information",22,nullptr,nullptr,1,nullptr,0},
};
static const Ps3DataItem kThemeSettingsCh[] = {
  {"Theme",22,"Sets for use of a preset combination of elements such as colour, background or icons.",nullptr,1,nullptr,0},
  {"Colour",22,"Sets the colour of the background and options menu.",nullptr,1,nullptr,0},
  {"Background",22,"Sets the background of the XMB™ screen.",nullptr,1,nullptr,0},
  {"Font",22,"Sets the font displayed on the XMB™ screen.",nullptr,1,nullptr,0},
};
static const Ps3DataItem kDateTimeCh[] = {
  {"Date and Time",22,"Sets the date and time for this system.",nullptr,1,nullptr,0},
  {"Date Format",22,"Sets the order of display for year, month and day.","DD/MM/YYYY",0,nullptr,0},
  {"Time Format",22,"Sets the time display to either a 12-hour or 24-hour clock.","24-Hour Clock",0,nullptr,0},
  {"Time Zone",22,nullptr,"GMT",0,nullptr,0},
  {"Daylight Saving",22,"Sets for daylight saving time.","Off",0,nullptr,0},
  {"Set via Internet",22,"Obtains the correct date and time automatically via the Internet when you sign in to PSN, and sets them on your system.",nullptr,1,nullptr,0},
  {"Set Manually",22,nullptr,nullptr,1,nullptr,0},
};
static const Ps3DataItem kPowerSaveCh[] = {
  {"System Auto-Off",22,"Sets whether or not to automatically turn off this system. If you do not operate the system for a set amount of time, the system will turn off automatically.","Off",0,nullptr,0},
  {"Controller Auto-Off",22,"Sets whether or not to automatically turn off controllers. If you do not use a controller for a set amount of time, it will turn off automatically.","After 10 min.",0,nullptr,0},
  {"Power Indicator",22,nullptr,"Bright",0,nullptr,0},
  {"Turn Off System Automatically After Background Download",22,nullptr,"Off",0,nullptr,0},
};
static const Ps3DataItem kAccessoryCh[] = {
  {"Calibrate Motion Controller",22,"Calibrates the magnetic sensor of a motion controller. Use this setting when the motion controller does not control on-screen movement as expected.",nullptr,1,nullptr,0},
  {"Reassign Controllers",22,"Change the number assigned to the controller that is currently in use.",nullptr,1,nullptr,0},
  {"Controller Vibration Function",22,"Sets whether or not to use the vibration function. This setting will be applied to all controllers that support the vibration function.","On",0,nullptr,0},
  {"BD Remote Control Registration",22,"Register the BD Remote Control.",nullptr,1,nullptr,0},
  {"Manage Bluetooth® Devices",22,"Manage Bluetooth® devices.",nullptr,1,nullptr,0},
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
};
static const Ps3DataItem kDisplayCh[] = {
  {"Video Output Settings",22,"Configure video output settings according to your TV.",nullptr,1,nullptr,0},
  {"Screen Saver",22,"Configure the screen saver settings.","After 20 Minutes",0,nullptr,0},
  {"Cross Color Reduction Filter",22,"Reduces rainbow-effect artifacts. This setting is used when the system outputs composite signal to a VIDEO IN or SCART connector.","Off",0,nullptr,0},
  {"50 Hz Video Output",22,"Sets the playback method for content recorded at 50 Hz. This setting is used when playing content saved on the system storage or storage media.","Auto",0,nullptr,0},
  {"RGB Full Range (HDMI)",22,"Sets the range of RGB output signals for an HDMI connection.","Limited",0,nullptr,0},
  {"Y Pb/Cb Pr/Cr Super-White (HDMI)",22,"Sets the output format of video content recorded in a wide color range. Set this option as necessary for the TV in use.","Off",0,nullptr,0},
  {"Deep Color Output (HDMI)",22,"Outputs Deep Color video signal. If the video output is not clean or the colors do not look right, set this option to [Off].","Automatic",0,nullptr,0},
  {"1080p 24 Hz Output (HDMI)",22,"Sets the playback method for content items recorded at 24 Hz (frames/second).","Auto",0,nullptr,0},
  {"BD/DVD - Video Output Format (HDMI)",22,"Sets the output method for color signals when playing BDs or DVDs.","Auto",0,nullptr,0},
  {"Control for HDMI",22,"The system and devices connected via HDMI can operate each other.","Off",0,nullptr,0},
};
static const Ps3DataItem kSoundCh[] = {
  {"Audio Output Settings",22,"Configure audio output settings.",nullptr,1,nullptr,0},
  {"Audio Multi-Output",22,"Sets to output audio through multiple connectors simultaneously. Audio output to connectors that are not selected in [Audio Output Settings] is downscaled to 2 Ch.","Off",0,nullptr,0},
  {"Audio Output Device",22,"Sets the audio output device for use during remote play. To output audio from a PS Vita system, a PSP™ system, a PC or a mobile phone, select [Remote Play Device].","System Default",0,nullptr,0},
  {"Key Tone",22,"Sets whether or not to use key tones on the XMB™ menu.","On",0,nullptr,0},
  {"BD Audio Language",22,"Set the default BD audio language.","Original",0,nullptr,0},
  {"DVD Audio Language",22,"Set the default DVD audio language.","English",0,nullptr,0},
  {"HDD Audio Language",22,"Set the default audio language for content on the HDD.","English",0,nullptr,0},
  {"BD Audio Output Format (HDMI)",22,"Set the BD audio output format.","Linear PCM",0,nullptr,0},
};
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
static const Ps3DataItem kNetworkSettingsCh[] = {
  {"Settings and Connection Status List",22,"Displays current network settings and connection status.",nullptr,1,nullptr,0},
  {"Internet Connection",22,"Sets whether or not to connect this system to the Internet. Select this option if you want to temporarily disable the Internet connection.","Enabled",0,nullptr,0},
  {"Internet Connection Settings",22,"Sets the method for connecting the system to the Internet. Select this option to connect to a wireless LAN or to change the settings.",nullptr,1,nullptr,0},
  {"Internet Connection Test",22,"Tests the Internet connection and displays the results.",nullptr,1,nullptr,0},
  {"Media Server Connection",22,"Sets whether or not to connect to media servers.","Disabled",0,nullptr,0},
};

// ---- Settings top-level items -------------------------------------------
static const Ps3DataItem kSettingsItems[] = {
  {"System Update",8,"Update the PS3™ system software.",nullptr,1,nullptr,0},
  {"Game Settings",5,"Adjusts settings for games.",nullptr,0,PS3CH(kGameSettingsCh)},
  {"Video Settings",9,"Adjusts settings for video.",nullptr,0,PS3CH(kVideoSettingsCh)},
  {"Music Settings",3,"Adjusts settings for music.",nullptr,0,nullptr,0},
  {"Chat Settings",42,"Adjusts settings for chat.",nullptr,0,nullptr,0},
  {"System Settings",12,"Adjusts settings for this PS3™ system.",nullptr,0,PS3CH(kSystemSettingsCh)},
  {"Theme Settings",23,"Adjusts settings related to the appearance of the XMB™ screen.",nullptr,0,PS3CH(kThemeSettingsCh)},
  {"Date and Time Settings",14,"Adjusts date and time settings.",nullptr,0,PS3CH(kDateTimeCh)},
  {"Power Save Settings",56,"Adjusts settings to reduce power usage by this system.",nullptr,0,PS3CH(kPowerSaveCh)},
  {"Accessory Settings",15,"Adjusts settings for accessories that are connected to this system.",nullptr,0,PS3CH(kAccessoryCh)},
  {"Printer Settings",10,"Adjusts settings for printers that are connected to this system.",nullptr,0,nullptr,0},
  {"Display Settings",16,"Adjusts settings for video output.",nullptr,0,PS3CH(kDisplayCh)},
  {"Sound Settings",17,"Adjusts settings for audio output.",nullptr,0,PS3CH(kSoundCh)},
  {"Security Settings",18,"Adjusts parental control settings.",nullptr,0,PS3CH(kSecurityCh)},
  {"Remote Play Settings",20,"Adjusts settings for remote play. Remote play enables you to use a device that supports the remote play feature (such as a PSP™ system) to operate this system over a network.",nullptr,0,PS3CH(kRemotePlayCh)},
  {"Network Settings",6,"Adjusts settings for the Internet connection.",nullptr,0,PS3CH(kNetworkSettingsCh)},
};

// ---- Photo --------------------------------------------------------------
static const Ps3DataItem kPhotoItems[] = {
  {"Search for Media Servers",35,"Scans the network and connects to a media server. To use this function, a media server must be set up to allow connections from the PS3™ system.",nullptr,0,nullptr,0},
  {"Photo Gallery",64,"Create a space to enjoy and enhance your photos.\nTurn the photos on your PS3™ system into great albums in minutes.\nYou can sort your photos by themes, add music to enhance a slideshow or add custom frames to your photos. The more photos you add, the more fun you can have - the possibilities are endless!",nullptr,0,nullptr,0},
  {"Playlists",37,nullptr,nullptr,0,nullptr,0},
  {"May 2026",62,"8 Images",nullptr,0,nullptr,0},
};

// ---- Music --------------------------------------------------------------
static const Ps3DataItem kMusicItems[] = {
  {"Search for Media Servers",35,"Scans the network and connects to a media server. To use this function, a media server must be set up to allow connections from the PS3™ system.",nullptr,0,nullptr,0},
  {"Playlists",37,nullptr,nullptr,0,nullptr,0},
};

// ---- Video --------------------------------------------------------------
static const Ps3DataItem kVideoItems[] = {
  {"BD Data Utility",62,"Delete BD data saved on the system storage.",nullptr,0,nullptr,0},
  {"Search for Media Servers",35,"Scans the network and connects to a media server. To use this function, a media server must be set up to allow connections from the PS3™ system.",nullptr,0,nullptr,0},
  {"Video Editor & Uploader",67,"You can edit a video that you like, upload it to a video sharing website, and then invite your friends to view the video.",nullptr,0,nullptr,0},
};

// ---- Game (firmware items; nano consoles appended at runtime) -----------
static const Ps3DataItem kMemCardCh[] = {
  {"Internal Memory Card",64,"Internal memory card for PS/PS2 saved data.",nullptr,0,nullptr,0},
  {"Create New Internal Memory Card",64,"Create a new internal memory card.",nullptr,0,nullptr,0},
};
static const Ps3DataItem kGameItems[] = {
  {"PS Vita System Application Utility",62,"*User",nullptr,0,nullptr,0},
  {"Game Data Utility",62,"Manages game data installed on the PS3™ system. To delete a game data item, select it and then press the @T button.",nullptr,0,nullptr,0},
  {"Memory Card Utility (PS/PS2)",64,"Manages internal memory cards for use with PlayStation® and PlayStation®2 format software.",nullptr,0,PS3CH(kMemCardCh)},
  {"Saved Data Utility (PS2)",63,"*User",nullptr,0,nullptr,0},
  {"Saved Data Utility (minis/PSP™)",66,"*User",nullptr,0,nullptr,0},
  {"Saved Data Utility (PS3™)",63,"*User",nullptr,0,nullptr,0},
  {"PlayStation®Store",-1,"FREE* to access, PlayStation®Store is the only place to download new and exclusive PS3™ games, FREE playable demos, add-on packs, and high-definition videos.\n\nGet more for your PS3™ and visit PlayStation®Store today.\n\n* Broadband Internet connection required.",nullptr,2,nullptr,0},
  {"Software Instruction Manuals",30,"Displays manuals for the software installed on the PS3™ system.",nullptr,0,nullptr,0},
  {"Corrupted Data",22,nullptr,nullptr,0,nullptr,0},
};

// ---- Network ------------------------------------------------------------
static const Ps3DataItem kNetworkItems[] = {
  {"Online Instruction Manuals",30,"View the online instruction manuals.\nThe latest version of the manual will be available.",nullptr,0,nullptr,0},
  {"Play Remote Devices",36,"Operate other devices on the network from this system.",nullptr,0,nullptr,0},
  {"Internet Browser",40,"View Web pages on the Internet.",nullptr,0,nullptr,0},
  {"Internet Search",55,"Search the Internet.",nullptr,0,nullptr,0},
};

// ---- Category table (Users / PSN / Friends excluded) --------------------
// Category icons: settings=1, photo=2, music=3, video=4, game=5, network=6.
static const Ps3DataCat kPs3DataCats[] = {
  {"settings","Settings",1,kSettingsItems,(int)(sizeof(kSettingsItems)/sizeof(kSettingsItems[0]))},
  {"photo",   "Photo",   2,kPhotoItems,   (int)(sizeof(kPhotoItems)/sizeof(kPhotoItems[0]))},
  {"music",   "Music",   3,kMusicItems,   (int)(sizeof(kMusicItems)/sizeof(kMusicItems[0]))},
  {"video",   "Video",   4,kVideoItems,   (int)(sizeof(kVideoItems)/sizeof(kVideoItems[0]))},
  {"game",    "Game",    5,kGameItems,    (int)(sizeof(kGameItems)/sizeof(kGameItems[0]))},
  {"network", "Network", 6,kNetworkItems, (int)(sizeof(kNetworkItems)/sizeof(kNetworkItems[0]))},
};
static const int kPs3DataCatCount = (int)(sizeof(kPs3DataCats)/sizeof(kPs3DataCats[0]));

#undef PS3CH
#undef PS3D

} // namespace android

#endif // GAMMAOS_NANO_PS3_DATA_H
