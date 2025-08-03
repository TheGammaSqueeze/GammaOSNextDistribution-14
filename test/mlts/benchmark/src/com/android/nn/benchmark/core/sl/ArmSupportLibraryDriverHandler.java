package com.android.nn.benchmark.core.sl;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import java.io.IOException;

public class ArmSupportLibraryDriverHandler extends SupportLibraryDriverHandler {
  // This environment variable is required by Arm SL driver, and is used to control
  // different options. It is fully documented in ArmNN repository.
  private static final String ARM_OPTIONS_VAR = "ARMNN_SL_OPTIONS";
  // Arm SL Options:
  // -v : Verbose logging
  // -c GpuAcc : Use GPU backend (rather than CPU)
  private static final String ARM_OPTIONS_VAR_VALUE = "-v -c GpuAcc";

  @Override
  public void prepareDriver(Context context, String nnSupportLibFilePath) throws IOException {
    Log.i(TAG, "Preparing Arm NNAPI SL");
    try {
      Os.setenv(ARM_OPTIONS_VAR, ARM_OPTIONS_VAR_VALUE, /*overwrite=*/true);
      Log.i(TAG, String.format("Overwritten system env variable %s with %s",
        ARM_OPTIONS_VAR, ARM_OPTIONS_VAR_VALUE));
    } catch (ErrnoException errnoException) {
      throw new IOException(String.format("Unable to overwrite system env variable %s with %s",
        ARM_OPTIONS_VAR, ARM_OPTIONS_VAR_VALUE), errnoException);
    }
  }
}
