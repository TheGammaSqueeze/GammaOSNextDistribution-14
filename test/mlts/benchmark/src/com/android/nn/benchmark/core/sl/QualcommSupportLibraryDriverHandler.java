package com.android.nn.benchmark.core.sl;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

public class QualcommSupportLibraryDriverHandler extends SupportLibraryDriverHandler {
  private static final String NNAPI_DSP_SL_LIBRARIES_ASSET_PATH = "dsp_loaded_libraries.txt";
  private static final String DSP_LOAD_PATH_ENV_VAR = "ADSP_LIBRARY_PATH";

  @Override
  public void prepareDriver(Context context, String nnSupportLibFilePath) throws IOException {
    boolean isApkPath = nnSupportLibFilePath.contains("apk!");

    String dspLibsFolder = null;
    Log.i(TAG, "Preparing NNAPI SL");
    if (isApkPath) {
      dspLibsFolder = extractDSPLibraries(context);
    } else {
      dspLibsFolder = new File(nnSupportLibFilePath).getParent();
    }

    if (dspLibsFolder != null) {
      try {
        Os.setenv(DSP_LOAD_PATH_ENV_VAR, dspLibsFolder, /*overwrite=*/true);
        Log.i(TAG, String.format("Overwritten system env variable %s with %s",
            DSP_LOAD_PATH_ENV_VAR, dspLibsFolder));
      } catch (ErrnoException errnoException) {
        throw new IOException(String.format("Unable to overwrite system env variable %s with %s",
            DSP_LOAD_PATH_ENV_VAR, dspLibsFolder), errnoException);
      }
    }
  }

  private String extractDSPLibraries(Context context)
      throws IOException {
    try {
      BufferedReader slLibraryListReader
          = new BufferedReader(
          new InputStreamReader(
              context.getAssets().open(NNAPI_DSP_SL_LIBRARIES_ASSET_PATH)));
      final String nnLibTargetFolder = context.getCodeCacheDir().toString();
      final List<String> libsToExtract = slLibraryListReader.lines().collect(Collectors.toList());
      if (libsToExtract.isEmpty()) {
        Log.i(TAG, "No SL library to extract.");
        return null;
      }
      for (final String libraryFile : libsToExtract) {
        if (!extractNnApiSlLibTo(context, libraryFile, nnLibTargetFolder)) {
          throw new FileNotFoundException(String.format("Unable to extract file %s", libraryFile));
        }
      }
      return nnLibTargetFolder;
    } catch (IOException e) {
      Log.e(TAG, "Unable to find list of SL libraries to extract from APK under assets.", e);
      throw e;
    }
  }
}
