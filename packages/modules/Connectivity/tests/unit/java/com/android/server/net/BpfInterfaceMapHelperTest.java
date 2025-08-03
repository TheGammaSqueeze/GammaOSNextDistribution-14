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

package com.android.server.net;

import static android.system.OsConstants.EPERM;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import android.os.Build;
import android.system.ErrnoException;
import android.util.IndentingPrintWriter;

import androidx.test.filters.SmallTest;

import com.android.net.module.util.BaseNetdUnsolicitedEventListener;
import com.android.net.module.util.IBpfMap;
import com.android.net.module.util.Struct.S32;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;
import com.android.testutils.TestBpfMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

import java.io.PrintWriter;
import java.io.StringWriter;

@SmallTest
@RunWith(DevSdkIgnoreRunner.class)
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.S_V2)
public final class BpfInterfaceMapHelperTest {
    private static final int TEST_INDEX = 1;
    private static final int TEST_INDEX2 = 2;
    private static final String TEST_INTERFACE_NAME = "test1";
    private static final String TEST_INTERFACE_NAME2 = "test2";

    private BaseNetdUnsolicitedEventListener mListener;
    private BpfInterfaceMapHelper mUpdater;
    private IBpfMap<S32, InterfaceMapValue> mBpfMap =
            spy(new TestBpfMap<>(S32.class, InterfaceMapValue.class));

    private class TestDependencies extends BpfInterfaceMapHelper.Dependencies {
        @Override
        public IBpfMap<S32, InterfaceMapValue> getInterfaceMap() {
            return mBpfMap;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mUpdater = new BpfInterfaceMapHelper(new TestDependencies());
    }

    @Test
    public void testGetIfNameByIndex() throws Exception {
        mBpfMap.updateEntry(new S32(TEST_INDEX), new InterfaceMapValue(TEST_INTERFACE_NAME));
        assertEquals(TEST_INTERFACE_NAME, mUpdater.getIfNameByIndex(TEST_INDEX));
    }

    @Test
    public void testGetIfNameByIndexNoEntry() {
        assertNull(mUpdater.getIfNameByIndex(TEST_INDEX));
    }

    @Test
    public void testGetIfNameByIndexException() throws Exception {
        doThrow(new ErrnoException("", EPERM)).when(mBpfMap).getValue(new S32(TEST_INDEX));
        assertNull(mUpdater.getIfNameByIndex(TEST_INDEX));
    }

    private void assertDumpContains(final String dump, final String message) {
        assertTrue(String.format("dump(%s) does not contain '%s'", dump, message),
                dump.contains(message));
    }

    private String getDump() {
        final StringWriter sw = new StringWriter();
        mUpdater.dump(new IndentingPrintWriter(new PrintWriter(sw), " "));
        return sw.toString();
    }

    @Test
    public void testDump() throws ErrnoException {
        mBpfMap.updateEntry(new S32(TEST_INDEX), new InterfaceMapValue(TEST_INTERFACE_NAME));
        mBpfMap.updateEntry(new S32(TEST_INDEX2), new InterfaceMapValue(TEST_INTERFACE_NAME2));

        final String dump = getDump();
        assertDumpContains(dump, "IfaceIndexNameMap: OK");
        assertDumpContains(dump, "ifaceIndex=1 ifaceName=test1");
        assertDumpContains(dump, "ifaceIndex=2 ifaceName=test2");
    }
}
