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

package com.android.systemui;

import static org.mockito.Mockito.mock;

import android.content.Context;

import com.android.systemui.dagger.SysUIComponent;
import com.android.systemui.dagger.WMComponent;
import com.android.systemui.wm.MDSystemBarsController;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;

import java.util.Optional;

public class CarSystemUITestInitializer extends CarSystemUIInitializer {
    public CarSystemUITestInitializer(Context context) {
        super(context);
    }

    @Override
    protected SysUIComponent.Builder prepareSysUIComponentBuilder(
            SysUIComponent.Builder sysUIBuilder, WMComponent wm) {
        return ((CarSysUIComponent.Builder) sysUIBuilder).setRootTaskDisplayAreaOrganizer(
                Optional.of(mock(RootTaskDisplayAreaOrganizer.class)))
                .setMDSystemBarsController(Optional.of(mock(MDSystemBarsController.class)));
    }
}
