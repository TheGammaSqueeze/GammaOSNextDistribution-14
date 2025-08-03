/*
 * Copyright (C) 2020, The Android Open Source Project
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
import static org.junit.Assert.assertTrue;

import android.aidl.versioned.tests.BazUnion;
import android.aidl.versioned.tests.Foo;
import android.aidl.versioned.tests.IFooInterface;
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
public class TestVersionedInterface {
    private IFooInterface service;

    @Before
    public void setUp() {
        IBinder binder = ServiceManager.waitForService(IFooInterface.class.getName());
        assertNotNull(binder);
        service = IFooInterface.Stub.asInterface(binder);
        assertNotNull(service);
    }

    @Test
    public void testGetInterfaceVersion() throws RemoteException {
        assertThat(service.getInterfaceVersion(), is(1));
    }

    @Test
    public void testGetInterfaceHash() throws RemoteException {
      assertThat(service.getInterfaceHash(), is("9e7be1859820c59d9d55dd133e71a3687b5d2e5b"));
    }

    @Rule public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testUnimplementedMethodTriggersException() throws RemoteException {
      expectedException.expect(RemoteException.class);
      expectedException.expectMessage("Method newApi is unimplemented.");

      service.newApi();
    }

    @Test
    public void testOldServerAcceptsUnionWithOldField() throws RemoteException {
      assertThat(service.acceptUnionAndReturnString(BazUnion.intNum(42)), is("42"));
    }

    @Test
    public void testUnknownUnionFieldTriggersException() throws RemoteException {
      expectedException.expect(IllegalArgumentException.class);

      service.acceptUnionAndReturnString(BazUnion.longNum(42L));
    }

    @Test
    public void testArrayOfPacelableWithNewField() throws RemoteException {
      Foo[] foos = new Foo[42];
      for (int i = 0; i < foos.length; i++) {
        foos[i] = new Foo();
      }
      int length = service.returnsLengthOfFooArray(foos);
      assertThat(length, is(foos.length));
    }

    @Test
    public void testReadDataCorrectlyAfterParcelableWithNewField() throws RemoteException {
      Foo inFoo = new Foo();
      Foo inoutFoo = new Foo();
      inoutFoo.intDefault42 = 0;
      Foo outFoo = new Foo();
      outFoo.intDefault42 = 0;
      int ret = service.ignoreParcelablesAndRepeatInt(inFoo, inoutFoo, outFoo, 43);
      assertThat(ret, is(43));
      assertThat(inoutFoo.intDefault42, is(0));
      assertThat(outFoo.intDefault42, is(0));
    }

    @Test
    public void testDelegatorGetInterfaceVersion() throws RemoteException {
      IFooInterface.Delegator delegator = new IFooInterface.Delegator(service);
      assertThat(delegator.getInterfaceVersion(), is(1));
    }

    @Test
    public void testDelegatorGetInterfaceHash() throws RemoteException {
      IFooInterface.Delegator delegator = new IFooInterface.Delegator(service);
      assertThat(delegator.getInterfaceHash(), is("9e7be1859820c59d9d55dd133e71a3687b5d2e5b"));
    }
}
