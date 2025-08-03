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

package android.view;

import com.android.layoutlib.bridge.impl.DelegateManager;
import com.android.tools.layoutlib.annotations.LayoutlibDelegate;

import android.os.MessageQueue;
import android.view.DisplayEventReceiver.VsyncEventData;

import java.lang.ref.WeakReference;

import libcore.util.NativeAllocationRegistry_Delegate;

public class DisplayEventReceiver_Delegate {
    private static final DelegateManager<DisplayEventReceiver_Delegate> sManager =
            new DelegateManager<>(DisplayEventReceiver_Delegate.class);
    private static long sFinalizer = -1;

    @LayoutlibDelegate
    /*package*/ static long nativeInit(WeakReference<DisplayEventReceiver> receiver,
            WeakReference<VsyncEventData> vsyncEventData, MessageQueue messageQueue,
            int vsyncSource, int eventRegistration, long layerHandle) {
        return sManager.addNewDelegate(new DisplayEventReceiver_Delegate());
    }

    @LayoutlibDelegate
    /*package*/ static long nativeGetDisplayEventReceiverFinalizer() {
        synchronized (DisplayEventReceiver_Delegate.class) {
            if (sFinalizer == -1) {
                sFinalizer = NativeAllocationRegistry_Delegate.createFinalizer(sManager::removeJavaReferenceFor);
            }
        }
        return sFinalizer;
    }
}
