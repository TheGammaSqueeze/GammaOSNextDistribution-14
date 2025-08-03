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

package com.android.car.qc.controller;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.Context;

import androidx.lifecycle.Observer;
import androidx.test.core.app.ApplicationProvider;

import com.android.car.qc.QCItem;
import com.android.car.qc.QCTile;

import org.junit.Test;

public abstract class BaseQCControllerTestCase<T extends BaseQCController> {

    protected final Context mContext = spy(ApplicationProvider.getApplicationContext());

    protected abstract T getController();

    @Test
    public void listen_updateListeningCalled() {
        T spiedController = spy(getController());
        spiedController.listen(true);
        verify(spiedController).updateListening();
    }

    @Test
    public void addObserver_updateListeningCalled() {
        Observer<QCItem> observer = mock(Observer.class);
        T spiedController = spy(getController());
        spiedController.addObserver(observer);
        verify(spiedController).updateListening();
    }

    @Test
    public void removeObserver_updateListeningCalled() {
        Observer<QCItem> observer = mock(Observer.class);
        T spiedController = spy(getController());
        spiedController.removeObserver(observer);
        verify(spiedController).updateListening();
    }

    @Test
    public void onQCItemUpdated_observersNotified() {
        Observer<QCItem> observer = mock(Observer.class);
        getController().addObserver(observer);
        getController().onQCItemUpdated(new QCTile.Builder().build());
        verify(observer).onChanged(any(QCItem.class));
    }

    @Test
    public void onDestroy_cleanUpController() {
        Observer<QCItem> observer = mock(Observer.class);
        getController().addObserver(observer);
        getController().listen(true);
        getController().destroy();
        assertThat(getController().mObservers.size()).isEqualTo(0);
        assertThat(getController().mShouldListen).isFalse();
    }
}
