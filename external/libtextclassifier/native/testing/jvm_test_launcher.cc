#include <jni.h>

#include "utils/testing/logging_event_listener.h"
#include "gtest/gtest.h"

JNIEnv* g_jenv = nullptr;
jobject g_context = nullptr;

// This method is called from Java to trigger running of all the tests.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_google_android_textclassifier_tests_JvmTestLauncher_testsMain(
    JNIEnv* env, jclass clazz, jobject context) {
  g_jenv = env;
  g_context = context;

  char arg[] = "jvm_test_launcher";
  std::vector<char*> argv = {arg};
  int argc = 1;
  testing::InitGoogleTest(&argc, argv.data());
  testing::UnitTest::GetInstance()->listeners().Append(
      new libtextclassifier3::LoggingEventListener());
  return RUN_ALL_TESTS() == 0;
}