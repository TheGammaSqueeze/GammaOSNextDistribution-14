/*
 * Copyright (C) 2022, The Android Open Source Project
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

import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DelegatorTests {
  static byte kFakeByte = 16;
  static byte kExpectedByte = 12;

  private class Custom extends ITestService.Delegator {
    public Custom(ITestService impl) { super(impl); }
    @Override
    public byte RepeatByte(byte token) throws RemoteException {
      return kFakeByte;
    }
  }

  @Test
  public void testDelegator() throws RemoteException {
    IBinder binder = ServiceManager.waitForService(ITestService.class.getName());
    assertNotNull(binder);
    ITestService service = ITestService.Stub.asInterface(binder);
    ITestService.Delegator delegator = new ITestService.Delegator(service);
    assertThat(delegator.RepeatByte(kExpectedByte), is(kExpectedByte));
  }

  @Test
  public void testCustomDelegator() throws RemoteException {
    IBinder binder = ServiceManager.waitForService(ITestService.class.getName());
    assertNotNull(binder);
    ITestService service = ITestService.Stub.asInterface(binder);
    ITestService.Delegator custom = new Custom(service);
    assertThat(custom.RepeatByte(kExpectedByte), is(kFakeByte));
  }

  @Test
  public void testAsBinderAsInterface() throws RemoteException {
    IBinder binder = ServiceManager.waitForService(ITestService.class.getName());
    assertNotNull(binder);
    ITestService service = ITestService.Stub.asInterface(binder);
    ITestService.Delegator delegator = new ITestService.Delegator(service);
    assertThat(ITestService.Delegator.asInterface(delegator.asBinder()).RepeatByte(kExpectedByte),
        is(kExpectedByte));
  }
}
