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

package com.google.android.car.kitchensink.radio;

import android.content.Context;
import android.hardware.radio.RadioManager;

import com.android.car.broadcastradio.support.platform.ProgramSelectorExt;

final class AmFmProgramInfoAdapter extends ProgramInfoAdapter {
    AmFmProgramInfoAdapter(Context context, int layoutResourceId,
                           RadioManager.ProgramInfo[] programInfos,
                           RadioTunerFragment fragment) {
        super(context, layoutResourceId, programInfos, fragment);
    }

    @Override
    String getChannelDisplayName(int position) {
        return ProgramSelectorExt.getDisplayName(mProgramInfos[position].getSelector(),
                /* flags= */ 0);
    }
}
