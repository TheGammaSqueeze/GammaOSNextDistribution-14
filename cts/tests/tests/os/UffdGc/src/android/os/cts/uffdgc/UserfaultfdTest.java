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
 * limitations under the License
 */

package android.os.cts.uffdgc;

import static org.junit.Assert.assertEquals;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.FeatureUtil;

import android.os.Build.VERSION_CODES;
import androidx.test.runner.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public final class UserfaultfdTest {

  static {
      System.loadLibrary("userfaultfdtest");
  }

  @Before
  public void setUp() {
      boolean mShouldRunTest = !(FeatureUtil.isAutomotive()
              && ApiLevelUtil.isAtMost(VERSION_CODES.S_V2));
      Assume.assumeTrue("Skip userfaultfd tests on Automotive targets till S_V2", mShouldRunTest);
      Assume.assumeTrue("Skip userfaultfd tests on kernels lower than 5.4", confirmKernelVersion());
  }

  // Test if a userfault from kernel-space fails or not. It is
  // not allowed for unprivileged processes.
  @Test
  public void kernelSpaceUserfault() {
    // Expect return value to be EFAULT (14).
    assertEquals(14, performKernelSpaceUffd());
  }

  // Test if a userfault file descriptor can be obtained without
  // UFFD_USER_MODE_ONLY flag. Unprivileged processes must specify
  // this flag to forbid kernel-space userfaults.
  @Test
  public void nonUserModeOnlyUserfaultfd() {
    // Expect return value to be EPERM (1).
    assertEquals(1, uffdWithoutUserModeOnly());
  }

  // Test if mremap syscall on a non-anonymous shared mapping
  // using MREMAP_DONTUNMAP flag works.
  @Test
  public void mremapDontUnmap() {
    assertEquals(0, performMremapDontUnmap());
  }

  // Test if userfaultfd works for minor-faults on shmem.
  @Test
  public void minorUserfaultfd() {
    // minor fault feature is not enabled on 32-bit kernel archs.
    Assume.assumeTrue(confirmKernelArch64bit());
    assertEquals(0, performMinorUffd());
  }

  // Confirms if userfaultfd is controlled by selinux or not.
  // We don't allow getattr operation in our selinux policy.
  @Test
  public void selinuxEnabled() {
    // Expect the return value to be EACCES (13).
    assertEquals(13, checkGetattr());
  }

  private native boolean confirmKernelArch64bit();
  private native boolean confirmKernelVersion();
  private native int performKernelSpaceUffd();
  private native int uffdWithoutUserModeOnly();
  private native int performMremapDontUnmap();
  private native int performMinorUffd();
  private native int checkGetattr();
}
