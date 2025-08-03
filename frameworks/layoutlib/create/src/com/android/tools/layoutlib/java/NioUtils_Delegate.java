
package com.android.tools.layoutlib.java;

import java.nio.ByteBuffer;

public final class NioUtils_Delegate {
  public static void freeDirectBuffer(ByteBuffer buffer) {
    /*
     * NioUtils is not included in layoutlib classpath. Thus, calling NioUtils.freeDirectBuffer in
     * {@link android.graphics.ImageReader} produces ClassNotFound exception. Moreover, it does not
     * seem we have to do anything in here as we are only referencing the existing native buffer
     * and do not perform any allocation on creation.
     */
  }
}