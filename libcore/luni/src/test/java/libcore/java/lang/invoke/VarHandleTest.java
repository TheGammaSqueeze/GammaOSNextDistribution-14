/*
 * Copyright (C) 2021 The Android Open Source Project
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

package libcore.java.lang.invoke;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

@RunWith(JUnit4.class)
public class VarHandleTest {

    private int field = 0;

    @Test
    public void fences() {
        // In theory, these should log coverage for these fences, but they are implemented
        // as intrinsics in the runtime and the compiler.
        VarHandle.acquireFence();
        VarHandle.releaseFence();
        VarHandle.fullFence();
        VarHandle.loadLoadFence();
        VarHandle.storeStoreFence();
    }

    @Test
    public void toString_describes_variable_and_its_coordinates_plain_field() throws Throwable {
        VarHandle vh = MethodHandles.lookup().findVarHandle(
                VarHandleTest.class, "field", int.class);

        String str = vh.toString();

        // Type of field is int.
        assertTrue(str + " does not mention int", str.contains("int"));
        assertTrue(str + " does not mention VarHandleTest", str.contains("VarHandleTest"));
        // Just to make errorprone happy.
        assertEquals(0, field);
    }
}
