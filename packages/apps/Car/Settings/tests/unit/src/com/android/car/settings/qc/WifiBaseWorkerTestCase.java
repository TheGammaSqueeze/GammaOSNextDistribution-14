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

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.lifecycle.Lifecycle;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public abstract class WifiBaseWorkerTestCase<E extends WifiBaseWorker> {
    protected final Context mContext = ApplicationProvider.getApplicationContext();
    private E mWorker;

    protected abstract E getWorker();

    @Before
    @UiThreadTest
    public void setUp() {
        mWorker = getWorker();
    }

    @Test
    public void onCreate_lifecycleCreated() {
        assertThat(mWorker.getLifecycle().getCurrentState()).isEqualTo(Lifecycle.State.CREATED);
    }

    @Test
    @UiThreadTest
    public void onSubscribe_lifecycleResumed() {
        mWorker.onQCItemSubscribe();
        assertThat(mWorker.getLifecycle().getCurrentState()).isEqualTo(Lifecycle.State.RESUMED);
    }

    @Test
    @UiThreadTest
    public void onUnsubscribe_lifecycleCreated() {
        mWorker.onQCItemSubscribe();
        mWorker.onQCItemUnsubscribe();
        assertThat(mWorker.getLifecycle().getCurrentState()).isEqualTo(Lifecycle.State.CREATED);
    }

    @Test
    @UiThreadTest
    public void onClose_lifecycleDestroyed() throws IOException {
        mWorker.onQCItemSubscribe();
        mWorker.close();
        assertThat(mWorker.getLifecycle().getCurrentState()).isEqualTo(Lifecycle.State.DESTROYED);
    }
}
