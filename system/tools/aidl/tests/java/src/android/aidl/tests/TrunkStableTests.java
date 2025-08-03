/*
 * Copyright (C) 2023, The Android Open Source Project
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
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import android.aidl.test.trunk.ITrunkStableTest;
import android.aidl.test.trunk.ITrunkStableTest.IMyCallback;
import android.aidl.test.trunk.ITrunkStableTest.MyEnum;
import android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable;
import android.aidl.test.trunk.ITrunkStableTest.MyParcelable;
import android.aidl.test.trunk.ITrunkStableTest.MyUnion;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TrunkStableTests {
  private ITrunkStableTest service;
  // Java doesn't know about the build-time flag, so this test is based on the
  // reported version of the service. The cpp/ndk client tests will make sure
  // the reported version is due to the build-time flag.
  private int mRemoteVersion;
  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void setUp() throws RemoteException {
    IBinder binder = ServiceManager.waitForService(ITrunkStableTest.class.getName());
    assertNotNull(binder);
    service = ITrunkStableTest.Stub.asInterface(binder);
    assertNotNull(service);
    mRemoteVersion = service.getInterfaceVersion();
  }

  @Test
  public void testGetInterfaceVersion() throws RemoteException {
    if (mRemoteVersion == 1) {
      assertThat(service.getInterfaceVersion(), is(1));
      assertThat(ITrunkStableTest.VERSION, is(1));
    } else {
      assertThat(service.getInterfaceVersion(), is(2));
      assertThat(ITrunkStableTest.VERSION, is(2));
    }
  }

  @Test
  public void testGetInterfaceHash() throws RemoteException {
    if (mRemoteVersion == 1) {
      assertThat(service.getInterfaceHash(), is("88311b9118fb6fe9eff4a2ca19121de0587f6d5f"));
      assertThat(ITrunkStableTest.HASH, is("88311b9118fb6fe9eff4a2ca19121de0587f6d5f"));
    } else {
      assertThat(service.getInterfaceHash(), is("notfrozen"));
      assertThat(ITrunkStableTest.HASH, is("notfrozen"));
    }
  }

  @Test
  public void testRepeatParcelable() throws RemoteException {
    MyParcelable p1 = new MyParcelable();
    p1.a = 12;
    p1.b = 13;
    p1.c = 14;
    MyParcelable p2;
    p2 = service.repeatParcelable(p1);
    assertThat(p2.a, is(p1.a));
    assertThat(p2.b, is(p1.b));
    if (mRemoteVersion == 1) {
      assertTrue(p2.c != p1.c);
    } else {
      assertTrue(p2.c == p1.c);
    }
  }

  @Test
  public void testRepeatEnum() throws RemoteException {
    byte e1 = MyEnum.THREE;
    byte e2 = 0;
    e2 = service.repeatEnum(e1);
    assertThat(e2, is(e1));
  }

  @Test
  public void testRepeatUnion() throws RemoteException {
    MyUnion u1, u2;
    u1 = MyUnion.c(13);

    if (mRemoteVersion == 1) {
      // 'c' is a new field only in V2 and will throw the exception
      expectedException.expect(IllegalArgumentException.class);
    }
    u2 = service.repeatUnion(u1);
    if (mRemoteVersion != 1) {
      assertThat(u2.getC(), is(u1.getC()));
    }
  }

  @Test
  public void testRepeatOtherParcelable() throws RemoteException {
    MyOtherParcelable p1 = new MyOtherParcelable();
    p1.a = 12;
    p1.b = 13;
    MyOtherParcelable p2;

    if (mRemoteVersion == 1) {
      expectedException.expect(RemoteException.class);
    }
    p2 = service.repeatOtherParcelable(p1);
  }

  public static class MyCallback extends IMyCallback.Stub {
    @Override
    public MyParcelable repeatParcelable(MyParcelable input) throws android.os.RemoteException {
      repeatParcelableCalled = true;
      return input;
    }
    @Override
    public byte repeatEnum(byte input) throws android.os.RemoteException {
      repeatEnumCalled = true;
      return input;
    }
    @Override
    public ITrunkStableTest.MyUnion repeatUnion(MyUnion input) throws android.os.RemoteException {
      repeatUnionCalled = true;
      return input;
    }
    @Override
    public MyOtherParcelable repeatOtherParcelable(MyOtherParcelable input)
        throws android.os.RemoteException {
      repeatOtherParcelableCalled = true;
      return input;
    }
    @Override
    public final int getInterfaceVersion() {
      return super.VERSION;
    }

    @Override
    public final String getInterfaceHash() {
      return super.HASH;
    }
    boolean repeatParcelableCalled = false;
    boolean repeatEnumCalled = false;
    boolean repeatUnionCalled = false;
    boolean repeatOtherParcelableCalled = false;
  }
  @Test
  public void testCallMyCallback() throws RemoteException {
    MyCallback cb = new MyCallback();
    service.callMyCallback(cb);
    assertTrue(cb.repeatParcelableCalled);
    assertTrue(cb.repeatEnumCalled);
    assertTrue(cb.repeatUnionCalled);
    if (mRemoteVersion == 1) {
      assertTrue(!cb.repeatOtherParcelableCalled);
    } else {
      assertTrue(cb.repeatOtherParcelableCalled);
    }
  }
}
