plugins {
  `java-gradle-plugin`
  `kotlin-dsl`
  `groovy-gradle-plugin`
}

val androidTop = "$rootDir/../../../../../../"

// Due to a gradle bug, we also need to have a symlink to build, which is why
// SystemUIGoogle/studio-dev/SysUIGradleProject/buildSrc/build points to
// ../../../../../../../out/gradle/build/buildSrc/build.
// The symlink dest dir is created in studiow. See: https://github.com/gradle/gradle/issues/13847
buildDir = file("$androidTop/out/gradle/build/buildSrc/build")

val libDir = "${androidTop}/vendor/unbundled_google/libraries/androidbuildinternal"

dependencies {
  implementation(localGroovy())
  implementation(libs.scriptClasspath.android)
  implementation(libs.scriptClasspath.kotlin)
  implementation(libs.scriptClasspath.errorprone)
  implementation(libs.scriptClasspath.nullaway)
  implementation(libs.scriptClasspath.protobuf)
  implementation(libs.scriptClasspath.hilt)
  implementation(libs.guava)

  // dependencies for jar fetcher
  implementation("com.google.api-client:google-api-client:1.33.0")
  implementation("org.apache.commons:commons-compress:1.21")
  implementation("com.google.oauth-client:google-oauth-client-jetty:1.33.0")
  implementation(fileTree(libDir) { include("libandroid_build_v3_java.jar") })
}
