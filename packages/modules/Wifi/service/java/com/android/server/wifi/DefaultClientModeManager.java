/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.wifi;

import android.annotation.Nullable;
import android.os.WorkSource;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * This is used for creating a public {@link ClientModeManager} instance when wifi is off.
 *
 * Note: this class is currently a singleton as it has no state.
 */
public class DefaultClientModeManager implements ClientModeManager, ClientModeDefaults {

    private static final long ID = -1;

    @Override
    public void stop() {
        throw new IllegalStateException();
    }

    @Override
    @Nullable public ClientRole getRole() {
        return null;
    }

    @Override
    @Nullable public ClientRole getPreviousRole() {
        return null;
    }

    @Override
    public long getLastRoleChangeSinceBootMs() {
        return 0;
    }

    @Override
    public String getInterfaceName() {
        return null;
    }

    @Override
    public WorkSource getRequestorWs() {
        return null;
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) { }

    @Override
    public void enableVerboseLogging(boolean verbose) { }

    @Override
    public long getId() {
        return ID;
    }

    @Override
    public String toString() {
        return "DefaultClientModeManager{id=" + getId()
                + " iface=" + getInterfaceName()
                + " role=" + getRole()
                + "}";
    }

    @Override
    public long getSupportedFeatures() {
        return 0L;
    }

    @Override
    public boolean isWifiStandardSupported(int standard) {
        return false;
    }
}
