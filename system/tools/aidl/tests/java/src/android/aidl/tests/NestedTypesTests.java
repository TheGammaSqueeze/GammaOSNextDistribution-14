/*
 * Copyright (C) 2021, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.aidl.tests;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;

import android.aidl.tests.nested.INestedService;
import android.aidl.tests.nested.ParcelableWithNested;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class NestedTypesTests {
  @Test
  public void testUseNestedTypes() throws RemoteException {
    IBinder binder = ServiceManager.waitForService(INestedService.class.getName());
    assertNotNull(binder);
    INestedService nestedService = INestedService.Stub.asInterface(binder);
    assertNotNull(nestedService);

    // OK -> NOT_OK
    ParcelableWithNested p = new ParcelableWithNested();
    p.status = ParcelableWithNested.Status.OK;
    INestedService.Result result = nestedService.flipStatus(p);
    assertThat(result, is(ParcelableWithNested.Status.NOT_OK));

    // NOT_OK -> OK with nested callback interface
    class Callback extends INestedService.ICallback.Stub {
      byte received = ParcelableWithNested.Status.NOT_OK;
      @Override
      public void done(byte st) {
        received = st;
      }
    }
    Callback cb = new Callback();
    nestedService.flipStatusWithCallback(p.status, cb);
    assertThat(cb.received, is(ParcelableWithNested.Status.OK));
  }
}
