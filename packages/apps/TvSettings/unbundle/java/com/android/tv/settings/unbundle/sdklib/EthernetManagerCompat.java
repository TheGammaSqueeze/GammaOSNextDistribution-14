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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.EthernetManager;
import android.net.EthernetNetworkUpdateRequest;
import android.net.IpConfiguration;
import android.net.NetworkCapabilities;

import java.util.concurrent.Executor;

public class EthernetManagerCompat {
    public static final int STATE_LINK_UP = EthernetManager.STATE_LINK_UP;
    private final EthernetManager mEthernetManager;
    private EthernetManager.InterfaceStateListener mInterfaceStateListener;

    public interface InterfaceStateListenerCompat {
        void onInterfaceStateChanged(@NonNull String iface, int state,
                int role, @Nullable IpConfiguration configuration);
    }

    public static class EthernetNetworkUpdateRequestCompat {
        private final EthernetNetworkUpdateRequest mEthernetNetworkUpdateRequest;

        public EthernetNetworkUpdateRequestCompat(IpConfiguration ipConfiguration,
                NetworkCapabilities nc) {
            mEthernetNetworkUpdateRequest = new EthernetNetworkUpdateRequest.Builder()
                    .setIpConfiguration(ipConfiguration)
                    .setNetworkCapabilities(nc)
                    .build();
        }
    }

    public EthernetManagerCompat(Context context) {
        mEthernetManager = context.getSystemService(EthernetManager.class);
    }

    public void removeInterfaceStateListener() {
        mEthernetManager.removeInterfaceStateListener(mInterfaceStateListener);
    }

    public void addInterfaceStateListener(Executor executor,
            InterfaceStateListenerCompat listenerCompat) {
        mInterfaceStateListener = (iface, state, role, configuration) ->
                listenerCompat.onInterfaceStateChanged(iface, state, role, configuration);
        mEthernetManager.addInterfaceStateListener(executor, mInterfaceStateListener);
    }

    public void updateConfiguration(String iface, EthernetNetworkUpdateRequestCompat requestCompat,
            Executor executor) {
        mEthernetManager.updateConfiguration(iface, requestCompat.mEthernetNetworkUpdateRequest,
                executor, null);
    }
}
