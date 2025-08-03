package com.android.nn.benchmark.core.sl;

import android.content.Context;
import android.util.Log;
import com.android.nn.benchmark.core.NNTestBase;
import dalvik.system.BaseDexClassLoader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * Abstracts the initialization required to enable a NNAPI Support Library for a given vendor.
 **/
public abstract class SupportLibraryDriverHandler {

  static {
    System.loadLibrary("support_library_jni");
  }

  protected static final String TAG = "NN_TESTBASE";

  private static final String NNAPI_SL_LIBRARIES_LIST_ASSET_PATH = "sl_prebuilt_filelist.txt";
  public static final String NNAPI_SL_LIB_NAME = "libnnapi_sl_driver.so";

  private static native long loadNnApiSlHandle(String nnApiSlPath);

  // Guarded by this
  private static long nnapiSlHandle = 0;

  public synchronized long getOrLoadNnApiSlHandle(Context context, boolean extractNnApiSupportLibrary)
      throws IOException {
    if (nnapiSlHandle == 0) {
      Log.i(TAG, "Initializing NNAPI SL.");

      String nnSupportLibFilePath = null;
      Log.i(TAG, "Preparing NNAPI SL");
      if (extractNnApiSupportLibrary) {
        nnSupportLibFilePath = extractAllAndGetNnApiSlPath(context, NNAPI_SL_LIB_NAME);
      } else {
        nnSupportLibFilePath = getNnApiSlPathFromApkLibraries(context, NNAPI_SL_LIB_NAME);
      }

      prepareDriver(context, nnSupportLibFilePath);

      if (nnSupportLibFilePath != null) {
        nnapiSlHandle = loadNnApiSlHandle(nnSupportLibFilePath);
        if (nnapiSlHandle == 0) {
          Log.e(TAG, String
              .format("Unable load NNAPI SL from '%s'.", nnSupportLibFilePath));
        } else {
          Log.i(TAG, String
              .format("Successfully loaded NNAPI SL from '%s'.", nnSupportLibFilePath));
        }
      } else {
        Log.e(TAG, String
            .format("Unable to find NNAPI SL entry point '%s' in embedded libraries path.",
                NNAPI_SL_LIB_NAME));
      }
    }
    return nnapiSlHandle;
  }

  private static InputStream getInputStreamFromApk(String apkPath, String filePath) throws IOException {
    Log.i(TAG, String.format("Getting input stream from APK '%s' and file '%s'.", apkPath, filePath));

    JarFile jarFile = new JarFile(apkPath);
    JarEntry jarEntry = jarFile.getJarEntry(filePath);
    return jarFile.getInputStream(jarEntry);
  }

  private static String extractAllAndGetNnApiSlPath(Context context, String entryPointName)
      throws IOException {
    try {
      BufferedReader slLibraryListReader
          = new BufferedReader(
          new InputStreamReader(
              context.getAssets().open(NNAPI_SL_LIBRARIES_LIST_ASSET_PATH)));
      String result = null;
      final String nnLibTargetFolder = context.getCodeCacheDir().toString();
      for (final String libraryFile : slLibraryListReader.lines().collect(Collectors.toList())) {
        try {
          boolean copied = extractNnApiSlLibTo(context, libraryFile, nnLibTargetFolder);
          if (copied && libraryFile.equals(entryPointName)) {
            result = new File(nnLibTargetFolder, libraryFile).getAbsolutePath();
          }
        } catch (FileNotFoundException unableToExtractFile) {
          return null;
        }
      }
      return result;
    } catch (IOException e) {
      Log.e(TAG, "Unable to find list of SL libraries under assets.", e);
      throw e;
    }
  }

  protected static boolean extractNnApiSlLibTo(Context context, String libraryFile, String targetFolder)
      throws IOException {
    String sourcePath = getNnApiSlPathFromApkLibraries(context, libraryFile);
    if (sourcePath == null) {
      Log.w(TAG, String.format("Unable to find SL library '%s' to extract assuming is not part of this chipset distribution.", libraryFile));
      return false;
    }

    String[] apkAndLibraryPaths = sourcePath.split("!");
    if (apkAndLibraryPaths.length != 2) {
      final String errorMsg = String.format("Unable to extract %s.", sourcePath);
      Log.e(TAG, errorMsg);
      throw new FileNotFoundException(errorMsg);
    }

    File targetPath = new File(targetFolder, libraryFile);
    try(InputStream in = getInputStreamFromApk(apkAndLibraryPaths[0],
        // Removing leading '/'
        apkAndLibraryPaths[1].substring(1));
        OutputStream out = new FileOutputStream(targetPath)
    ) {
      NNTestBase.copyFull(in, out);
    }

    Log.i(TAG, String.format("Copied '%s' to '%s'.", sourcePath, targetPath));

    return true;
  }

  private static String getNnApiSlPathFromApkLibraries(Context context, String resourceName) {
    BaseDexClassLoader dexClassLoader = (BaseDexClassLoader) context.getClassLoader();
    // Removing the "lib" prefix and ".so" suffix.
    String libShortName = resourceName.substring(3, resourceName.length() - 3);
    String result = dexClassLoader.findLibrary(libShortName);
    if (result != null) {
      return result;
    }
    return dexClassLoader.findLibrary(resourceName);
  }

  // Vendor-specifi preparation steps
  protected abstract void prepareDriver(Context context, String nnSupportLibFilePath) throws IOException;
}
