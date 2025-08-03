package com.android.nn.benchmark.core.sl;

import android.content.Context;

public class MediaTekSupportLibraryDriverHandler extends SupportLibraryDriverHandler {
  @Override
  public void prepareDriver(Context context, String nnSupportLibFilePath) {
    // MediaTek SL driver has no specific preparation needed
  }
}
