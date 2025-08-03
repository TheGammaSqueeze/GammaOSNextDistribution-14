/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.pm;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class WindowDumpParserTest {
    private static final String WINDOW_DUMP = "WINDOW MANAGER WINDOWS (dumpsys window windows)\n"
            + "  Window #0 Window{ccae0fb u10 com.app1}:\n"
            + "    mDisplayId=0 rootTaskId=1000006 mSession=Session{ef1bfd2 2683:u10a10088} "
            + "mClient=android.os.BinderProxy@46bd28a\n"
            + "    mOwnerUid=1010088 showForAllUsers=false package=com.app1 "
            + "appop=SYSTEM_ALERT_WINDOW\n"
            + "    mAttrs={(0,0)(0x0) gr=TOP RIGHT CENTER sim={adjust=pan} ty=APPLICATION_OVERLAY"
            + " fmt=TRANSPARENT\n"
            + "    isOnScreen=true\n"
            + "    isVisible=true\n"

            + "  Window #1 Window{17aaef4 u0 App 2}:\n"
            + "    mDisplayId=1 rootTaskId=1000006 mSession=Session{629ba4e 2235:u0a10120} "
            + "mClient=android.os.Binderproxy@3f3ea06\n"
            + "    mOwnerUid=10120 showForAllUsers=true package=com.app2 appop=NONE\n"
            + "    mAttrs={(24,0)(84x419) gr=BOTTOM RIGHT CENTER sim={adjust=pan} "
            + "ty=DISPLAY_OVERLAY fmt=TRANSLUCENT\n"
            + "      fl=NOT_FOCUSABLE LAYOUT_NO_LIMITS HARDWARE_ACCELERATED\n"
            + "      pfl=NO_MOVE_ANIMATION USE_BLAST INSET_PARENT_FRAME_BY_IME\n"
            + "      bhv=DEFAULT\n"
            + "      fitTypes=STATUS_BARS NAVIGATION_BARS CAPTION_BAR}\n"
            + "    Requested w=1080 h=1920 mLayoutSeq=154\n"
            + "    mBaseLayer=21000 mSubLayer=0    mToken=AppWindowToken{546fe66 token=Token"
            + "{8b7a6c1 ActivityRecord{2789ba8 u0 com.app2/.MainActivity t5}}}\n"
            + "    mAppToken=AppWindowToken{546fe66 token=Token{8b7a6c1 ActivityRecord{2789ba8 u0"
            + " com.app2/.MainActivity t5}}}\n"

            + ".BinderProxy@3f3ea06}\n"
            + "    Frames: containing=[0,0][1080,600] parent=[0,0][1080,600] display=[-10000,"
            + "-10000][10000,10000]\n"
            + "    mFrame=[972,181][1056,600] last=[0,0][0,0]\n"
            + "     surface=[0,0][0,0]\n"
            + "    WindowStateAnimator{8edd4a7 HVAC Passenger Temp}:\n"
            + "      mDrawState=NO_SURFACE       mLastHidden=false\n"
            + "      mEnterAnimationPending=false      mSystemDecorRect=[0,0][0,0]\n"
            + "      mShownAlpha=0.0 mAlpha=1.0 mLastAlpha=0.0\n"
            + "    mForceSeamlesslyRotate=false seamlesslyRotate: pending=null "
            + "finishedFrameNumber=0\n"
            + "    isOnScreen=false\n"
            + "    isVisible=false\n"

            + "  Window #2 Window{1c5571 u0 HVAC Driver Temp}:\n"
            + "    mDisplayId=1 rootTaskId=1000006 mSession=Session{629ba4e 2235:u0a10120} "
            + "mClient=android.os.BinderProxy@99ccafb\n"
            + "    mOwnerUid=10120 showForAllUsers=true package=com.app2 appop=NONE\n"
            + "    mAttrs={(24,0)(84x419) gr=BOTTOM LEFT CENTER sim={adjust=pan} "
            + "ty=DISPLAY_OVERLAY fmt=TRANSLUCENT\n"
            + "      fl=NOT_FOCUSABLE LAYOUT_NO_LIMITS HARDWARE_ACCELERATED\n"
            + "      pfl=NO_MOVE_ANIMATION USE_BLAST INSET_PARENT_FRAME_BY_IME\n"
            + "      bhv=DEFAULT\n"
            + "      fitTypes=STATUS_BARS NAVIGATION_BARS CAPTION_BAR}\n"
            + "    Requested w=84 h=419 mLayoutSeq=143\n"
            + "    mBaseLayer=21000 mSubLayer=0    mToken=ActivityRecord{b44066 u10 com.app2/"
            + "SecondActivity t1000031}\n"
            + "    mActivityRecord=ActivityRecord{b44066 u10 com.app2/SecondActivity t1000031}\n"
            + ".BinderProxy@99ccafb}\n"
            + "    mViewVisibility=0x4 mHaveFrame=true mObscured=false\n"
            + "    mGivenContentInsets=[0,0][0,0] mGivenVisibleInsets=[0,0][0,0]\n"
            + "    isOnScreen=false\n"
            + "    isVisible=false\n"

            + "  Window #3 Window{1c5571 u0 HVAC Driver Temp}:\n"
            + "    mDisplayId=2 rootTaskId=1000006 mSession=Session{629ba4e 2235:u0a10120} "
            + "mClient=android.os.BinderProxy@99ccafb\n"
            + "    mOwnerUid=10120 showForAllUsers=true package=com.app3 appop=NONE\n"
            + "    mAttrs={(24,0)(84x419) gr=BOTTOM LEFT CENTER sim={adjust=pan} "
            + "ty=DISPLAY_OVERLAY fmt=TRANSLUCENT\n"
            + "      fl=NOT_FOCUSABLE LAYOUT_NO_LIMITS HARDWARE_ACCELERATED\n"
            + "      pfl=NO_MOVE_ANIMATION USE_BLAST INSET_PARENT_FRAME_BY_IME\n"
            + "      bhv=DEFAULT\n"
            + "      fitTypes=STATUS_BARS NAVIGATION_BARS CAPTION_BAR}\n"
            + "    Requested w=84 h=419 mLayoutSeq=143\n"
            + "    mBaseLayer=291000 mSubLayer=0    mToken=WindowToken{6bd1718 android.os"
            + "    mActivityRecord=ActivityRecord{a3f066 u10 com.app3/MainActivity t1000031}\n"
            + ".BinderProxy@99ccafb}\n"
            + "    mViewVisibility=0x4 mHaveFrame=true mObscured=false\n"
            + "    mGivenContentInsets=[0,0][0,0] mGivenVisibleInsets=[0,0][0,0]\n"
            + "    isOnScreen=false\n"
            + "    isVisible=false\n"

            + "  Window #4 Window{1c5571 u0 HVAC Driver Temp}:\n"
            + "    mDisplayId=2 rootTaskId=1000006 mSession=Session{629ba4e 2235:u0a10120} "
            + "mClient=android.os.BinderProxy@99ccafb\n"
            + "    mOwnerUid=10120 showForAllUsers=true package=com.app3 appop=NONE\n"
            + "    mAttrs={(24,0)(84x419) gr=BOTTOM LEFT CENTER sim={adjust=pan} "
            + "ty=APPLICATION_STARTING fmt=TRANSLUCENT\n"
            + "      fl=NOT_FOCUSABLE LAYOUT_NO_LIMITS HARDWARE_ACCELERATED\n"
            + "      pfl=NO_MOVE_ANIMATION USE_BLAST INSET_PARENT_FRAME_BY_IME\n"
            + "      bhv=DEFAULT\n"
            + "      fitTypes=STATUS_BARS NAVIGATION_BARS CAPTION_BAR}\n"
            + "    Requested w=84 h=419 mLayoutSeq=143\n"
            + "    mBaseLayer=291000 mSubLayer=0    mToken=WindowToken{6bd1718 android.os"
            + ".BinderProxy@99ccafb}\n"
            + "    mViewVisibility=0x4 mHaveFrame=true mObscured=false\n"
            + "    mGivenContentInsets=[0,0][0,0] mGivenVisibleInsets=[0,0][0,0]\n"
            + "    isOnScreen=false\n"
            + "    isVisible=false\n";

    private static final WindowDumpParser.Window APP_1_WINDOW = new WindowDumpParser.Window(
            "com.app1", 0, null);
    private static final WindowDumpParser.Window APP_2_WINDOW = new WindowDumpParser.Window(
            "com.app2", 1, "2789ba8 u0 com.app2/.MainActivity t5");
    private static final WindowDumpParser.Window APP_2_WINDOW_2 = new WindowDumpParser.Window(
            "com.app2", 1, "b44066 u10 com.app2/SecondActivity t1000031");
    private static final WindowDumpParser.Window APP_3_WINDOW = new WindowDumpParser.Window(
            "com.app3", 2, "a3f066 u10 com.app3/MainActivity t1000031");

    @Test
    public void testWindowDumpParsing() {
        assertThat(WindowDumpParser.getParsedAppWindows(WINDOW_DUMP, "com.app1")).containsExactly(
                APP_1_WINDOW);
        assertThat(WindowDumpParser.getParsedAppWindows(WINDOW_DUMP, "com.app2")).containsExactly(
                APP_2_WINDOW, APP_2_WINDOW_2);
        assertThat(WindowDumpParser.getParsedAppWindows(WINDOW_DUMP, "com.app3")).containsExactly(
                APP_3_WINDOW);
    }
}
