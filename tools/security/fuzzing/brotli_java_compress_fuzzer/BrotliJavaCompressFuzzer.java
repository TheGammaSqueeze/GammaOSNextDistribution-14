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

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import java.io.IOException;
import org.brotli.wrapper.enc.Encoder;

public class BrotliJavaCompressFuzzer {
  static {
    System.loadLibrary("brotli_encoder_jni");
  }
  public static void fuzzerTestOneInput(FuzzedDataProvider data) throws IOException{
    // quality range for brotli is limited in the original library
    int quality = data.consumeInt(-1, 11);
    // similar to quality, window size is also limited
    int lgwin = data.consumeInt(10, 24);

    boolean pickDefaultWin = data.consumeBoolean();
    if (pickDefaultWin) {
      lgwin = -1;
    }

    byte[] rawData = data.consumeRemainingAsBytes();
    Encoder.Parameters params = new Encoder.Parameters();
    params.setQuality(quality);
    params.setWindow(lgwin);
    Encoder.compress(rawData, params);
  }
}
