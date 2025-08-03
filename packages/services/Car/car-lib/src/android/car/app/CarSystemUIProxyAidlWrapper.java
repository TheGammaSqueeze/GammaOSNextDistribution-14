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

package android.car.app;

import android.os.IBinder;
import android.os.RemoteException;

/**
 * This class wraps the client's {@link CarSystemUIProxy} implementation & facilitates the
 * communication with {@link ICarSystemUIProxy}.
 */
final class CarSystemUIProxyAidlWrapper extends ICarSystemUIProxy.Stub {
    private final CarSystemUIProxy mCarSystemUIProxy;

    CarSystemUIProxyAidlWrapper(CarSystemUIProxy carSystemUIProxy) {
        mCarSystemUIProxy = carSystemUIProxy;
    }

    @Override
    public ICarTaskViewHost createControlledCarTaskView(ICarTaskViewClient client) {
        return createCarTaskView(client);
    }

    @Override
    public ICarTaskViewHost createCarTaskView(ICarTaskViewClient client) {
        CarTaskViewHost carTaskViewHost =
                mCarSystemUIProxy.createCarTaskView(new CarTaskViewClient(client));

        IBinder.DeathRecipient clientDeathRecipient = new IBinder.DeathRecipient() {
            @Override
            public void binderDied() {
                carTaskViewHost.release();
                client.asBinder().unlinkToDeath(this, /* flags= */ 0);
            }
        };

        try {
            client.asBinder().linkToDeath(clientDeathRecipient, /* flags= */ 0);
        } catch (RemoteException ex) {
            throw new IllegalStateException(
                    "Linking to binder death failed for "
                            + "ICarTaskViewClient, the System UI might already died");
        }

        return new CarTaskViewHostAidlToImplAdapter(carTaskViewHost);
    }
}
