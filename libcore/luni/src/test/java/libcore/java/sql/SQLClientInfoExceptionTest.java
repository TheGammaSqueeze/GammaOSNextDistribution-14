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

import java.sql.ClientInfoStatus;
import java.sql.SQLClientInfoException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SQLClientInfoExceptionTest {

    @Test
    public void testConstructor() {
        String reason = "secret reason";
        String sqlState = "random SQL state";
        int vendorCode = 1234;
        Map<String, ClientInfoStatus> failedProps = new HashMap<String, ClientInfoStatus>() {{
            put("key1", ClientInfoStatus.REASON_VALUE_TRUNCATED);
        }};
        SQLClientInfoException exception = new SQLClientInfoException(reason, sqlState,
                vendorCode, failedProps);
        assertEquals(reason, exception.getMessage());
        assertEquals(sqlState, exception.getSQLState());
        assertEquals(vendorCode, exception.getErrorCode());
        assertEquals(failedProps, exception.getFailedProperties());
    }
}
