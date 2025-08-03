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

package android.os.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Process;
import android.os.WorkSource;
import android.platform.test.annotations.IgnoreUnderRavenwood;
import android.platform.test.ravenwood.RavenwoodRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BinderTest {
    @Rule public RavenwoodRule mRavenwood = new RavenwoodRule.Builder().setProcessApp().build();

    private static final String DESCRIPTOR_GOOGLE = "google";
    private static final String DESCRIPTOR_ANDROID = "android";
    private MockBinder mBinder;

    @Before
    public void setUp() throws Exception {
        mBinder = new MockBinder();
    }

    @Test
    public void testSimpleMethods() {
        new Binder();

        assertEquals(Process.myPid(), Binder.getCallingPid());
        assertEquals(Process.myUid(), Binder.getCallingUid());

        assertTrue(mBinder.isBinderAlive());

        mBinder.linkToDeath(new MockDeathRecipient(), 0);

        assertTrue(mBinder.unlinkToDeath(new MockDeathRecipient(), 0));

        assertTrue(mBinder.pingBinder());

        assertTrue(IBinder.getSuggestedMaxIpcSizeBytes() > 0);
    }

    @Test
    @IgnoreUnderRavenwood
    public void testDump() {
        final String[] dumpArgs = new String[]{"one", "two", "three"};
        mBinder.dump(new FileDescriptor(),
                new PrintWriter(new ByteArrayOutputStream()),
                dumpArgs);

        mBinder.dump(new FileDescriptor(), dumpArgs);
    }

    @Test
    public void testFlushPendingCommands() {
        Binder.flushPendingCommands();
    }

    @Test
    @IgnoreUnderRavenwood(reason = "Requires kernel support")
    public void testJoinThreadPool() {
        final CountDownLatch waitLatch = new CountDownLatch(1);
        final CountDownLatch alertLatch = new CountDownLatch(1);
        Thread joinThread = new Thread("JoinThreadPool-Thread") {
            @Override
            public void run() {
                waitLatch.countDown();
                Binder.joinThreadPool();
                // Should not reach here. Let the main thread know.
                alertLatch.countDown();
            }
        };
        joinThread.setDaemon(true);
        joinThread.start();
        try {
            assertTrue(waitLatch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail("InterruptedException");
        }
        try {
            // This waits a small amount of time, hoping that if joinThreadPool
            // fails, it fails fast.
            assertFalse(alertLatch.await(3, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail("InterruptedException");
        }
        // Confirm that the thread is actually in joinThreadPool.
        StackTraceElement stack[] = joinThread.getStackTrace();
        boolean found = false;
        for (StackTraceElement elem : stack) {
            if (elem.toString().contains("Binder.joinThreadPool")) {
                found = true;
                break;
            }
        }
        assertTrue(Arrays.toString(stack), found);
    }

    @Test
    public void testClearCallingIdentity() {
        long token = Binder.clearCallingIdentity();
        assertTrue(token > 0);
        Binder.restoreCallingIdentity(token);
    }

    @Test
    public void testGetCallingUidOrThrow_throws() throws Exception {
        assertThrows(IllegalStateException.class, () -> Binder.getCallingUidOrThrow());
    }

    @Test
    public void testGetCallingUidOrThrow_insideClearRestoreCallingIdentity_doesNotThrow()
            throws Exception {
        long token = Binder.clearCallingIdentity();
        try {
            Binder.getCallingUidOrThrow();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Test
    public void testGetCallingUidOrThrow_afterClearRestoreCallingIdentity_throws()
            throws Exception {
        long token = Binder.clearCallingIdentity();
        try {
            Binder.getCallingUidOrThrow();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
        // if a token is properly cleared and restored, a subsequent call should throw
        assertThrows(IllegalStateException.class, () -> Binder.getCallingUidOrThrow());
    }

    @Test
    public void testGetCallingUidOrThrow_multipleClearsAreRestoredCorrectly_throws()
            throws Exception {
        long outerToken = Binder.clearCallingIdentity();
        long innerToken = Binder.clearCallingIdentity();
        try {
            Binder.getCallingUidOrThrow();
        } finally {
            Binder.restoreCallingIdentity(innerToken);
            Binder.restoreCallingIdentity(outerToken);
        }
        // if multiple tokens are cleared and restored in the proper order,
        // a subsequent call should throw
        assertThrows(IllegalStateException.class, () -> Binder.getCallingUidOrThrow());
    }

    @Test
    public void testGetCallingUidOrThrow_onlyOutermostClearIsRestored_throws() throws Exception {
        long outerToken = Binder.clearCallingIdentity();
        long innerToken = Binder.clearCallingIdentity();
        try {
            Binder.getCallingUidOrThrow();
        } finally {
            Binder.restoreCallingIdentity(outerToken);
        }
        // if multiple tokens are cleared, and only the outermost token is restored,
        // a subsequent call should throw
        assertThrows(IllegalStateException.class, () -> Binder.getCallingUidOrThrow());
    }

    @Test
    public void testGetCallingUidOrThrow_multipleClearsAreRestoredIncorrectly_doesNotThrow()
            throws Exception {
        long outerToken = Binder.clearCallingIdentity();
        long innerToken = Binder.clearCallingIdentity();
        try {
            Binder.getCallingUidOrThrow();
        } finally {
            Binder.restoreCallingIdentity(outerToken);
            Binder.restoreCallingIdentity(innerToken);
        }
        // if multiple tokens are restored incorrectly,
        // a subsequent call will not throw
        Binder.getCallingUidOrThrow();
    }

    @Test
    public void testGetCallingUidOrThrow_duplicateClearsAreStoredInSameVariable_doesNotThrow()
            throws Exception {
        long token = Binder.clearCallingIdentity();
        token = Binder.clearCallingIdentity();
        try {
            Binder.getCallingUidOrThrow();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
        // if the same variable is used for multiple clears, a subsequent call will not throw
        Binder.getCallingUidOrThrow();
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = WorkSource.class)
    public void testClearCallingWorkSource() {
        final long token = Binder.clearCallingWorkSource();
        Binder.restoreCallingWorkSource(token);
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = WorkSource.class)
    public void testSetCallingWorkSourceUid() {
        final int otherUid = android.os.Process.myUid() + 1;
        assertFalse(Binder.getCallingWorkSourceUid() == otherUid);

        final long token = Binder.setCallingWorkSourceUid(otherUid);
        assertTrue(Binder.getCallingWorkSourceUid() == otherUid);
        Binder.restoreCallingWorkSource(token);

        assertFalse(Binder.getCallingWorkSourceUid() == otherUid);
    }

    @Test
    public void testInterfaceRelatedMethods() {
        assertNull(mBinder.getInterfaceDescriptor());
        mBinder.attachInterface(new MockIInterface(), DESCRIPTOR_GOOGLE);
        assertEquals(DESCRIPTOR_GOOGLE, mBinder.getInterfaceDescriptor());

        mBinder.attachInterface(new MockIInterface(), DESCRIPTOR_ANDROID);
        assertNull(mBinder.queryLocalInterface(DESCRIPTOR_GOOGLE));
        mBinder.attachInterface(new MockIInterface(), DESCRIPTOR_GOOGLE);
        assertNotNull(mBinder.queryLocalInterface(DESCRIPTOR_GOOGLE));
    }

    private static class MockDeathRecipient implements IBinder.DeathRecipient {
         public void binderDied() {

         }
    }

    private static class MockIInterface implements IInterface {
        public IBinder asBinder() {
            return new Binder();
        }
    }

    private static class MockBinder extends Binder {
        @Override
        public void dump(FileDescriptor fd, PrintWriter fout, String[] args) {
            super.dump(fd, fout, args);
        }
    }

}
