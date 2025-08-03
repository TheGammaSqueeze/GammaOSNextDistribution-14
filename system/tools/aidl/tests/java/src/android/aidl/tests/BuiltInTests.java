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
import static org.junit.Assert.assertTrue;

import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BuiltInTests {
  private IBinder mBinder;

  @Before
  public void setUp() throws RemoteException {
    mBinder = ServiceManager.waitForService(ITestService.class.getName());
    assertNotNull(mBinder);
  }

  @Test
  public void testPing() {
    assertTrue(mBinder.pingBinder());
  }

  @Test
  public void testInterfaceDescriptor() throws RemoteException {
    assertThat(mBinder.getInterfaceDescriptor(), is("android.aidl.tests.ITestService"));
  }
}
