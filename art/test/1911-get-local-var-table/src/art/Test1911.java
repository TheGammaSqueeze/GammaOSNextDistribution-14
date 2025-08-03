/*
 * Copyright (C) 2017 The Android Open Source Project
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

package art;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.Integer;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

public class Test1911 {
  // Class/dex file containing the following class.
  //
  // CLASS_BYTES generated with java version 17.0.4.1: javac -g art/Target.java
  // DEX_BYTES generated with d8 version 8.3.7-dev: d8 --debug art/Target.class
  //
  // package art;
  // import java.util.ArrayList;
  // public class Target {
  //   public int zzz;
  //   public Target(int xxx) {
  //     int q = xxx * 4;
  //     zzz = q;
  //   }
  //   public static void doNothing(Object... objs) { doNothing(objs); }
  //   public void doSomething(int x) {
  //     doNothing(this);
  //     int y = x + 3;
  //     for (int z = 0; z < y * x; z++) {
  //       float q = y - z;
  //       double i = 0.3d * q;
  //       doNothing(q, i);
  //     }
  //     Object o = new Object();
  //     ArrayList<Integer> i = new ArrayList<>();
  //     int p = 4 | x;
  //     long q = 3 * p;
  //     doNothing(p, q, o, i);
  //   }
  //   public void testGenericParameters(ArrayList<Integer> array, int i, Integer val) {
  //     array.set(i, val);
  //   }
  // }
  public static byte[] CLASS_BYTES = Base64.getDecoder().decode(
      "yv66vgAAADcAUQoABAA2CQAOADcKAA4AOAcAOQY/0zMzMzMzMwoAOgA7CgA8AD0HAD4KAAkANgoA" +
      "PwBACgBBAEIKAAkAQwcARAEAA3p6egEAAUkBAAY8aW5pdD4BAAQoSSlWAQAEQ29kZQEAD0xpbmVO" +
      "dW1iZXJUYWJsZQEAEkxvY2FsVmFyaWFibGVUYWJsZQEABHRoaXMBAAxMYXJ0L1RhcmdldDsBAAN4" +
      "eHgBAAFxAQAJZG9Ob3RoaW5nAQAWKFtMamF2YS9sYW5nL09iamVjdDspVgEABG9ianMBABNbTGph" +
      "dmEvbGFuZy9PYmplY3Q7AQALZG9Tb21ldGhpbmcBAAFGAQABaQEAAUQBAAF6AQABeAEAAXkBAAFv" +
      "AQASTGphdmEvbGFuZy9PYmplY3Q7AQAVTGphdmEvdXRpbC9BcnJheUxpc3Q7AQABcAEAAUoBABZM" +
      "b2NhbFZhcmlhYmxlVHlwZVRhYmxlAQAqTGphdmEvdXRpbC9BcnJheUxpc3Q8TGphdmEvbGFuZy9J" +
      "bnRlZ2VyOz47AQANU3RhY2tNYXBUYWJsZQEAFXRlc3RHZW5lcmljUGFyYW1ldGVycwEALChMamF2" +
      "YS91dGlsL0FycmF5TGlzdDtJTGphdmEvbGFuZy9JbnRlZ2VyOylWAQAFYXJyYXkBAAN2YWwBABNM" +
      "amF2YS9sYW5nL0ludGVnZXI7AQAJU2lnbmF0dXJlAQBBKExqYXZhL3V0aWwvQXJyYXlMaXN0PExq" +
      "YXZhL2xhbmcvSW50ZWdlcjs+O0lMamF2YS9sYW5nL0ludGVnZXI7KVYBAApTb3VyY2VGaWxlAQAL" +
      "VGFyZ2V0LmphdmEMABEARQwADwAQDAAaABsBABBqYXZhL2xhbmcvT2JqZWN0BwBGDABHAEgHAEkM" +
      "AEcASgEAE2phdmEvdXRpbC9BcnJheUxpc3QHAEsMAEcATAcATQwARwBODABPAFABAAphcnQvVGFy" +
      "Z2V0AQADKClWAQAPamF2YS9sYW5nL0Zsb2F0AQAHdmFsdWVPZgEAFChGKUxqYXZhL2xhbmcvRmxv" +
      "YXQ7AQAQamF2YS9sYW5nL0RvdWJsZQEAFShEKUxqYXZhL2xhbmcvRG91YmxlOwEAEWphdmEvbGFu" +
      "Zy9JbnRlZ2VyAQAWKEkpTGphdmEvbGFuZy9JbnRlZ2VyOwEADmphdmEvbGFuZy9Mb25nAQATKEop" +
      "TGphdmEvbGFuZy9Mb25nOwEAA3NldAEAJyhJTGphdmEvbGFuZy9PYmplY3Q7KUxqYXZhL2xhbmcv" +
      "T2JqZWN0OwAhAA4ABAAAAAEAAQAPABAAAAAEAAEAEQASAAEAEwAAAFgAAgADAAAADiq3AAEbB2g9" +
      "Khy1AAKxAAAAAgAUAAAAEgAEAAAACAAEAAkACAAKAA0ACwAVAAAAIAADAAAADgAWABcAAAAAAA4A" +
      "GAAQAAEACAAGABkAEAACAIkAGgAbAAEAEwAAAC8AAQABAAAABSq4AAOxAAAAAgAUAAAABgABAAAA" +
      "DQAVAAAADAABAAAABQAcAB0AAAABAB4AEgABABMAAAFYAAUACAAAAIIEvQAEWQMqU7gAAxsGYD0D" +
      "Ph0cG2iiAC8cHWSGOAQUAAUXBI1rOQUFvQAEWQMXBLgAB1NZBBgFuAAIU7gAA4QDAaf/0LsABFm3" +
      "AAFOuwAJWbcACjoEBxuANgUGFQVohTcGB70ABFkDFQW4AAtTWQQWBrgADFNZBS1TWQYZBFO4AAOx" +
      "AAAABAAUAAAANgANAAAAEAALABEADwASABgAEwAeABQAJwAVAD4AEgBEABcATAAYAFUAGQBaABoA" +
      "YQAbAIEAHAAVAAAAZgAKAB4AIAAZAB8ABAAnABcAIAAhAAUAEQAzACIAEAADAAAAggAWABcAAAAA" +
      "AIIAIwAQAAEADwBzACQAEAACAEwANgAlACYAAwBVAC0AIAAnAAQAWgAoACgAEAAFAGEAIQAZACkA" +
      "BgAqAAAADAABAFUALQAgACsABAAsAAAACgAC/QARAQH6ADIAAQAtAC4AAgATAAAAZgADAAQAAAAI" +
      "KxwttgANV7EAAAADABQAAAAKAAIAAAAfAAcAIAAVAAAAKgAEAAAACAAWABcAAAAAAAgALwAnAAEA" +
      "AAAIACAAEAACAAAACAAwADEAAwAqAAAADAABAAAACAAvACsAAQAyAAAAAgAzAAEANAAAAAIANQ==");
  public static byte[] DEX_BYTES = Base64.getDecoder().decode(
      "ZGV4CjAzNQAALyjG3vy0POIlfGUh9Q7yf3NFwlp6VbWoBwAAcAAAAHhWNBIAAAAAAAAAAOQGAAAz" +
      "AAAAcAAAAA8AAAA8AQAACgAAAHgBAAABAAAA8AEAAAwAAAD4AQAAAQAAAFgCAAAwBQAAeAIAACIE" +
      "AAAlBAAAKQQAADEEAAA2BAAAOQQAADwEAAA/BAAAQgQAAEYEAABKBAAATgQAAFMEAABXBAAAZQQA" +
      "AIQEAACYBAAAqwQAAMAEAADSBAAA5gQAAP0EAAAUBQAAQAUAAE0FAABQBQAAVAUAAFgFAABeBQAA" +
      "YQUAAGUFAAB6BQAAgQUAAIwFAACZBQAAnAUAAKMFAACmBQAArAUAAK8FAACyBQAAtwUAAM4FAADT" +
      "BQAA2gUAAOMFAADmBQAA6wUAAO4FAADxBQAA9gUAAAQAAAAFAAAABgAAAAcAAAANAAAADgAAAA8A" +
      "AAAQAAAAEQAAABIAAAATAAAAFAAAABgAAAAcAAAAHgAAAAgAAAAGAAAA6AMAAAkAAAAHAAAA8AMA" +
      "AAoAAAAIAAAA+AMAAAwAAAAJAAAAAAQAAAsAAAAKAAAACAQAABgAAAAMAAAAAAAAABkAAAAMAAAA" +
      "+AMAABsAAAAMAAAAEAQAABoAAAAMAAAAHAQAAB0AAAANAAAA6AMAAAQAAgAxAAAABAAGAAIAAAAE" +
      "AAgAIAAAAAQABgAhAAAABAAHACkAAAAGAAkAIwAAAAYAAAAsAAAABwABACwAAAAIAAIALAAAAAkA" +
      "AwAsAAAACgAFAAIAAAALAAUAAgAAAAsABAAoAAAABAAAAAEAAAAKAAAAAAAAABcAAADMBgAApgYA" +
      "AAAAAAADAAIAAQAAAIwDAAAIAAAAcBAJAAEA2gACBFkQAAAOAAEAAQABAAAAmAMAAAQAAABxEAEA" +
      "AAAOAA4AAgACAAAAnQMAAFoAAAASECMBDgASAk0MAQJxEAEAAQDYAQ0DEgOSBAENEiU1QyQAkQQB" +
      "A4JEGAYzMzMzMzPTP4lIcSAEAJgArQgIBnEQBgAEAAwGcSAFAJgADAcjVQ4ATQYFAk0HBQBxEAEA" +
      "BQDYAwMBKNoiAwoAcBAJAAMAIgQLAHAQCgAEAN4GDQTaBwYDgXdxEAcABgAMCXEgCACHAAwKEksj" +
      "uw4ATQkLAk0KCwBNAwsFEjBNBAsAcRABAAsADgAEAAQAAwAAANsDAAAEAAAAbjALACEDDgAIAS8O" +
      "PC0DACgDLQANASYOABABLg6WLQMBMAMBAQMDMQNaPAMEKAK0AwgjAQERCwUEBQhABQNaAwMlC1oE" +
      "BCMMFy0DBicDPAMHKAQBFw8AHwMAIysOBAEgDBc8AAEAAAAAAAAAAQAAAAEAAAABAAAAAgAAAAEA" +
      "AAADAAAAAgAAAAIACgADAAAACwACAAgAAAABAAAADgABKAACKVYABjxpbml0PgADPjtJAAFEAAFG" +
      "AAFJAAFKAAJMRAACTEYAAkxJAANMSUwAAkxKAAxMYXJ0L1RhcmdldDsAHUxkYWx2aWsvYW5ub3Rh" +
      "dGlvbi9TaWduYXR1cmU7ABJMamF2YS9sYW5nL0RvdWJsZTsAEUxqYXZhL2xhbmcvRmxvYXQ7ABNM" +
      "amF2YS9sYW5nL0ludGVnZXI7ABBMamF2YS9sYW5nL0xvbmc7ABJMamF2YS9sYW5nL09iamVjdDsA" +
      "FUxqYXZhL3V0aWwvQXJyYXlMaXN0OwAVTGphdmEvdXRpbC9BcnJheUxpc3Q8ACpMamF2YS91dGls" +
      "L0FycmF5TGlzdDxMamF2YS9sYW5nL0ludGVnZXI7PjsAC1RhcmdldC5qYXZhAAFWAAJWSQACVkwA" +
      "BFZMSUwAAVoAAlpEABNbTGphdmEvbGFuZy9PYmplY3Q7AAVhcnJheQAJZG9Ob3RoaW5nAAtkb1Nv" +
      "bWV0aGluZwABaQAFaXNOYU4AAW8ABG9ianMAAXAAAXEAA3NldAAVdGVzdEdlbmVyaWNQYXJhbWV0" +
      "ZXJzAAN2YWwABXZhbHVlAAd2YWx1ZU9mAAF4AAN4eHgAAXkAAXoAA3p6egCbAX5+RDh7ImJhY2tl" +
      "bmQiOiJkZXgiLCJjb21waWxhdGlvbi1tb2RlIjoiZGVidWciLCJoYXMtY2hlY2tzdW1zIjpmYWxz" +
      "ZSwibWluLWFwaSI6MSwic2hhLTEiOiIzMTAxYWQ2Zjc0ZWUyMzI1MjhkZmM2NmEyNjE3YTkzODM4" +
      "NGU2NmVhIiwidmVyc2lvbiI6IjguMy43LWRldiJ9AAIFASscBhcAFxUXERcDFxEXAQABAgIAAQCB" +
      "gAT4BAGJAZgFAgGwBQEB9AYAAAAAAAEAAACUBgAAwAYAAAAAAAABAAAAAAAAAAMAAADEBgAAEAAA" +
      "AAAAAAABAAAAAAAAAAEAAAAzAAAAcAAAAAIAAAAPAAAAPAEAAAMAAAAKAAAAeAEAAAQAAAABAAAA" +
      "8AEAAAUAAAAMAAAA+AEAAAYAAAABAAAAWAIAAAEgAAAEAAAAeAIAAAMgAAAEAAAAjAMAAAEQAAAH" +
      "AAAA6AMAAAIgAAAzAAAAIgQAAAQgAAABAAAAlAYAAAAgAAABAAAApgYAAAMQAAACAAAAwAYAAAYg" +
      "AAABAAAAzAYAAAAQAAABAAAA5AYAAA==");

  // The variables of the functions in the above Target class.
  public static Set<Locals.VariableDescription>[] CONSTRUCTOR_VARIABLES = new Set[] {
      // RI Local variable table
      new HashSet<>(Arrays.asList(
              new Locals.VariableDescription(8, 6, "q", "I", null, 2),
              new Locals.VariableDescription(0, 14, "xxx", "I", null, 1),
              new Locals.VariableDescription(0, 14, "this", "Lart/Target;", null, 0))),
      // ART Local variable table
      new HashSet<>(Arrays.asList(
              new Locals.VariableDescription(0, 8, "this", "Lart/Target;", null, 1),
              new Locals.VariableDescription(5, 3, "q", "I", null, 0),
              new Locals.VariableDescription(0, 8, "xxx", "I", null, 2))),
  };

  public static Set<Locals.VariableDescription>[] DO_NOTHING_VARIABLES = new Set[] {
      // RI Local variable table
      new HashSet<>(Arrays.asList(
              new Locals.VariableDescription(0, 5, "objs", "[Ljava/lang/Object;", null, 0))),
      // ART Local variable table
      new HashSet<>(Arrays.asList(
              new Locals.VariableDescription(0, 4, "objs", "[Ljava/lang/Object;", null, 0))),
  };

  public static Set<Locals.VariableDescription>[] DO_SOMETHING_VARIABLES = new Set[] {
      // RI Local variable table
      new HashSet<>(Arrays.asList(
              new Locals.VariableDescription(0, 130, "x", "I", null, 1),
              new Locals.VariableDescription(76, 54, "o", "Ljava/lang/Object;", null, 3),
              new Locals.VariableDescription(30, 32, "q", "F", null, 4),
              new Locals.VariableDescription(39, 23, "i", "D", null, 5),
              new Locals.VariableDescription(17, 51, "z", "I", null, 3),
              new Locals.VariableDescription(15, 115, "y", "I", null, 2),
              new Locals.VariableDescription(90, 40, "p", "I", null, 5),
              new Locals.VariableDescription(97, 33, "q", "J", null, 6),
              new Locals.VariableDescription(0, 130, "this", "Lart/Target;", null, 0),
              new Locals.VariableDescription(85,
                                             45,
                                             "i",
                                             "Ljava/util/ArrayList;",
                                             "Ljava/util/ArrayList<Ljava/lang/Integer;>;",
                                             4))),
      // ART Local variable table
      new HashSet<>(Arrays.asList(
              new Locals.VariableDescription(20, 28, "q", "F", null, 4),
              new Locals.VariableDescription(56, 34, "o", "Ljava/lang/Object;", null, 3),
              new Locals.VariableDescription(0, 90, "this", "Lart/Target;", null, 12),
              new Locals.VariableDescription(12, 39, "z", "I", null, 3),
              new Locals.VariableDescription(11, 79, "y", "I", null, 1),
              new Locals.VariableDescription(63, 27, "p", "I", null, 6),
              new Locals.VariableDescription(0, 90, "x", "I", null, 13),
              new Locals.VariableDescription(31, 17, "i", "D", null, 8),
              new Locals.VariableDescription(66, 24, "q", "J", null, 7),
              new Locals.VariableDescription(61,
                                             29,
                                             "i",
                                             "Ljava/util/ArrayList;",
                                             "Ljava/util/ArrayList<Ljava/lang/Integer;>;",
                                             4))),
  };

  public static Set<Locals.VariableDescription>[] TEST_GENERIC_PARAMETERS_VARIABLES = new Set[] {
      // RI Local variable table
      new HashSet<>(Arrays.asList(
              new Locals.VariableDescription(0, 8, "this", "Lart/Target;", null, 0),
              new Locals.VariableDescription(0,
                                             8,
                                             "array",
                                             "Ljava/util/ArrayList;",
                                             "Ljava/util/ArrayList<Ljava/lang/Integer;>;",
                                             1),
              new Locals.VariableDescription(0, 8, "i", "I", null, 2),
              new Locals.VariableDescription(0, 8, "val", "Ljava/lang/Integer;", null, 3))),
      // ART Local variable table
      new HashSet<>(Arrays.asList(
              new Locals.VariableDescription(0, 4, "this", "Lart/Target;", null, 0),
              new Locals.VariableDescription(0,
                                             4,
                                             "array",
                                             "Ljava/util/ArrayList;",
                                             "Ljava/util/ArrayList<Ljava/lang/Integer;>;",
                                             1),
              new Locals.VariableDescription(0, 4, "i", "I", null, 2),
              new Locals.VariableDescription(0, 4, "val", "Ljava/lang/Integer;", null, 3))),
  };


  // Get a classloader that can load the Target class.
  public static ClassLoader getClassLoader() throws Exception {
    try {
      Class<?> class_loader_class = Class.forName("dalvik.system.InMemoryDexClassLoader");
      Constructor<?> ctor = class_loader_class.getConstructor(ByteBuffer.class, ClassLoader.class);
      // We are on art since we got the InMemoryDexClassLoader.
      return (ClassLoader)ctor.newInstance(
          ByteBuffer.wrap(DEX_BYTES), Test1911.class.getClassLoader());
    } catch (ClassNotFoundException e) {
      // Running on RI.
      return new ClassLoader(Test1911.class.getClassLoader()) {
        protected Class<?> findClass(String name) throws ClassNotFoundException {
          if (name.equals("art.Target")) {
            return defineClass(name, CLASS_BYTES, 0, CLASS_BYTES.length);
          } else {
            return super.findClass(name);
          }
        }
      };
    }
  }

  public static void CheckLocalVariableTable(Executable m,
          Set<Locals.VariableDescription>[] possible_vars) {
    Set<Locals.VariableDescription> real_vars =
            new HashSet<>(Arrays.asList(Locals.GetLocalVariableTable(m)));
    for (Set<Locals.VariableDescription> pos : possible_vars) {
      if (pos.equals(real_vars)) {
        return;
      }
    }
    System.out.println("Unexpected variables for " + m);
    System.out.println("Received: " + real_vars);
    System.out.println("Expected one of:");
    for (Object pos : possible_vars) {
      System.out.println("\t" + pos);
    }
  }
  public static void run() throws Exception {
    Locals.EnableLocalVariableAccess();
    Class<?> target = getClassLoader().loadClass("art.Target");
    CheckLocalVariableTable(target.getDeclaredConstructor(Integer.TYPE),
            CONSTRUCTOR_VARIABLES);
    CheckLocalVariableTable(target.getDeclaredMethod("doNothing", (new Object[0]).getClass()),
            DO_NOTHING_VARIABLES);
    CheckLocalVariableTable(target.getDeclaredMethod("doSomething", Integer.TYPE),
            DO_SOMETHING_VARIABLES);
    CheckLocalVariableTable(target.getDeclaredMethod("testGenericParameters",
            (new ArrayList<Integer>(0)).getClass(), Integer.TYPE, (new Integer(0)).getClass()),
            TEST_GENERIC_PARAMETERS_VARIABLES);
  }
}

