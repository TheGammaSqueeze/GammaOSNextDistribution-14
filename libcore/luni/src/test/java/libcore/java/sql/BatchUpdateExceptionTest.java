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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.sql.BatchUpdateException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BatchUpdateExceptionTest {

    @Test
    public void testConstructor() {
        String reason = "exception reason";
        Throwable cause = new RuntimeException();
        int[] updateCounts = {9, 5, 6};
        BatchUpdateException exception = new BatchUpdateException(reason, updateCounts, cause);
        assertEquals(reason, exception.getMessage());
        assertArrayEquals(updateCounts, exception.getUpdateCounts());
        assertSame(cause, exception.getCause());
    }
}
