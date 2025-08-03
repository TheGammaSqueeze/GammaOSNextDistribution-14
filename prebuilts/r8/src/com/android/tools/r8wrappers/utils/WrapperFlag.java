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
package com.android.tools.r8wrappers.utils;

import com.android.tools.r8.ParseFlagInfo;
import java.util.Collections;
import java.util.List;

public class WrapperFlag implements ParseFlagInfo {

  private final String flag;
  private final String help;

  public WrapperFlag(String flag, String help) {
    this.flag = flag;
    this.help = help;
  }

  @Override
  public String getFlagFormat() {
    return flag;
  }

  @Override
  public List<String> getFlagFormatAlternatives() {
    return Collections.emptyList();
  }

  @Override
  public List<String> getFlagHelp() {
    return Collections.singletonList(help);
  }
}
