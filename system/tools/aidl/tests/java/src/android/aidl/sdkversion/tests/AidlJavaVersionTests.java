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

package android.aidl.sdkversion.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.aidl.sdkversion.ITestService;
import android.aidl.sdkversion.ITestService.TypedObject;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.internal.TextListener;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AidlJavaVersionTests {
  public static void main(String[] args) {
    JUnitCore junit = new JUnitCore();
    junit.addListener(new TextListener(System.out));
    Result result = junit.run(AidlJavaVersionTests.class);
    System.out.println(result.wasSuccessful() ? "TEST SUCCESS" : "TEST FAILURE");
  }

  private ITestService mService;

  @Before
  public void setUp() {
    IBinder binder = ServiceManager.waitForService(ITestService.class.getName());
    assertNotNull(binder);
    mService = ITestService.Stub.asInterface(binder);
    assertNotNull(mService);
  }

  @Test
  public void testBoolean() throws RemoteException {
    assertTrue(mService.RepeatBoolean(true));
    assertFalse(mService.RepeatBoolean(false));
  }

  @Test
  public void testTypedObject() throws RemoteException {
    TypedObject token = new TypedObject();
    assertEquals(mService.RepeatTypedObject(token), token);
    assertNull(mService.RepeatTypedObject(null));
    List<TypedObject> list = new ArrayList<>();
    list.add(token);
    assertEquals(mService.RepeatTypedList(list), list);
  }
}
