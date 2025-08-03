import com.android.ndkports.AndroidExecutableTestTask
import com.android.ndkports.CMakeCompatibleVersion
import com.android.ndkports.CMakePortTask

val portVersion = "1.14.0"

group = "com.android.ndk.thirdparty"
version = "$portVersion${rootProject.extra.get("snapshotSuffix")}"

plugins {
    id("maven-publish")
    id("com.android.ndkports.NdkPorts")
    distribution
}

ndkPorts {
    ndkPath.set(File(project.findProperty("ndkPath") as String))
    source.set(project.file("src.tar.gz"))
    minSdkVersion.set(21)
}

val buildTask = tasks.register<CMakePortTask>("buildPort") {
    cmake {
        arg("-Dgtest_build_tests=ON")
        arg("-Dgmock_build_tests=ON")
    }
}

tasks.prefabPackage {
    version.set(CMakeCompatibleVersion.parse(portVersion))

    modules {
        create("gtest") {
            static.set(true)
        }
        create("gtest_main") {
            static.set(true)
        }
        create("gmock") {
            static.set(true)
        }
        create("gmock_main") {
            static.set(true)
        }
    }
}

fun findTests(directory: File) = directory.listFiles()!!.filter {
    // There are also many tests that end with test_, but those require running
    // Python on the device.
    it.name.endsWith("test")
}

tasks.register<AndroidExecutableTestTask>("test") {
    push {
        val buildDir = buildTask.get().buildDirectoryFor(abi)
        findTests(buildDir.resolve("googlemock")).forEach { test ->
            push(test, File("googlemock").resolve(test.name))
        }
        findTests(buildDir.resolve("googletest")).forEach { test ->
            push(test, File("googletest").resolve(test.name))
        }
    }

    run {
        val buildDir = buildTask.get().buildDirectoryFor(abi)
        findTests(buildDir.resolve("googlemock")).forEach { test ->
            shellTest(
                test.name, listOf(
                    "cd",
                    deviceDirectory.resolve("googlemock").toString(),
                    "&&",
                    "./${test.name}"
                )
            )
        }
        findTests(buildDir.resolve("googletest")).forEach { test ->
            shellTest(
                test.name, listOf(
                    "cd",
                    deviceDirectory.resolve("googletest").toString(),
                    "&&",
                    "./${test.name}"
                )
            )
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["prefab"])
            pom {
                name.set("GoogleTest")
                description.set("The ndkports AAR for GoogleTest.")
                url.set(
                    "https://android.googlesource.com/platform/tools/ndkports"
                )
                licenses {
                    license {
                        name.set("BSD-3-Clause License")
                        url.set("https://github.com/google/googletest/blob/master/LICENSE")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        name.set("The Android Open Source Project")
                    }
                }
                scm {
                    url.set("https://android.googlesource.com/platform/tools/ndkports")
                    connection.set("scm:git:https://android.googlesource.com/platform/tools/ndkports")
                }
            }
        }
    }

    repositories {
        maven {
            url = uri("${project.buildDir}/repository")
        }
    }
}

distributions {
    main {
        contents {
            from("${project.buildDir}/repository")
            include("**/*.aar")
            include("**/*.pom")
        }
    }
}

tasks {
    distZip {
        dependsOn("publish")
        destinationDirectory.set(File(rootProject.buildDir, "distributions"))
    }
}
