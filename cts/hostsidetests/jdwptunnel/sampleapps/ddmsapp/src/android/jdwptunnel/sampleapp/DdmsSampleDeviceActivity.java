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

package android.jdwptunnel.sampleapp.ddms;

import android.app.Activity;
import android.os.Bundle;
import android.os.Debug;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * A simple activity which starts a thread that continuously spams DDMS messages.
 *
 * <p>This serves as a simple target application/activity get a ddms connection to.
 */
public final class DdmsSampleDeviceActivity extends Activity {
  @Override
  public void onCreate(Bundle icicle) {
    try {
      super.onCreate(icicle);
      setContentView(R.layout.sample_layout);
      Debug.attachJvmtiAgent(
          "libDdmsTestAgent.so", null, DdmsSampleDeviceActivity.class.getClassLoader());
      if (ForceNoHiddenapi() != 0) {
        throw new Error("Failed to disable hiddenapi!");
      }
      Class<?> chunk_klass = Class.forName("org.apache.harmony.dalvik.ddmc.Chunk");
      Constructor<?> chunk_cons =
          chunk_klass.getConstructor(
              Integer.TYPE, new byte[] {}.getClass(), Integer.TYPE, Integer.TYPE);
      Class<?> server_klass = Class.forName("org.apache.harmony.dalvik.ddmc.DdmServer");
      Method send_meth = server_klass.getDeclaredMethod("sendChunk", chunk_klass);
      // Spin spamming DDMS packets so we can try to lose the race.
      Thread t =
          new Thread(
              () -> {
                try {
                  while (true) {
                    send_meth.invoke(
                        null,
                        chunk_cons.newInstance(0xDEADBEEF, new byte[] {0x1, 0x3, 0x3, 0x7}, 0, 4));
                  }
                } catch (Exception e) {
                  throw new Error("Could not send data!", e);
                }
              });
      t.setDaemon(true);
      t.start();
    } catch (Exception e) {
      throw new Error("Failed to send ddm notes!", e);
    }
  }

  // Linked by the agent
  public static native int ForceNoHiddenapi();
}
