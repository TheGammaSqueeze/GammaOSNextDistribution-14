/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.go;

import android.content.Context;

import com.android.systemui.SystemUIInitializer;
import com.android.systemui.dagger.GlobalRootComponent;
import com.android.systemui.go.DaggerSystemUIGoGlobalRootComponent;

/**
 * Go variant {@link SystemUIInitializer}, that substitutes default {@link GlobalRootComponent} for
 * {@link SystemUIGoGlobalRootComponent}
 */
final public class SystemUIGoInitializer extends SystemUIInitializer {
    public SystemUIGoInitializer(Context context) {
        super(context);
    }

    @Override
    protected GlobalRootComponent.Builder getGlobalRootComponentBuilder() {
        return DaggerSystemUIGoGlobalRootComponent.builder();
    }
}
