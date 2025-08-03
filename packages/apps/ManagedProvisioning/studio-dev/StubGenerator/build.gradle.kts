plugins {
  java
}

//create a single Jar with all dependencies
tasks {
  register<Jar>("fatJar") {
    manifest {
      attributes(
        mapOf(
          "Main-Class" to "com.android.development.SdkGenerator"
        )
      )
    }
    archiveClassifier = "all"
    from(configurations.compileClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(jar.get())
  }
}

repositories {
  mavenCentral()
}

dependencies {
  implementation("org.smali:dexlib2:2.2.7")
  implementation("org.ow2.asm:asm:7.0")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}
