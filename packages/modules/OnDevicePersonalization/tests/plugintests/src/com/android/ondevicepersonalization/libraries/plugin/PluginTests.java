/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.ondevicepersonalization.libraries.plugin;

import static org.junit.Assert.assertEquals;

import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PluginTests {
    @Test
    public void testFailureType() throws Exception {
        FailureType ft = FailureType.ERROR_LOADING_PLUGIN;

        Parcel parcel = Parcel.obtain();
        ft.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        FailureType ft2 = FailureType.CREATOR.createFromParcel(parcel);

        assertEquals(ft, ft2);
        assertEquals(0, ft2.describeContents());
    }

    @Test
    public void testPluginState() throws Exception {
        PluginState ps = PluginState.STATE_LOADED;

        Parcel parcel = Parcel.obtain();
        ps.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        PluginState ps2 = PluginState.CREATOR.createFromParcel(parcel);

        assertEquals(ps, ps2);
        assertEquals(0, ps2.describeContents());
    }
}
