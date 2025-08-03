/**
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.app.uiautomation.cts;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * A stub accessibility service to install for testing UiAutomation's effect on accessibility
 * services
 */
public class UiAutomationTestA11yService extends AccessibilityService {

    private static String LOG_TAG = "UiAutomationTest";

    private static final boolean VERBOSE = false;

    public static final Object sWaitObjectForConnectOrUnbind = new Object();

    public static UiAutomationTestA11yService sConnectedInstance;

    @Override
    public boolean onUnbind(Intent intent) {
        Log.v(LOG_TAG, "onUnbind() for " + this);
        synchronized (sWaitObjectForConnectOrUnbind) {
            sConnectedInstance = null;
            sWaitObjectForConnectOrUnbind.notifyAll();
        }
        return false;
    }

    @Override
    public void onDestroy() {
        Log.v(LOG_TAG, "onDestroy() for " + this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
        if (VERBOSE) {
            Log.v(LOG_TAG, "onAccessibilityEvent() " + this + ": " + accessibilityEvent);
        }
    }

    @Override
    public void onInterrupt() {
        Log.v(LOG_TAG, "onInterrupt() for " + this);
    }

    @Override
    protected void onServiceConnected() {
        Log.v(LOG_TAG, "onServiceConnected() for user " + getUserId() + " on " + this);
        synchronized (sWaitObjectForConnectOrUnbind) {
            sConnectedInstance = this;
            sWaitObjectForConnectOrUnbind.notifyAll();
        }
    }

    @Override
    public String toString() {
        StringBuilder string = new StringBuilder(getClass().getSimpleName())
                .append("{user=").append(getUserId());
        AccessibilityServiceInfo serviceInfo = getServiceInfo();
        if (serviceInfo == null) {
            string.append(", disconnected");
        } else {
            string.append(", serviceInfo=").append(serviceInfo.getId());
        }
        return string.append('}').toString();
    }

    public boolean isConnected() {
        try {
            if (getServiceInfo() == null) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
