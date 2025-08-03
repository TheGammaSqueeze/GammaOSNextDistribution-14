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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import androidx.lifecycle.Observer;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.qc.QCItem;
import com.android.car.qc.provider.BaseLocalQCProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

@RunWith(AndroidJUnit4.class)
public class LocalQCControllerTest extends BaseQCControllerTestCase<LocalQCController> {

    private LocalQCController mController;
    private BaseLocalQCProvider mProvider;

    @Override
    protected LocalQCController getController() {
        if (mController == null) {
            mProvider = mock(BaseLocalQCProvider.class);
            mController = new LocalQCController(mContext, mProvider);
        }
        return mController;
    }

    @Test
    public void onCreate_setsProviderNotifier() {
        getController(); // instantiate
        verify(mProvider).setNotifier(any());
    }

    @Test
    public void onBind_updatesQCItem() {
        Observer<QCItem> observer = mock(Observer.class);
        LocalQCController spiedController = spy(getController());
        spiedController.addObserver(observer);
        Mockito.reset(mProvider);
        spiedController.bind();
        verify(mProvider).getQCItem();
        verify(spiedController).onQCItemUpdated(any());
    }

    @Test
    public void updateListening_updatesProviderListening() {
        Observer<QCItem> observer = mock(Observer.class);
        getController().addObserver(observer);
        getController().listen(true);
        verify(mProvider).shouldListen(true);
        getController().listen(false);
        verify(mProvider).shouldListen(false);
    }

    @Test
    public void updateListening_listen_updatesQCItem() {
        Observer<QCItem> observer = mock(Observer.class);
        LocalQCController spiedController = spy(getController());
        spiedController.addObserver(observer);
        Mockito.reset(mProvider);
        spiedController.listen(true);
        verify(mProvider).getQCItem();
        verify(spiedController).onQCItemUpdated(any());
    }

    @Test
    public void onDestroy_callsProviderDestroy() {
        getController().destroy();
        verify(mProvider).onDestroy();
    }
}
