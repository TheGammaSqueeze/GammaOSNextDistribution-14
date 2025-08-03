/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static art.Redefinition.doCommonClassRedefinition;
import java.util.Base64;
import java.util.OptionalLong;
public class Main {

  /**
   * This is the base64 encoded class/dex.
   *
   * To regenerate these constants:
   *  1) Update src-optional/java/util/OptionalLong.java
   *  2) run convert-to-base64.sh script, specifying
   *     required parameters (path to d8 tool and path to android.jar;
   *     both can be found in Android sdk)
   *  3) copy and paste base64 text below
   *
   */
  private static final byte[] CLASS_BYTES = Base64.getDecoder().decode(
      "LyoKICogQ29weXJpZ2h0IChDKSAyMDIxIFRoZSBBbmRyb2lkIE9wZW4gU291cmNlIFByb2plY3QK" +
      "ICoKICogTGljZW5zZWQgdW5kZXIgdGhlIEFwYWNoZSBMaWNlbnNlLCBWZXJzaW9uIDIuMCAodGhl" +
      "ICJMaWNlbnNlIik7CiAqIHlvdSBtYXkgbm90IHVzZSB0aGlzIGZpbGUgZXhjZXB0IGluIGNvbXBs" +
      "aWFuY2Ugd2l0aCB0aGUgTGljZW5zZS4KICogWW91IG1heSBvYnRhaW4gYSBjb3B5IG9mIHRoZSBM" +
      "aWNlbnNlIGF0CiAqCiAqICAgICAgaHR0cDovL3d3dy5hcGFjaGUub3JnL2xpY2Vuc2VzL0xJQ0VO" +
      "U0UtMi4wCiAqCiAqIFVubGVzcyByZXF1aXJlZCBieSBhcHBsaWNhYmxlIGxhdyBvciBhZ3JlZWQg" +
      "dG8gaW4gd3JpdGluZywgc29mdHdhcmUKICogZGlzdHJpYnV0ZWQgdW5kZXIgdGhlIExpY2Vuc2Ug" +
      "aXMgZGlzdHJpYnV0ZWQgb24gYW4gIkFTIElTIiBCQVNJUywKICogV0lUSE9VVCBXQVJSQU5USUVT" +
      "IE9SIENPTkRJVElPTlMgT0YgQU5ZIEtJTkQsIGVpdGhlciBleHByZXNzIG9yIGltcGxpZWQuCiAq" +
      "IFNlZSB0aGUgTGljZW5zZSBmb3IgdGhlIHNwZWNpZmljIGxhbmd1YWdlIGdvdmVybmluZyBwZXJt" +
      "aXNzaW9ucyBhbmQKICogbGltaXRhdGlvbnMgdW5kZXIgdGhlIExpY2Vuc2UuCiAqLwpwYWNrYWdl" +
      "IGphdmEudXRpbDsKaW1wb3J0IGphdmEudXRpbC5mdW5jdGlvbi5Mb25nQ29uc3VtZXI7CmltcG9y" +
      "dCBqYXZhLnV0aWwuZnVuY3Rpb24uTG9uZ1N1cHBsaWVyOwppbXBvcnQgamF2YS51dGlsLmZ1bmN0" +
      "aW9uLlN1cHBsaWVyOwppbXBvcnQgamF2YS51dGlsLnN0cmVhbS5Mb25nU3RyZWFtOwpwdWJsaWMg" +
      "ZmluYWwgY2xhc3MgT3B0aW9uYWxMb25nIHsKICAvLyBNYWtlIHN1cmUgd2UgaGF2ZSBhIDxjbGlu" +
      "aXQ+IGZ1bmN0aW9uIHNpbmNlIHRoZSByZWFsIGltcGxlbWVudGF0aW9uIG9mIE9wdGlvbmFsTG9u" +
      "ZyBkb2VzLgogIHN0YXRpYyB7IEVNUFRZID0gbnVsbDsgfQogIHByaXZhdGUgc3RhdGljIGZpbmFs" +
      "IE9wdGlvbmFsTG9uZyBFTVBUWTsKICBwcml2YXRlIGZpbmFsIGJvb2xlYW4gaXNQcmVzZW50Owog" +
      "IHByaXZhdGUgZmluYWwgbG9uZyB2YWx1ZTsKICBwcml2YXRlIE9wdGlvbmFsTG9uZygpIHsgaXNQ" +
      "cmVzZW50ID0gZmFsc2U7IHZhbHVlID0gMDsgfQogIHByaXZhdGUgT3B0aW9uYWxMb25nKGxvbmcg" +
      "bCkgeyB0aGlzKCk7IH0KICBwdWJsaWMgc3RhdGljIE9wdGlvbmFsTG9uZyBlbXB0eSgpIHsgcmV0" +
      "dXJuIG51bGw7IH0KICBwdWJsaWMgc3RhdGljIE9wdGlvbmFsTG9uZyBvZihsb25nIHZhbHVlKSB7" +
      "IHJldHVybiBudWxsOyB9CiAgcHVibGljIGxvbmcgZ2V0QXNMb25nKCkgeyByZXR1cm4gMDsgfQog" +
      "IHB1YmxpYyBib29sZWFuIGlzUHJlc2VudCgpIHsgcmV0dXJuIGZhbHNlOyB9CiAgcHVibGljIGJv" +
      "b2xlYW4gaXNFbXB0eSgpIHsgcmV0dXJuIGZhbHNlOyB9CiAgcHVibGljIHZvaWQgaWZQcmVzZW50" +
      "KExvbmdDb25zdW1lciBjKSB7IH0KICBwdWJsaWMgdm9pZCBpZlByZXNlbnRPckVsc2UoTG9uZ0Nv" +
      "bnN1bWVyIGFjdGlvbiwgUnVubmFibGUgZW1wdHlBY3Rpb24pIHsgfQogIHB1YmxpYyBMb25nU3Ry" +
      "ZWFtIHN0cmVhbSgpIHsgcmV0dXJuIG51bGw7IH0KICBwdWJsaWMgbG9uZyBvckVsc2UobG9uZyBs" +
      "KSB7IHJldHVybiAwOyB9CiAgcHVibGljIGxvbmcgb3JFbHNlR2V0KExvbmdTdXBwbGllciBzKSB7" +
      "IHJldHVybiAwOyB9CiAgcHVibGljIGxvbmcgb3JFbHNlVGhyb3coKSB7IHJldHVybiAwOyB9CiAg" +
      "cHVibGljPFggZXh0ZW5kcyBUaHJvd2FibGU+IGxvbmcgb3JFbHNlVGhyb3coU3VwcGxpZXI8PyBl" +
      "eHRlbmRzIFg+IHMpIHRocm93cyBYIHsgcmV0dXJuIDA7IH0KICBwdWJsaWMgYm9vbGVhbiBlcXVh" +
      "bHMoT2JqZWN0IG8pIHsgcmV0dXJuIGZhbHNlOyB9CiAgcHVibGljIGludCBoYXNoQ29kZSgpIHsg" +
      "cmV0dXJuIDA7IH0KICBwdWJsaWMgU3RyaW5nIHRvU3RyaW5nKCkgeyByZXR1cm4gIlJlZGVmaW5l" +
      "ZCBPcHRpb25hbExvbmchIjsgfQp9Cg==");
  private static final byte[] DEX_BYTES = Base64.getDecoder().decode(
      "ZGV4CjAzNQBVWRCACMU+HJ9PqTkRRt+Gpa1jx32x1C8kCQAAcAAAAHhWNBIAAAAAAAAAAGAIAAAw" +
      "AAAAcAAAAA8AAAAwAQAADwAAAGwBAAADAAAAIAIAABMAAAA4AgAAAQAAANACAAA0BgAA8AIAAL4E" +
      "AADMBAAA0QQAANsEAADjBAAA5wQAAO4EAADxBAAA9AQAAPgEAAD8BAAA/wQAAAMFAAAiBQAAPgUA" +
      "AFIFAABoBQAAfAUAAJMFAACtBQAA0AUAAPMFAAASBgAAMQYAAFAGAABjBgAAfAYAAH8GAACDBgAA" +
      "hwYAAIwGAACPBgAAkwYAAJoGAACiBgAArQYAALcGAADCBgAA0wYAANwGAADnBgAA6wYAAPMGAAD+" +
      "BgAACwcAABMHAAAdBwAAJAcAAAYAAAAHAAAADAAAAA0AAAAOAAAADwAAABAAAAARAAAAEgAAABMA" +
      "AAAUAAAAFQAAABcAAAAaAAAAHgAAAAYAAAAAAAAAAAAAAAcAAAABAAAAAAAAAAgAAAABAAAAkAQA" +
      "AAkAAAABAAAAmAQAAAkAAAABAAAAoAQAAAoAAAAGAAAAAAAAAAoAAAAIAAAAAAAAAAsAAAAIAAAA" +
      "kAQAAAoAAAAMAAAAAAAAABoAAAANAAAAAAAAABsAAAANAAAAkAQAABwAAAANAAAAqAQAAB0AAAAN" +
      "AAAAsAQAAB4AAAAOAAAAAAAAAB8AAAAOAAAAuAQAAAgACAAFAAAACAAOACcAAAAIAAEALgAAAAQA" +
      "CQADAAAACAAJAAIAAAAIAAkAAwAAAAgACgADAAAACAAGACAAAAAIAA4AIQAAAAgAAQAiAAAACAAA" +
      "ACMAAAAIAAsAJAAAAAgADAAlAAAACAANACYAAAAIAA0AJwAAAAgABwAoAAAACAACACkAAAAIAAMA" +
      "KgAAAAgAAQArAAAACAAEACsAAAAIAAgALAAAAAgABQAtAAAACAAAABEAAAAEAAAAAAAAABgAAABI" +
      "CAAA3QcAAAAAAAACAAIAAAAAAAAAAAACAAAAEgEPAQIAAQAAAAAAAAAAAAIAAAASAA8AAgABAAAA" +
      "AAAAAAAAAgAAABIADwACAAEAAAAAAAAAAAACAAAAEgAPAAIAAQAAAAAAAAAAAAMAAAAaABkAEQAA" +
      "AAEAAAAAAAAAAAAAAAIAAAASABEAAgACAAAAAAAAAAAAAgAAABIAEQACAAEAAAAAAAAAAAACAAAA" +
      "EgARAAMAAQAAAAAAAAAAAAMAAAAWAAAAEAAAAAMAAwAAAAAAAAAAAAMAAAAWAQAAEAEAAAQAAgAA" +
      "AAAAAAAAAAMAAAAWAAAAEAAAAAMAAQAAAAAAAAAAAAMAAAAWAAAAEAAAAAQAAgAAAAAAAAAAAAMA" +
      "AAAWAAAAEAAAAAAAAAAAAAAAAAAAAAEAAAAOAAAAAwABAAEAAACGBAAACwAAAHAQAAACABIAXCAB" +
      "ABYAAABaIAIADgAAAAMAAwABAAAAigQAAAQAAABwEAIAAAAOAAIAAgAAAAAAAAAAAAEAAAAOAAAA" +
      "AwADAAAAAAAAAAAAAQAAAA4AGwAOABwBAA4AAAEAAAABAAAAAQAAAAoAAAABAAAACwAAAAEAAAAJ" +
      "AAAAAgAAAAkABQABAAAABAAMK1RYOz47KUpeVFg7AAM8WDoACDxjbGluaXQ+AAY8aW5pdD4AAj4o" +
      "AAVFTVBUWQABSQABSgACSkoAAkpMAAFMAAJMSgAdTGRhbHZpay9hbm5vdGF0aW9uL1NpZ25hdHVy" +
      "ZTsAGkxkYWx2aWsvYW5ub3RhdGlvbi9UaHJvd3M7ABJMamF2YS9sYW5nL09iamVjdDsAFExqYXZh" +
      "L2xhbmcvUnVubmFibGU7ABJMamF2YS9sYW5nL1N0cmluZzsAFUxqYXZhL2xhbmcvVGhyb3dhYmxl" +
      "OwAYTGphdmEvdXRpbC9PcHRpb25hbExvbmc7ACFMamF2YS91dGlsL2Z1bmN0aW9uL0xvbmdDb25z" +
      "dW1lcjsAIUxqYXZhL3V0aWwvZnVuY3Rpb24vTG9uZ1N1cHBsaWVyOwAdTGphdmEvdXRpbC9mdW5j" +
      "dGlvbi9TdXBwbGllcjsAHUxqYXZhL3V0aWwvZnVuY3Rpb24vU3VwcGxpZXI8AB1MamF2YS91dGls" +
      "L3N0cmVhbS9Mb25nU3RyZWFtOwART3B0aW9uYWxMb25nLmphdmEAF1JlZGVmaW5lZCBPcHRpb25h" +
      "bExvbmchAAFWAAJWSgACVkwAA1ZMTAABWgACWkwABWVtcHR5AAZlcXVhbHMACWdldEFzTG9uZwAI" +
      "aGFzaENvZGUACWlmUHJlc2VudAAPaWZQcmVzZW50T3JFbHNlAAdpc0VtcHR5AAlpc1ByZXNlbnQA" +
      "Am9mAAZvckVsc2UACW9yRWxzZUdldAALb3JFbHNlVGhyb3cABnN0cmVhbQAIdG9TdHJpbmcABXZh" +
      "bHVlAJ4Bfn5EOHsiYmFja2VuZCI6ImRleCIsImNvbXBpbGF0aW9uLW1vZGUiOiJyZWxlYXNlIiwi" +
      "aGFzLWNoZWNrc3VtcyI6ZmFsc2UsIm1pbi1hcGkiOjEsInNoYS0xIjoiOWM5OGM2ZGRmZDc0ZGVj" +
      "ZThiOTdlOGEyODc4ZDIwOGEwNjJmZGJmNCIsInZlcnNpb24iOiIzLjAuNDEtZGV2In0AAgIBLhwF" +
      "FwEXERcEFxYXAAIDAS4cARgHAQIFDQAaARIBEgGIgASMCAGCgASgCAGCgATICAEJ2AYICewGBQHw" +
      "BQEBlAcBAawGAQHgCAEB9AgBAYQGAQGYBgIBrAcBAcQHAQHcBwEB9AcBAYAHAQHABgAAAAAAAAAC" +
      "AAAAxQcAANUHAAA4CAAAAAAAAAEAAAAAAAAAEAAAADwIAAAQAAAAAAAAAAEAAAAAAAAAAQAAADAA" +
      "AABwAAAAAgAAAA8AAAAwAQAAAwAAAA8AAABsAQAABAAAAAMAAAAgAgAABQAAABMAAAA4AgAABgAA" +
      "AAEAAADQAgAAASAAABIAAADwAgAAAyAAAAIAAACGBAAAARAAAAYAAACQBAAAAiAAADAAAAC+BAAA" +
      "BCAAAAIAAADFBwAAACAAAAEAAADdBwAAAxAAAAIAAAA4CAAABiAAAAEAAABICAAAABAAAAEAAABg" +
      "CAAA");

  public static void main(String[] args) {
    // OptionalLong is a class that is unlikely to be used by the time this test starts and is not
    // likely to be changed in any meaningful way in the future.
    OptionalLong ol = OptionalLong.of(0xDEADBEEF);
    System.out.println("ol.toString() -> '" + ol.toString() + "'");
    System.out.println("Redefining OptionalLong!");
    doCommonClassRedefinition(OptionalLong.class, CLASS_BYTES, DEX_BYTES);
    System.out.println("ol.toString() -> '" + ol.toString() + "'");
  }
}
