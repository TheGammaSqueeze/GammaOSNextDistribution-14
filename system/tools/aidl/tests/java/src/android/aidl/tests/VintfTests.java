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
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VintfTests {
  private static final String kInstance = "android.does.not.exist.IFoo/default";

  @Test
  public void cantAddVintfService() throws RemoteException {
    Binder binder = new Binder();
    binder.markVintfStability(); // <- do not do this, for test only

    boolean hasException = false;
    try {
      ServiceManager.addService(kInstance, binder);
    } catch (IllegalArgumentException e) {
      hasException = true;
    }

    assertTrue(hasException);
  }

  @Test
  public void canDowngradeVintfService() throws RemoteException {
    Binder binder = new Binder();
    binder.markVintfStability(); // <- do not do this, for test only

    binder.forceDowngradeToSystemStability();

    ServiceManager.addService(kInstance, binder);
  }
}
