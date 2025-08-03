package com.google.android.textclassifier.tests;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import android.content.Context;
import androidx.test.InstrumentationRegistry;


/** This is a launcher of the tests because we need a valid JNIEnv in some C++ tests. */
@RunWith(JUnit4.class)
public class JvmTestLauncher {

  @Before
  public void setUp() throws Exception {
    System.loadLibrary("jvm_test_launcher");
  }

  private native boolean testsMain(Context context);

  @Test
  public void testNative() throws Exception {
    assertThat(testsMain(InstrumentationRegistry.getContext())).isTrue();
  }
}
