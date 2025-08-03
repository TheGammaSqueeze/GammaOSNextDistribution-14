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

package com.chassis.car.ui.plugin.toolbar;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.ui.plugin.oemapis.toolbar.ImeSearchInterfaceOEMV1;
import com.android.car.ui.plugin.oemapis.toolbar.ImeSearchInterfaceOEMV2;

/**
 * Adapts an ImeSearchInterface for backwards compatibility with apps that use an older version of
 * car-ui-lib.
 */
public final class ImeSearchInterfaceProxy {
    /**
     * Converts an ImeSearchInterfaceOEMV2 from {@code ToolbarController#getImeSearchInterface} to
     * an ImeSearchInterfaceOEMV1 which must be returned from an {@code ToolbarControllerOEMV1}.
     */
    @NonNull
    public static ImeSearchInterfaceOEMV1 getImeSearchInterfaceV1(
            @NonNull ToolbarControllerImpl toolbarController) {
        ImeSearchInterfaceOEMV2 imeSearchInterface = toolbarController.getImeSearchInterface();
        return new ImeSearchInterfaceOEMV1() {
            @Override
            public void setSearchTextViewConsumer(
                    @Nullable java.util.function.Consumer<TextView> consumer) {
                imeSearchInterface.setSearchTextViewConsumer(
                        (TextView tv) -> consumer.accept(tv));
            }

            @Override
            public void setOnPrivateImeCommandListener(
                    @Nullable java.util.function.BiConsumer<String, Bundle> biConsumer) {
                imeSearchInterface.setOnPrivateImeCommandListener(
                        (String s, Bundle b) -> biConsumer.accept(s, b));
            }
        };
    }
}
