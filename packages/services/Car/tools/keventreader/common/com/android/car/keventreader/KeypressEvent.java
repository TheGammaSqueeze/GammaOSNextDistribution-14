/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.car.keventreader;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class KeypressEvent implements Parcelable {
    private static final Map<Integer, String> KEYCODE_NAME_MAP = new HashMap<Integer, String>();
    static {
        KEYCODE_NAME_MAP.put(0, "RESERVED");
        KEYCODE_NAME_MAP.put(1, "ESC");
        KEYCODE_NAME_MAP.put(2, "1");
        KEYCODE_NAME_MAP.put(3, "2");
        KEYCODE_NAME_MAP.put(4, "3");
        KEYCODE_NAME_MAP.put(5, "4");
        KEYCODE_NAME_MAP.put(6, "5");
        KEYCODE_NAME_MAP.put(7, "6");
        KEYCODE_NAME_MAP.put(8, "7");
        KEYCODE_NAME_MAP.put(9, "8");
        KEYCODE_NAME_MAP.put(10, "9");
        KEYCODE_NAME_MAP.put(11, "0");
        KEYCODE_NAME_MAP.put(12, "MINUS");
        KEYCODE_NAME_MAP.put(13, "EQUAL");
        KEYCODE_NAME_MAP.put(14, "BACKSPACE");
        KEYCODE_NAME_MAP.put(15, "TAB");
        KEYCODE_NAME_MAP.put(16, "Q");
        KEYCODE_NAME_MAP.put(17, "W");
        KEYCODE_NAME_MAP.put(18, "E");
        KEYCODE_NAME_MAP.put(19, "R");
        KEYCODE_NAME_MAP.put(20, "T");
        KEYCODE_NAME_MAP.put(21, "Y");
        KEYCODE_NAME_MAP.put(22, "U");
        KEYCODE_NAME_MAP.put(23, "I");
        KEYCODE_NAME_MAP.put(24, "O");
        KEYCODE_NAME_MAP.put(25, "P");
        KEYCODE_NAME_MAP.put(26, "LEFTBRACE");
        KEYCODE_NAME_MAP.put(27, "RIGHTBRACE");
        KEYCODE_NAME_MAP.put(28, "ENTER");
        KEYCODE_NAME_MAP.put(29, "LEFTCTRL");
        KEYCODE_NAME_MAP.put(30, "A");
        KEYCODE_NAME_MAP.put(31, "S");
        KEYCODE_NAME_MAP.put(32, "D");
        KEYCODE_NAME_MAP.put(33, "F");
        KEYCODE_NAME_MAP.put(34, "G");
        KEYCODE_NAME_MAP.put(35, "H");
        KEYCODE_NAME_MAP.put(36, "J");
        KEYCODE_NAME_MAP.put(37, "K");
        KEYCODE_NAME_MAP.put(38, "L");
        KEYCODE_NAME_MAP.put(39, "SEMICOLON");
        KEYCODE_NAME_MAP.put(40, "APOSTROPHE");
        KEYCODE_NAME_MAP.put(41, "GRAVE");
        KEYCODE_NAME_MAP.put(42, "LEFTSHIFT");
        KEYCODE_NAME_MAP.put(43, "BACKSLASH");
        KEYCODE_NAME_MAP.put(44, "Z");
        KEYCODE_NAME_MAP.put(45, "X");
        KEYCODE_NAME_MAP.put(46, "C");
        KEYCODE_NAME_MAP.put(47, "V");
        KEYCODE_NAME_MAP.put(48, "B");
        KEYCODE_NAME_MAP.put(49, "N");
        KEYCODE_NAME_MAP.put(50, "M");
        KEYCODE_NAME_MAP.put(51, "COMMA");
        KEYCODE_NAME_MAP.put(52, "DOT");
        KEYCODE_NAME_MAP.put(53, "SLASH");
        KEYCODE_NAME_MAP.put(54, "RIGHTSHIFT");
        KEYCODE_NAME_MAP.put(55, "KPASTERISK");
        KEYCODE_NAME_MAP.put(56, "LEFTALT");
        KEYCODE_NAME_MAP.put(57, "SPACE");
        KEYCODE_NAME_MAP.put(58, "CAPSLOCK");
        KEYCODE_NAME_MAP.put(59, "F1");
        KEYCODE_NAME_MAP.put(60, "F2");
        KEYCODE_NAME_MAP.put(61, "F3");
        KEYCODE_NAME_MAP.put(62, "F4");
        KEYCODE_NAME_MAP.put(63, "F5");
        KEYCODE_NAME_MAP.put(64, "F6");
        KEYCODE_NAME_MAP.put(65, "F7");
        KEYCODE_NAME_MAP.put(66, "F8");
        KEYCODE_NAME_MAP.put(67, "F9");
        KEYCODE_NAME_MAP.put(68, "F10");
        KEYCODE_NAME_MAP.put(69, "NUMLOCK");
        KEYCODE_NAME_MAP.put(70, "SCROLLLOCK");
        KEYCODE_NAME_MAP.put(71, "KP7");
        KEYCODE_NAME_MAP.put(72, "KP8");
        KEYCODE_NAME_MAP.put(73, "KP9");
        KEYCODE_NAME_MAP.put(74, "KPMINUS");
        KEYCODE_NAME_MAP.put(75, "KP4");
        KEYCODE_NAME_MAP.put(76, "KP5");
        KEYCODE_NAME_MAP.put(77, "KP6");
        KEYCODE_NAME_MAP.put(78, "KPPLUS");
        KEYCODE_NAME_MAP.put(79, "KP1");
        KEYCODE_NAME_MAP.put(80, "KP2");
        KEYCODE_NAME_MAP.put(81, "KP3");
        KEYCODE_NAME_MAP.put(82, "KP0");
        KEYCODE_NAME_MAP.put(83, "KPDOT");
        KEYCODE_NAME_MAP.put(85, "ZENKAKUHANKAKU");
        KEYCODE_NAME_MAP.put(86, "102ND");
        KEYCODE_NAME_MAP.put(87, "F11");
        KEYCODE_NAME_MAP.put(88, "F12");
        KEYCODE_NAME_MAP.put(89, "RO");
        KEYCODE_NAME_MAP.put(90, "KATAKANA");
        KEYCODE_NAME_MAP.put(91, "HIRAGANA");
        KEYCODE_NAME_MAP.put(92, "HENKAN");
        KEYCODE_NAME_MAP.put(93, "KATAKANAHIRAGANA");
        KEYCODE_NAME_MAP.put(94, "MUHENKAN");
        KEYCODE_NAME_MAP.put(95, "KPJPCOMMA");
        KEYCODE_NAME_MAP.put(96, "KPENTER");
        KEYCODE_NAME_MAP.put(97, "RIGHTCTRL");
        KEYCODE_NAME_MAP.put(98, "KPSLASH");
        KEYCODE_NAME_MAP.put(99, "SYSRQ");
        KEYCODE_NAME_MAP.put(100, "RIGHTALT");
        KEYCODE_NAME_MAP.put(101, "LINEFEED");
        KEYCODE_NAME_MAP.put(102, "HOME");
        KEYCODE_NAME_MAP.put(103, "UP");
        KEYCODE_NAME_MAP.put(104, "PAGEUP");
        KEYCODE_NAME_MAP.put(105, "LEFT");
        KEYCODE_NAME_MAP.put(106, "RIGHT");
        KEYCODE_NAME_MAP.put(107, "END");
        KEYCODE_NAME_MAP.put(108, "DOWN");
        KEYCODE_NAME_MAP.put(109, "PAGEDOWN");
        KEYCODE_NAME_MAP.put(110, "INSERT");
        KEYCODE_NAME_MAP.put(111, "DELETE");
        KEYCODE_NAME_MAP.put(112, "MACRO");
        KEYCODE_NAME_MAP.put(113, "MUTE");
        KEYCODE_NAME_MAP.put(114, "VOLUMEDOWN");
        KEYCODE_NAME_MAP.put(115, "VOLUMEUP");
        KEYCODE_NAME_MAP.put(116, "POWER");
        KEYCODE_NAME_MAP.put(117, "KPEQUAL");
        KEYCODE_NAME_MAP.put(118, "KPPLUSMINUS");
        KEYCODE_NAME_MAP.put(119, "PAUSE");
        KEYCODE_NAME_MAP.put(120, "SCALE");
        KEYCODE_NAME_MAP.put(121, "KPCOMMA");
        KEYCODE_NAME_MAP.put(122, "HANGEUL");
        KEYCODE_NAME_MAP.put(123, "HANJA");
        KEYCODE_NAME_MAP.put(124, "YEN");
        KEYCODE_NAME_MAP.put(125, "LEFTMETA");
        KEYCODE_NAME_MAP.put(126, "RIGHTMETA");
        KEYCODE_NAME_MAP.put(127, "COMPOSE");
        KEYCODE_NAME_MAP.put(128, "STOP");
        KEYCODE_NAME_MAP.put(129, "AGAIN");
        KEYCODE_NAME_MAP.put(130, "PROPS");
        KEYCODE_NAME_MAP.put(131, "UNDO");
        KEYCODE_NAME_MAP.put(132, "FRONT");
        KEYCODE_NAME_MAP.put(133, "COPY");
        KEYCODE_NAME_MAP.put(134, "OPEN");
        KEYCODE_NAME_MAP.put(135, "PASTE");
        KEYCODE_NAME_MAP.put(136, "FIND");
        KEYCODE_NAME_MAP.put(137, "CUT");
        KEYCODE_NAME_MAP.put(138, "HELP");
        KEYCODE_NAME_MAP.put(139, "MENU");
        KEYCODE_NAME_MAP.put(140, "CALC");
        KEYCODE_NAME_MAP.put(141, "SETUP");
        KEYCODE_NAME_MAP.put(142, "SLEEP");
        KEYCODE_NAME_MAP.put(143, "WAKEUP");
        KEYCODE_NAME_MAP.put(144, "FILE");
        KEYCODE_NAME_MAP.put(145, "SENDFILE");
        KEYCODE_NAME_MAP.put(146, "DELETEFILE");
        KEYCODE_NAME_MAP.put(147, "XFER");
        KEYCODE_NAME_MAP.put(148, "PROG1");
        KEYCODE_NAME_MAP.put(149, "PROG2");
        KEYCODE_NAME_MAP.put(150, "WWW");
        KEYCODE_NAME_MAP.put(151, "MSDOS");
        KEYCODE_NAME_MAP.put(152, "SCREENLOCK");
        KEYCODE_NAME_MAP.put(153, "ROTATE_DISPLAY");
        KEYCODE_NAME_MAP.put(154, "CYCLEWINDOWS");
        KEYCODE_NAME_MAP.put(155, "MAIL");
        KEYCODE_NAME_MAP.put(156, "BOOKMARKS");
        KEYCODE_NAME_MAP.put(157, "COMPUTER");
        KEYCODE_NAME_MAP.put(158, "BACK");
        KEYCODE_NAME_MAP.put(159, "FORWARD");
        KEYCODE_NAME_MAP.put(160, "CLOSECD");
        KEYCODE_NAME_MAP.put(161, "EJECTCD");
        KEYCODE_NAME_MAP.put(162, "EJECTCLOSECD");
        KEYCODE_NAME_MAP.put(163, "NEXTSONG");
        KEYCODE_NAME_MAP.put(164, "PLAYPAUSE");
        KEYCODE_NAME_MAP.put(165, "PREVIOUSSONG");
        KEYCODE_NAME_MAP.put(166, "STOPCD");
        KEYCODE_NAME_MAP.put(167, "RECORD");
        KEYCODE_NAME_MAP.put(168, "REWIND");
        KEYCODE_NAME_MAP.put(169, "PHONE");
        KEYCODE_NAME_MAP.put(170, "ISO");
        KEYCODE_NAME_MAP.put(171, "CONFIG");
        KEYCODE_NAME_MAP.put(172, "HOMEPAGE");
        KEYCODE_NAME_MAP.put(173, "REFRESH");
        KEYCODE_NAME_MAP.put(174, "EXIT");
        KEYCODE_NAME_MAP.put(175, "MOVE");
        KEYCODE_NAME_MAP.put(176, "EDIT");
        KEYCODE_NAME_MAP.put(177, "SCROLLUP");
        KEYCODE_NAME_MAP.put(178, "SCROLLDOWN");
        KEYCODE_NAME_MAP.put(179, "KPLEFTPAREN");
        KEYCODE_NAME_MAP.put(180, "KPRIGHTPAREN");
        KEYCODE_NAME_MAP.put(181, "NEW");
        KEYCODE_NAME_MAP.put(182, "REDO");
        KEYCODE_NAME_MAP.put(183, "F13");
        KEYCODE_NAME_MAP.put(184, "F14");
        KEYCODE_NAME_MAP.put(185, "F15");
        KEYCODE_NAME_MAP.put(186, "F16");
        KEYCODE_NAME_MAP.put(187, "F17");
        KEYCODE_NAME_MAP.put(188, "F18");
        KEYCODE_NAME_MAP.put(189, "F19");
        KEYCODE_NAME_MAP.put(190, "F20");
        KEYCODE_NAME_MAP.put(191, "F21");
        KEYCODE_NAME_MAP.put(192, "F22");
        KEYCODE_NAME_MAP.put(193, "F23");
        KEYCODE_NAME_MAP.put(194, "F24");
        KEYCODE_NAME_MAP.put(200, "PLAYCD");
        KEYCODE_NAME_MAP.put(201, "PAUSECD");
        KEYCODE_NAME_MAP.put(202, "PROG3");
        KEYCODE_NAME_MAP.put(203, "PROG4");
        KEYCODE_NAME_MAP.put(204, "DASHBOARD");
        KEYCODE_NAME_MAP.put(205, "SUSPEND");
        KEYCODE_NAME_MAP.put(206, "CLOSE");
        KEYCODE_NAME_MAP.put(207, "PLAY");
        KEYCODE_NAME_MAP.put(208, "FASTFORWARD");
        KEYCODE_NAME_MAP.put(209, "BASSBOOST");
        KEYCODE_NAME_MAP.put(210, "PRINT");
        KEYCODE_NAME_MAP.put(211, "HP");
        KEYCODE_NAME_MAP.put(212, "CAMERA");
        KEYCODE_NAME_MAP.put(213, "SOUND");
        KEYCODE_NAME_MAP.put(214, "QUESTION");
        KEYCODE_NAME_MAP.put(215, "EMAIL");
        KEYCODE_NAME_MAP.put(216, "CHAT");
        KEYCODE_NAME_MAP.put(217, "SEARCH");
        KEYCODE_NAME_MAP.put(218, "CONNECT");
        KEYCODE_NAME_MAP.put(219, "FINANCE");
        KEYCODE_NAME_MAP.put(220, "SPORT");
        KEYCODE_NAME_MAP.put(221, "SHOP");
        KEYCODE_NAME_MAP.put(222, "ALTERASE");
        KEYCODE_NAME_MAP.put(223, "CANCEL");
        KEYCODE_NAME_MAP.put(224, "BRIGHTNESSDOWN");
        KEYCODE_NAME_MAP.put(225, "BRIGHTNESSUP");
        KEYCODE_NAME_MAP.put(226, "MEDIA");
        KEYCODE_NAME_MAP.put(227, "SWITCHVIDEOMODE");
        KEYCODE_NAME_MAP.put(228, "KBDILLUMTOGGLE");
        KEYCODE_NAME_MAP.put(229, "KBDILLUMDOWN");
        KEYCODE_NAME_MAP.put(230, "KBDILLUMUP");
        KEYCODE_NAME_MAP.put(231, "SEND");
        KEYCODE_NAME_MAP.put(232, "REPLY");
        KEYCODE_NAME_MAP.put(233, "FORWARDMAIL");
        KEYCODE_NAME_MAP.put(234, "SAVE");
        KEYCODE_NAME_MAP.put(235, "DOCUMENTS");
        KEYCODE_NAME_MAP.put(236, "BATTERY");
        KEYCODE_NAME_MAP.put(237, "BLUETOOTH");
        KEYCODE_NAME_MAP.put(238, "WLAN");
        KEYCODE_NAME_MAP.put(239, "UWB");
        KEYCODE_NAME_MAP.put(240, "UNKNOWN");
        KEYCODE_NAME_MAP.put(241, "VIDEO_NEXT");
        KEYCODE_NAME_MAP.put(242, "VIDEO_PREV");
        KEYCODE_NAME_MAP.put(243, "BRIGHTNESS_CYCLE");
        KEYCODE_NAME_MAP.put(244, "BRIGHTNESS_AUTO");
        KEYCODE_NAME_MAP.put(245, "DISPLAY_OFF");
        KEYCODE_NAME_MAP.put(246, "WWAN");
        KEYCODE_NAME_MAP.put(247, "RFKILL");
        KEYCODE_NAME_MAP.put(248, "MICMUTE");
        KEYCODE_NAME_MAP.put(0x160, "OK");
        KEYCODE_NAME_MAP.put(0x161, "SELECT");
        KEYCODE_NAME_MAP.put(0x162, "GOTO");
        KEYCODE_NAME_MAP.put(0x163, "CLEAR");
        KEYCODE_NAME_MAP.put(0x164, "POWER2");
        KEYCODE_NAME_MAP.put(0x165, "OPTION");
        KEYCODE_NAME_MAP.put(0x166, "INFO");
        KEYCODE_NAME_MAP.put(0x167, "TIME");
        KEYCODE_NAME_MAP.put(0x168, "VENDOR");
        KEYCODE_NAME_MAP.put(0x169, "ARCHIVE");
        KEYCODE_NAME_MAP.put(0x16a, "PROGRAM");
        KEYCODE_NAME_MAP.put(0x16b, "CHANNEL");
        KEYCODE_NAME_MAP.put(0x16c, "FAVORITES");
        KEYCODE_NAME_MAP.put(0x16d, "EPG");
        KEYCODE_NAME_MAP.put(0x16e, "PVR");
        KEYCODE_NAME_MAP.put(0x16f, "MHP");
        KEYCODE_NAME_MAP.put(0x170, "LANGUAGE");
        KEYCODE_NAME_MAP.put(0x171, "TITLE");
        KEYCODE_NAME_MAP.put(0x172, "SUBTITLE");
        KEYCODE_NAME_MAP.put(0x173, "ANGLE");
        KEYCODE_NAME_MAP.put(0x174, "ZOOM");
        KEYCODE_NAME_MAP.put(0x175, "MODE");
        KEYCODE_NAME_MAP.put(0x176, "KEYBOARD");
        KEYCODE_NAME_MAP.put(0x177, "SCREEN");
        KEYCODE_NAME_MAP.put(0x178, "PC");
        KEYCODE_NAME_MAP.put(0x179, "TV");
        KEYCODE_NAME_MAP.put(0x17a, "TV2");
        KEYCODE_NAME_MAP.put(0x17b, "VCR");
        KEYCODE_NAME_MAP.put(0x17c, "VCR2");
        KEYCODE_NAME_MAP.put(0x17d, "SAT");
        KEYCODE_NAME_MAP.put(0x17e, "SAT2");
        KEYCODE_NAME_MAP.put(0x17f, "CD");
        KEYCODE_NAME_MAP.put(0x180, "TAPE");
        KEYCODE_NAME_MAP.put(0x181, "RADIO");
        KEYCODE_NAME_MAP.put(0x182, "TUNER");
        KEYCODE_NAME_MAP.put(0x183, "PLAYER");
        KEYCODE_NAME_MAP.put(0x184, "TEXT");
        KEYCODE_NAME_MAP.put(0x185, "DVD");
        KEYCODE_NAME_MAP.put(0x186, "AUX");
        KEYCODE_NAME_MAP.put(0x187, "MP3");
        KEYCODE_NAME_MAP.put(0x188, "AUDIO");
        KEYCODE_NAME_MAP.put(0x189, "VIDEO");
        KEYCODE_NAME_MAP.put(0x18a, "DIRECTORY");
        KEYCODE_NAME_MAP.put(0x18b, "LIST");
        KEYCODE_NAME_MAP.put(0x18c, "MEMO");
        KEYCODE_NAME_MAP.put(0x18d, "CALENDAR");
        KEYCODE_NAME_MAP.put(0x18e, "RED");
        KEYCODE_NAME_MAP.put(0x18f, "GREEN");
        KEYCODE_NAME_MAP.put(0x190, "YELLOW");
        KEYCODE_NAME_MAP.put(0x191, "BLUE");
        KEYCODE_NAME_MAP.put(0x192, "CHANNELUP");
        KEYCODE_NAME_MAP.put(0x193, "CHANNELDOWN");
        KEYCODE_NAME_MAP.put(0x194, "FIRST");
        KEYCODE_NAME_MAP.put(0x195, "LAST");
        KEYCODE_NAME_MAP.put(0x196, "AB");
        KEYCODE_NAME_MAP.put(0x197, "NEXT");
        KEYCODE_NAME_MAP.put(0x198, "RESTART");
        KEYCODE_NAME_MAP.put(0x199, "SLOW");
        KEYCODE_NAME_MAP.put(0x19a, "SHUFFLE");
        KEYCODE_NAME_MAP.put(0x19b, "BREAK");
        KEYCODE_NAME_MAP.put(0x19c, "PREVIOUS");
        KEYCODE_NAME_MAP.put(0x19d, "DIGITS");
        KEYCODE_NAME_MAP.put(0x19e, "TEEN");
        KEYCODE_NAME_MAP.put(0x19f, "TWEN");
        KEYCODE_NAME_MAP.put(0x1a0, "VIDEOPHONE");
        KEYCODE_NAME_MAP.put(0x1a1, "GAMES");
        KEYCODE_NAME_MAP.put(0x1a2, "ZOOMIN");
        KEYCODE_NAME_MAP.put(0x1a3, "ZOOMOUT");
        KEYCODE_NAME_MAP.put(0x1a4, "ZOOMRESET");
        KEYCODE_NAME_MAP.put(0x1a5, "WORDPROCESSOR");
        KEYCODE_NAME_MAP.put(0x1a6, "EDITOR");
        KEYCODE_NAME_MAP.put(0x1a7, "SPREADSHEET");
        KEYCODE_NAME_MAP.put(0x1a8, "GRAPHICSEDITOR");
        KEYCODE_NAME_MAP.put(0x1a9, "PRESENTATION");
        KEYCODE_NAME_MAP.put(0x1aa, "DATABASE");
        KEYCODE_NAME_MAP.put(0x1ab, "NEWS");
        KEYCODE_NAME_MAP.put(0x1ac, "VOICEMAIL");
        KEYCODE_NAME_MAP.put(0x1ad, "ADDRESSBOOK");
        KEYCODE_NAME_MAP.put(0x1ae, "MESSENGER");
        KEYCODE_NAME_MAP.put(0x1af, "DISPLAYTOGGLE");
        KEYCODE_NAME_MAP.put(0x1b0, "SPELLCHECK");
        KEYCODE_NAME_MAP.put(0x1b1, "LOGOFF");
        KEYCODE_NAME_MAP.put(0x1b2, "DOLLAR");
        KEYCODE_NAME_MAP.put(0x1b3, "EURO");
        KEYCODE_NAME_MAP.put(0x1b4, "FRAMEBACK");
        KEYCODE_NAME_MAP.put(0x1b5, "FRAMEFORWARD");
        KEYCODE_NAME_MAP.put(0x1b6, "CONTEXT_MENU");
        KEYCODE_NAME_MAP.put(0x1b7, "MEDIA_REPEAT");
        KEYCODE_NAME_MAP.put(0x1b8, "10CHANNELSUP");
        KEYCODE_NAME_MAP.put(0x1b9, "10CHANNELSDOWN");
        KEYCODE_NAME_MAP.put(0x1ba, "IMAGES");
        KEYCODE_NAME_MAP.put(0x1c0, "DEL_EOL");
        KEYCODE_NAME_MAP.put(0x1c1, "DEL_EOS");
        KEYCODE_NAME_MAP.put(0x1c2, "INS_LINE");
        KEYCODE_NAME_MAP.put(0x1c3, "DEL_LINE");
        KEYCODE_NAME_MAP.put(0x1d0, "FN");
        KEYCODE_NAME_MAP.put(0x1d1, "FN_ESC");
        KEYCODE_NAME_MAP.put(0x1d2, "FN_F1");
        KEYCODE_NAME_MAP.put(0x1d3, "FN_F2");
        KEYCODE_NAME_MAP.put(0x1d4, "FN_F3");
        KEYCODE_NAME_MAP.put(0x1d5, "FN_F4");
        KEYCODE_NAME_MAP.put(0x1d6, "FN_F5");
        KEYCODE_NAME_MAP.put(0x1d7, "FN_F6");
        KEYCODE_NAME_MAP.put(0x1d8, "FN_F7");
        KEYCODE_NAME_MAP.put(0x1d9, "FN_F8");
        KEYCODE_NAME_MAP.put(0x1da, "FN_F9");
        KEYCODE_NAME_MAP.put(0x1db, "FN_F10");
        KEYCODE_NAME_MAP.put(0x1dc, "FN_F11");
        KEYCODE_NAME_MAP.put(0x1dd, "FN_F12");
        KEYCODE_NAME_MAP.put(0x1de, "FN_1");
        KEYCODE_NAME_MAP.put(0x1df, "FN_2");
        KEYCODE_NAME_MAP.put(0x1e0, "FN_D");
        KEYCODE_NAME_MAP.put(0x1e1, "FN_E");
        KEYCODE_NAME_MAP.put(0x1e2, "FN_F");
        KEYCODE_NAME_MAP.put(0x1e3, "FN_S");
        KEYCODE_NAME_MAP.put(0x1e4, "FN_B");
        KEYCODE_NAME_MAP.put(0x1f1, "BRL_DOT1");
        KEYCODE_NAME_MAP.put(0x1f2, "BRL_DOT2");
        KEYCODE_NAME_MAP.put(0x1f3, "BRL_DOT3");
        KEYCODE_NAME_MAP.put(0x1f4, "BRL_DOT4");
        KEYCODE_NAME_MAP.put(0x1f5, "BRL_DOT5");
        KEYCODE_NAME_MAP.put(0x1f6, "BRL_DOT6");
        KEYCODE_NAME_MAP.put(0x1f7, "BRL_DOT7");
        KEYCODE_NAME_MAP.put(0x1f8, "BRL_DOT8");
        KEYCODE_NAME_MAP.put(0x1f9, "BRL_DOT9");
        KEYCODE_NAME_MAP.put(0x1fa, "BRL_DOT10");
        KEYCODE_NAME_MAP.put(0x200, "NUMERIC_0");
        KEYCODE_NAME_MAP.put(0x201, "NUMERIC_1");
        KEYCODE_NAME_MAP.put(0x202, "NUMERIC_2");
        KEYCODE_NAME_MAP.put(0x203, "NUMERIC_3");
        KEYCODE_NAME_MAP.put(0x204, "NUMERIC_4");
        KEYCODE_NAME_MAP.put(0x205, "NUMERIC_5");
        KEYCODE_NAME_MAP.put(0x206, "NUMERIC_6");
        KEYCODE_NAME_MAP.put(0x207, "NUMERIC_7");
        KEYCODE_NAME_MAP.put(0x208, "NUMERIC_8");
        KEYCODE_NAME_MAP.put(0x209, "NUMERIC_9");
        KEYCODE_NAME_MAP.put(0x20a, "NUMERIC_STAR");
        KEYCODE_NAME_MAP.put(0x20b, "NUMERIC_POUND");
        KEYCODE_NAME_MAP.put(0x20c, "NUMERIC_A");
        KEYCODE_NAME_MAP.put(0x20d, "NUMERIC_B");
        KEYCODE_NAME_MAP.put(0x20e, "NUMERIC_C");
        KEYCODE_NAME_MAP.put(0x20f, "NUMERIC_D");
        KEYCODE_NAME_MAP.put(0x210, "CAMERA_FOCUS");
        KEYCODE_NAME_MAP.put(0x211, "WPS_BUTTON");
        KEYCODE_NAME_MAP.put(0x212, "TOUCHPAD_TOGGLE");
        KEYCODE_NAME_MAP.put(0x213, "TOUCHPAD_ON");
        KEYCODE_NAME_MAP.put(0x214, "TOUCHPAD_OFF");
        KEYCODE_NAME_MAP.put(0x215, "CAMERA_ZOOMIN");
        KEYCODE_NAME_MAP.put(0x216, "CAMERA_ZOOMOUT");
        KEYCODE_NAME_MAP.put(0x217, "CAMERA_UP");
        KEYCODE_NAME_MAP.put(0x218, "CAMERA_DOWN");
        KEYCODE_NAME_MAP.put(0x219, "CAMERA_LEFT");
        KEYCODE_NAME_MAP.put(0x21a, "CAMERA_RIGHT");
        KEYCODE_NAME_MAP.put(0x21b, "ATTENDANT_ON");
        KEYCODE_NAME_MAP.put(0x21c, "ATTENDANT_OFF");
        KEYCODE_NAME_MAP.put(0x21d, "ATTENDANT_TOGGLE");
        KEYCODE_NAME_MAP.put(0x21e, "LIGHTS_TOGGLE");
        KEYCODE_NAME_MAP.put(0x230, "ALS_TOGGLE");
        KEYCODE_NAME_MAP.put(0x240, "BUTTONCONFIG");
        KEYCODE_NAME_MAP.put(0x241, "TASKMANAGER");
        KEYCODE_NAME_MAP.put(0x242, "JOURNAL");
        KEYCODE_NAME_MAP.put(0x243, "CONTROLPANEL");
        KEYCODE_NAME_MAP.put(0x244, "APPSELECT");
        KEYCODE_NAME_MAP.put(0x245, "SCREENSAVER");
        KEYCODE_NAME_MAP.put(0x246, "VOICECOMMAND");
        KEYCODE_NAME_MAP.put(0x247, "ASSISTANT");
        KEYCODE_NAME_MAP.put(0x250, "BRIGHTNESS_MIN");
        KEYCODE_NAME_MAP.put(0x251, "BRIGHTNESS_MAX");
        KEYCODE_NAME_MAP.put(0x260, "KBDINPUTASSIST_PREV");
        KEYCODE_NAME_MAP.put(0x261, "KBDINPUTASSIST_NEXT");
        KEYCODE_NAME_MAP.put(0x262, "KBDINPUTASSIST_PREVGROUP");
        KEYCODE_NAME_MAP.put(0x263, "KBDINPUTASSIST_NEXTGROUP");
        KEYCODE_NAME_MAP.put(0x264, "KBDINPUTASSIST_ACCEPT");
        KEYCODE_NAME_MAP.put(0x265, "KBDINPUTASSIST_CANCEL");
        KEYCODE_NAME_MAP.put(0x266, "RIGHT_UP");
        KEYCODE_NAME_MAP.put(0x267, "RIGHT_DOWN");
        KEYCODE_NAME_MAP.put(0x268, "LEFT_UP");
        KEYCODE_NAME_MAP.put(0x269, "LEFT_DOWN");
        KEYCODE_NAME_MAP.put(0x26a, "ROOT_MENU");
        KEYCODE_NAME_MAP.put(0x26b, "MEDIA_TOP_MENU");
        KEYCODE_NAME_MAP.put(0x26c, "NUMERIC_11");
        KEYCODE_NAME_MAP.put(0x26d, "NUMERIC_12");
        KEYCODE_NAME_MAP.put(0x26e, "AUDIO_DESC");
        KEYCODE_NAME_MAP.put(0x26f, "3D_MODE");
        KEYCODE_NAME_MAP.put(0x270, "NEXT_FAVORITE");
        KEYCODE_NAME_MAP.put(0x271, "STOP_RECORD");
        KEYCODE_NAME_MAP.put(0x272, "PAUSE_RECORD");
        KEYCODE_NAME_MAP.put(0x273, "VOD");
        KEYCODE_NAME_MAP.put(0x274, "UNMUTE");
        KEYCODE_NAME_MAP.put(0x275, "FASTREVERSE");
        KEYCODE_NAME_MAP.put(0x276, "SLOWREVERSE");
        KEYCODE_NAME_MAP.put(0x277, "DATA");
        KEYCODE_NAME_MAP.put(0x278, "ONSCREEN_KEYBOARD");
        KEYCODE_NAME_MAP.put(113, "MIN_INTERESTING");
        KEYCODE_NAME_MAP.put(0x2ff, "MAX");
        KEYCODE_NAME_MAP.put(0x100, "MISC");
        KEYCODE_NAME_MAP.put(0x100, "0");
        KEYCODE_NAME_MAP.put(0x101, "1");
        KEYCODE_NAME_MAP.put(0x102, "2");
        KEYCODE_NAME_MAP.put(0x103, "3");
        KEYCODE_NAME_MAP.put(0x104, "4");
        KEYCODE_NAME_MAP.put(0x105, "5");
        KEYCODE_NAME_MAP.put(0x106, "6");
        KEYCODE_NAME_MAP.put(0x107, "7");
        KEYCODE_NAME_MAP.put(0x108, "8");
        KEYCODE_NAME_MAP.put(0x109, "9");
        KEYCODE_NAME_MAP.put(0x110, "MOUSE");
        KEYCODE_NAME_MAP.put(0x110, "LEFT");
        KEYCODE_NAME_MAP.put(0x111, "RIGHT");
        KEYCODE_NAME_MAP.put(0x112, "MIDDLE");
        KEYCODE_NAME_MAP.put(0x113, "SIDE");
        KEYCODE_NAME_MAP.put(0x114, "EXTRA");
        KEYCODE_NAME_MAP.put(0x115, "FORWARD");
        KEYCODE_NAME_MAP.put(0x116, "BACK");
        KEYCODE_NAME_MAP.put(0x117, "TASK");
        KEYCODE_NAME_MAP.put(0x120, "JOYSTICK");
        KEYCODE_NAME_MAP.put(0x120, "TRIGGER");
        KEYCODE_NAME_MAP.put(0x121, "THUMB");
        KEYCODE_NAME_MAP.put(0x122, "THUMB2");
        KEYCODE_NAME_MAP.put(0x123, "TOP");
        KEYCODE_NAME_MAP.put(0x124, "TOP2");
        KEYCODE_NAME_MAP.put(0x125, "PINKIE");
        KEYCODE_NAME_MAP.put(0x126, "BASE");
        KEYCODE_NAME_MAP.put(0x127, "BASE2");
        KEYCODE_NAME_MAP.put(0x128, "BASE3");
        KEYCODE_NAME_MAP.put(0x129, "BASE4");
        KEYCODE_NAME_MAP.put(0x12a, "BASE5");
        KEYCODE_NAME_MAP.put(0x12b, "BASE6");
        KEYCODE_NAME_MAP.put(0x12f, "DEAD");
        KEYCODE_NAME_MAP.put(0x130, "GAMEPAD");
        KEYCODE_NAME_MAP.put(0x130, "SOUTH");
        KEYCODE_NAME_MAP.put(0x131, "EAST");
        KEYCODE_NAME_MAP.put(0x132, "C");
        KEYCODE_NAME_MAP.put(0x133, "NORTH");
        KEYCODE_NAME_MAP.put(0x134, "WEST");
        KEYCODE_NAME_MAP.put(0x135, "Z");
        KEYCODE_NAME_MAP.put(0x136, "TL");
        KEYCODE_NAME_MAP.put(0x137, "TR");
        KEYCODE_NAME_MAP.put(0x138, "TL2");
        KEYCODE_NAME_MAP.put(0x139, "TR2");
        KEYCODE_NAME_MAP.put(0x13a, "SELECT");
        KEYCODE_NAME_MAP.put(0x13b, "START");
        KEYCODE_NAME_MAP.put(0x13c, "MODE");
        KEYCODE_NAME_MAP.put(0x13d, "THUMBL");
        KEYCODE_NAME_MAP.put(0x13e, "THUMBR");
        KEYCODE_NAME_MAP.put(0x140, "DIGI");
        KEYCODE_NAME_MAP.put(0x140, "TOOL_PEN");
        KEYCODE_NAME_MAP.put(0x141, "TOOL_RUBBER");
        KEYCODE_NAME_MAP.put(0x142, "TOOL_BRUSH");
        KEYCODE_NAME_MAP.put(0x143, "TOOL_PENCIL");
        KEYCODE_NAME_MAP.put(0x144, "TOOL_AIRBRUSH");
        KEYCODE_NAME_MAP.put(0x145, "TOOL_FINGER");
        KEYCODE_NAME_MAP.put(0x146, "TOOL_MOUSE");
        KEYCODE_NAME_MAP.put(0x147, "TOOL_LENS");
        KEYCODE_NAME_MAP.put(0x148, "TOOL_QUINTTAP");
        KEYCODE_NAME_MAP.put(0x149, "STYLUS3");
        KEYCODE_NAME_MAP.put(0x14a, "TOUCH");
        KEYCODE_NAME_MAP.put(0x14b, "STYLUS");
        KEYCODE_NAME_MAP.put(0x14c, "STYLUS2");
        KEYCODE_NAME_MAP.put(0x14d, "TOOL_DOUBLETAP");
        KEYCODE_NAME_MAP.put(0x14e, "TOOL_TRIPLETAP");
        KEYCODE_NAME_MAP.put(0x14f, "TOOL_QUADTAP");
        KEYCODE_NAME_MAP.put(0x150, "WHEEL");
        KEYCODE_NAME_MAP.put(0x150, "GEAR_DOWN");
        KEYCODE_NAME_MAP.put(0x151, "GEAR_UP");
        KEYCODE_NAME_MAP.put(0x220, "DPAD_UP");
        KEYCODE_NAME_MAP.put(0x221, "DPAD_DOWN");
        KEYCODE_NAME_MAP.put(0x222, "DPAD_LEFT");
        KEYCODE_NAME_MAP.put(0x223, "DPAD_RIGHT");
        KEYCODE_NAME_MAP.put(0x2c0, "TRIGGER_HAPPY");
        KEYCODE_NAME_MAP.put(0x2c0, "TRIGGER_HAPPY1");
        KEYCODE_NAME_MAP.put(0x2c1, "TRIGGER_HAPPY2");
        KEYCODE_NAME_MAP.put(0x2c2, "TRIGGER_HAPPY3");
        KEYCODE_NAME_MAP.put(0x2c3, "TRIGGER_HAPPY4");
        KEYCODE_NAME_MAP.put(0x2c4, "TRIGGER_HAPPY5");
        KEYCODE_NAME_MAP.put(0x2c5, "TRIGGER_HAPPY6");
        KEYCODE_NAME_MAP.put(0x2c6, "TRIGGER_HAPPY7");
        KEYCODE_NAME_MAP.put(0x2c7, "TRIGGER_HAPPY8");
        KEYCODE_NAME_MAP.put(0x2c8, "TRIGGER_HAPPY9");
        KEYCODE_NAME_MAP.put(0x2c9, "TRIGGER_HAPPY10");
        KEYCODE_NAME_MAP.put(0x2ca, "TRIGGER_HAPPY11");
        KEYCODE_NAME_MAP.put(0x2cb, "TRIGGER_HAPPY12");
        KEYCODE_NAME_MAP.put(0x2cc, "TRIGGER_HAPPY13");
        KEYCODE_NAME_MAP.put(0x2cd, "TRIGGER_HAPPY14");
        KEYCODE_NAME_MAP.put(0x2ce, "TRIGGER_HAPPY15");
        KEYCODE_NAME_MAP.put(0x2cf, "TRIGGER_HAPPY16");
        KEYCODE_NAME_MAP.put(0x2d0, "TRIGGER_HAPPY17");
        KEYCODE_NAME_MAP.put(0x2d1, "TRIGGER_HAPPY18");
        KEYCODE_NAME_MAP.put(0x2d2, "TRIGGER_HAPPY19");
        KEYCODE_NAME_MAP.put(0x2d3, "TRIGGER_HAPPY20");
        KEYCODE_NAME_MAP.put(0x2d4, "TRIGGER_HAPPY21");
        KEYCODE_NAME_MAP.put(0x2d5, "TRIGGER_HAPPY22");
        KEYCODE_NAME_MAP.put(0x2d6, "TRIGGER_HAPPY23");
        KEYCODE_NAME_MAP.put(0x2d7, "TRIGGER_HAPPY24");
        KEYCODE_NAME_MAP.put(0x2d8, "TRIGGER_HAPPY25");
        KEYCODE_NAME_MAP.put(0x2d9, "TRIGGER_HAPPY26");
        KEYCODE_NAME_MAP.put(0x2da, "TRIGGER_HAPPY27");
        KEYCODE_NAME_MAP.put(0x2db, "TRIGGER_HAPPY28");
        KEYCODE_NAME_MAP.put(0x2dc, "TRIGGER_HAPPY29");
        KEYCODE_NAME_MAP.put(0x2dd, "TRIGGER_HAPPY30");
        KEYCODE_NAME_MAP.put(0x2de, "TRIGGER_HAPPY31");
        KEYCODE_NAME_MAP.put(0x2df, "TRIGGER_HAPPY32");
        KEYCODE_NAME_MAP.put(0x2e0, "TRIGGER_HAPPY33");
        KEYCODE_NAME_MAP.put(0x2e1, "TRIGGER_HAPPY34");
        KEYCODE_NAME_MAP.put(0x2e2, "TRIGGER_HAPPY35");
        KEYCODE_NAME_MAP.put(0x2e3, "TRIGGER_HAPPY36");
        KEYCODE_NAME_MAP.put(0x2e4, "TRIGGER_HAPPY37");
        KEYCODE_NAME_MAP.put(0x2e5, "TRIGGER_HAPPY38");
        KEYCODE_NAME_MAP.put(0x2e6, "TRIGGER_HAPPY39");
        KEYCODE_NAME_MAP.put(0x2e7, "TRIGGER_HAPPY40");
    }

    public final String source;
    public final int keycode;
    public final boolean isKeydown;

    public static final Parcelable.Creator<KeypressEvent> CREATOR =
        new Parcelable.Creator<KeypressEvent>() {
            public KeypressEvent createFromParcel(Parcel in) {
                return new KeypressEvent(in);
            }

            public KeypressEvent[] newArray(int size) {
                return new KeypressEvent[size];
            }
        };

    public KeypressEvent(Parcel in) {
        source = in.readString();
        keycode = in.readInt();
        isKeydown = (in.readInt() != 0);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(source);
        dest.writeInt(keycode);
        dest.writeInt(isKeydown ? 1 : 0);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof KeypressEvent) {
            KeypressEvent other = (KeypressEvent)o;
            return other.source.equals(source) &&
                    other.keycode == keycode &&
                    other.isKeydown == isKeydown;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, keycode, isKeydown);
    }

    @Override
    public String toString() {
        return"Event{source = " + source + ", keycode = " + keycode +
                ", isKeydown = " + isKeydown + "}";
    }

    public String keycodeToString() {
        return keycodeToString(keycode);
    }

    /**
     * Translates a key code from keventreader into a string.
     * @param keycode Key code from a keventreader KeypressEvent.
     * @return String String label corresponding to keycode, if available. If not, String with
     *     hexidecimal representation of keycode.
     */
    public static String keycodeToString(int keycode) {
        String ret = KEYCODE_NAME_MAP.get(keycode);
        return ret != null ? ret : "0x" + Integer.toHexString(keycode);
    }
}
