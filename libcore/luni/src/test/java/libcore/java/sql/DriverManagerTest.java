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

package libcore.java.sql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.sql.DriverManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DriverManagerTest {

    @Test
    public void testSetLoginTimeout() {
        int originalTimeout = DriverManager.getLoginTimeout();

        try {
            int timeout = 9999;
            DriverManager.setLoginTimeout(timeout);
            assertEquals(timeout, DriverManager.getLoginTimeout());
        } finally {
            DriverManager.setLoginTimeout(originalTimeout);
        }
    }

    @Test
    public void testSetLogWriter() {
        PrintWriter originalWriter = DriverManager.getLogWriter();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintWriter writer = new PrintWriter(baos);
            DriverManager.setLogWriter(writer);
            assertSame(writer, DriverManager.getLogWriter());
            String msg = "secret message";
            DriverManager.println(msg);
            assertEquals(msg + "\n",  baos.toString());
        } finally {
            DriverManager.setLogWriter(originalWriter);
        }
    }
}
