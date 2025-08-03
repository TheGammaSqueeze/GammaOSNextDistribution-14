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
package com.android.systemui.car.distantdisplay.activity.window;

import android.os.Handler;

import com.android.systemui.dagger.SysUISingleton;
import com.android.wm.shell.common.HandlerExecutor;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.TransactionPool;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;

/**
 * Dagger injection module for {@link ActivityWindowManager}
 */
@Module
public abstract class ActivityWindowModule {

    /**
     * Injects CarUiDistantDisplayActivityWindowController.
     */
    @Binds
    @IntoMap
    @ClassKey(ActivityWindowController.class)
    public abstract ActivityWindowController bindActivityWindowController(
            ActivityWindowController activityWindowController);

    @Provides
    @SysUISingleton
    static SyncTransactionQueue provideSyncTransactionQueue(TransactionPool pool,
            ShellExecutor mainExecutor) {
        return new SyncTransactionQueue(pool, mainExecutor);
    }

    @Provides
    @SysUISingleton
    static TransactionPool provideTransactionPool() {
        return new TransactionPool();
    }

    /**
     * Provide a SysUI main-thread Executor.
     */
    @Provides
    @SysUISingleton
    public static ShellExecutor provideShellExecutor(
            Handler sysuiMainHandler) {
        return new HandlerExecutor(sysuiMainHandler);
    }
}
