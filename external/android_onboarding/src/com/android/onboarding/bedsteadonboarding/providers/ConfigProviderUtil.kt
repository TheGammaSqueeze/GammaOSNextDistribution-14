package com.android.onboarding.bedsteadonboarding.providers

import android.content.ContentResolver
import android.content.Context
import android.net.Uri

/**
 * Utility class for [TestContentProvider]. This contains constants representing content provider
 * paths and also functions on creating uri using those.
 */
object ConfigProviderUtil {
  /**
   * The relative path for content provider representing all test configurations. This should be
   * used to delete all the configs at once.
   */
  internal const val TEST_CONFIG_PATH = "test_config"

  /** The projection/ column name representing one single allowed node in a test. */
  const val TEST_NODE_CLASS_COLUMN = "allowed_node"

  /**
   * Returns the base content provider [Uri]. Eg: content://authority
   *
   * @param authority the content provider authority to use for constructing Uri
   * @return the base content provider path.
   */
  fun getBaseContentUri(authority: String): Uri =
    Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority).build()

  /**
   * Returns the uri for all the test configuration Eg: content://authority/test_config
   *
   * @param authority the content provider authority to use for constructing Uri
   */
  fun getTestConfigUri(authority: String): Uri =
    getBaseContentUri(authority).buildUpon().path(TEST_CONFIG_PATH).build()

  /**
   * Returns the uri for all the test configuration Eg: content://authority/test_config
   *
   * @param context context of the application being executed
   */
  fun getTestConfigUri(context: Context): Uri = getTestConfigUri(getAuthority(context.packageName))

  // LINT.IfChange(authority)
  /**
   * Given app package name it returns content provider authority for a given package
   *
   * @param packageName the app package name
   * @return the content provider authority for a given package
   */
  fun getAuthority(packageName: String): String = "$packageName.testprovider"
  // LINT.ThenChange(src/com/android/onboarding/bedsteadonboarding/AndroidManifest.xml:authority)
}
