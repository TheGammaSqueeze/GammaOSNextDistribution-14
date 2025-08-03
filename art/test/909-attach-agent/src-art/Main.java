/*
 * Copyright (C) 2011 The Android Open Source Project
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

import dalvik.system.PathClassLoader;
import dalvik.system.VMDebug;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;

public class Main {
  public static void main(String[] args) throws Exception {
    System.loadLibrary(args[0]);
    System.out.println("Hello, world!");
    String agent = null;
    // By default allow debugging
    boolean debugging_allowed = true;
    for(String a : args) {
      if(a.startsWith("agent:")) {
        agent = a.substring(6);
      } else if (a.equals("disallow-debugging")) {
        debugging_allowed = false;
      }
    }
    if (agent == null) {
      throw new Error("Could not find agent: argument!");
    }
    setDebuggingAllowed(debugging_allowed);
    // Setup is finished. Try to attach agent in 2 ways.
    try {
      VMDebug.attachAgent(agent, null);
    } catch(SecurityException e) {
      System.out.println(e.getMessage());
    }
    attachWithClassLoader(args);
    System.out.println("Goodbye!");
  }

  private static native void setDebuggingAllowed(boolean val);

  private static void attachWithClassLoader(String[] args) throws Exception {
    for(String a : args) {
      if(a.startsWith("agent:")) {
        try {
          VMDebug.attachAgent(a.substring(6), Main.class.getClassLoader());
        } catch(SecurityException e) {
          System.out.println(e.getMessage());
        } catch (Exception e) {
          e.printStackTrace(System.out);
        }
      }
    }
  }
}
