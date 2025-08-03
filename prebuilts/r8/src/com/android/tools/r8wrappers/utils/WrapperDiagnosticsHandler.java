// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8wrappers.utils;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;

public class WrapperDiagnosticsHandler implements DiagnosticsHandler {

  private boolean printInfoDiagnostics = false;

  public void setPrintInfoDiagnostics(boolean value) {
    printInfoDiagnostics = value;
  }

  @Override
  public void info(Diagnostic info) {
    if (printInfoDiagnostics) {
      DiagnosticsHandler.super.info(info);
    }
  }
}
