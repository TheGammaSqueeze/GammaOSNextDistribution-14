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

package com.android.car.settings.qc;

import android.content.Context;
import android.net.Uri;

import com.android.car.qc.QCItem;
import com.android.car.qc.QCTile;

public class TestSettingsQCItem extends SettingsQCItem {

    public TestSettingsQCItem(Context context) {
        super(context);
    }

    @Override
    QCItem getQCItem() {
        return new QCTile.Builder().build();
    }

    @Override
    Uri getUri() {
        return QCTestUtils.URI;
    }

    @Override
    Class<? extends SettingsQCBackgroundWorker> getBackgroundWorkerClass() {
        return TestSettingsQCBackgroundWorker.class;
    }
}
