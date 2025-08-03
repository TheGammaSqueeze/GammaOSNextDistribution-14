/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

@RunWith(JUnit4.class)
public class VarHandleRefCastTest {

    private Object field;

    @Test
    public void varhandleGetObjectAsInt() throws NoSuchFieldException, IllegalAccessException {
        VarHandle varHandle =
                MethodHandles.lookup()
                        .findVarHandle(VarHandleRefCastTest.class, "field", Object.class);

        varHandle.set(this, new Object());
        assertThrows(
                ClassCastException.class,
                () -> {
                    int x = (int) varHandle.get(this);
                });

        varHandle.set(this, null);
        assertThrows(
                NullPointerException.class,
                () -> {
                    int x = (int) varHandle.get(this);
                });

        varHandle.set(this, Integer.valueOf(42));
        assertEquals(42, (int) varHandle.get(this));
    }
}
