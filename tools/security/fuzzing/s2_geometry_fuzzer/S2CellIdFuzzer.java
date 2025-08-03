// Copyright 2022, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.google.common.geometry.S2CellId;

public class S2CellIdFuzzer {

    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        S2CellId id = new S2CellId(data.consumeLong());
        while (data.remainingBytes() > 0) {
                try
                {
                    id.fromToken(data.consumeString(Integer.MAX_VALUE));
                } catch (NumberFormatException e) {}
        }
    }
}