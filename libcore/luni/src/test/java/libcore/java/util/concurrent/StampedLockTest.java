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

package libcore.java.util.concurrent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.locks.StampedLock;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StampedLockTest {

    @Test
    public void testIsLockStamp() {
        final StampedLock lock = new StampedLock();

        long stamp = lock.tryConvertToReadLock(0L);
        assertFalse(lock.isLockStamp(stamp));

        stamp = lock.tryOptimisticRead();
        stamp = lock.tryConvertToReadLock(stamp);
        assertTrue(lock.isLockStamp(stamp));

        lock.unlockRead(stamp);
    }

    @Test
    public void testIsOptimisticReadStamp() {
        final StampedLock lock = new StampedLock();

        long stamp = lock.readLock();
        assertFalse(lock.isOptimisticReadStamp(stamp));

        stamp = lock.tryConvertToOptimisticRead(stamp);
        assertTrue(lock.isOptimisticReadStamp(stamp));

        stamp = lock.readLock();
        lock.unlockRead(stamp);
    }

    @Test
    public void testIsReadLockStamp() {
        final StampedLock lock = new StampedLock();

        long stamp = lock.writeLock();
        assertFalse(lock.isReadLockStamp(stamp));
        lock.unlockWrite(stamp);

        stamp = lock.readLock();
        assertTrue(lock.isReadLockStamp(stamp));
        lock.unlockRead(stamp);
    }

    @Test
    public void testIsWriteLockStamp() {
        final StampedLock lock = new StampedLock();

        long stamp = lock.readLock();
        assertFalse(lock.isWriteLockStamp(stamp));
        lock.unlockRead(stamp);

        stamp = lock.writeLock();
        assertTrue(lock.isWriteLockStamp(stamp));
        lock.unlockWrite(stamp);
    }

}
