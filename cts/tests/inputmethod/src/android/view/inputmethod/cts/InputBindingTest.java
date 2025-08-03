/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.view.inputmethod.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

import android.os.Binder;
import android.os.Parcel;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.ravenwood.RavenwoodRule;
import android.view.inputmethod.InputBinding;
import android.view.inputmethod.InputConnection;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

// This is a bivalent test, it is expected to run on both device and host (Ravenwood) sides.
@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public final class InputBindingTest {

    private static final int ANY_UID = 1;
    private static final int ANY_PID = 2;

    @Rule
    public final RavenwoodRule mRavenwood = new RavenwoodRule();

    @Test
    @DisabledOnRavenwood(blockedBy = {Parcel.class})
    public void testInputBinding() {
        InputConnection conn = mock(InputConnection.class);
        Binder binder = new Binder();
        InputBinding inputBinding = new InputBinding(conn, binder, ANY_UID, ANY_PID);
        assertSame(conn, inputBinding.getConnection());
        assertSame(binder, inputBinding.getConnectionToken());
        assertEquals(ANY_UID, inputBinding.getUid());
        assertEquals(ANY_PID, inputBinding.getPid());

        assertNotNull(inputBinding.toString());
        assertEquals(0, inputBinding.describeContents());

        Parcel p = Parcel.obtain();
        inputBinding.writeToParcel(p, 0);
        p.setDataPosition(0);
        InputBinding target = InputBinding.CREATOR.createFromParcel(p);
        assertEquals(ANY_UID, target.getUid());
        assertEquals(ANY_PID, target.getPid());
        assertSame(binder, target.getConnectionToken());

        p.recycle();
    }

    @Test
    public void testInputBindingConstructor() {
        InputConnection conn = mock(InputConnection.class);
        Binder connectionToken = mock(Binder.class);

        InputBinding binding = new InputBinding(conn, connectionToken, ANY_UID, ANY_PID);

        assertEquals(ANY_UID, binding.getUid());
        assertEquals(ANY_PID, binding.getPid());
        assertSame(conn, binding.getConnection());
        assertSame(connectionToken, binding.getConnectionToken());
    }

    @Test
    public void testInputBindingCopyConstructor() {
        InputConnection conn = mock(InputConnection.class);
        Binder connectionToken = mock(Binder.class);
        InputBinding source = new InputBinding(conn, connectionToken, ANY_UID, ANY_PID);

        InputBinding copy = new InputBinding(conn, source);
        assertEquals(ANY_UID, copy.getUid());
        assertEquals(ANY_PID, copy.getPid());
        assertSame(conn, copy.getConnection());
        assertSame(connectionToken, copy.getConnectionToken());
    }
}
