import com.android.ndkports.AdHocPortTask
import com.android.ndkports.AndroidExecutableTestTask
import com.android.ndkports.CMakeCompatibleVersion

val portVersion = "3.2.1"

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

val buildTask = tasks.register<AdHocPortTask>("buildPort") {
    builder {
        run {
            args(
                sourceDirectory.resolve("Configure").absolutePath,
                "android-${toolchain.abi.archName}",
                "-D__ANDROID_API__=${toolchain.api}",
                "--prefix=${installDirectory.absolutePath}",
                "--openssldir=${installDirectory.absolutePath}",
                "no-sctp",
                "shared"
            )

            env("ANDROID_NDK", toolchain.ndk.path.absolutePath)
            env("PATH", "${toolchain.binDir}:${System.getenv("PATH")}")
        }

        run {
            args("make", "-j$ncpus", "SHLIB_EXT=.so")

            env("ANDROID_NDK", toolchain.ndk.path.absolutePath)
            env("PATH", "${toolchain.binDir}:${System.getenv("PATH")}")
        }

        run {
            args("make", "install_sw", "SHLIB_EXT=.so")

            env("ANDROID_NDK", toolchain.ndk.path.absolutePath)
            env("PATH", "${toolchain.binDir}:${System.getenv("PATH")}")
        }
    }
}

tasks.prefabPackage {
    version.set(CMakeCompatibleVersion.parse(portVersion))

    licensePath.set("LICENSE.txt")

    modules {
        create("crypto")
        create("ssl")
    }
}

tasks.register<AndroidExecutableTestTask>("test") {
    val srcDir = tasks.extractSrc.get().outDir.asFile.get()
    val testSrc = srcDir.resolve("test/ssl-tests")
    val deviceTestRelPath = File("testconf")

    val unsupportedTests = listOf(
        // This test is empty and appears to just be broken in 1.1.1k.
        "16-certstatus.conf",
        // zlib support is not enabled.
        "22-compression.conf",
        // Android does not support SCTP sockets and this test requires them.
        "29-dtls-sctp-label-bug.conf"
    )

    push {
        val ignoredExtensions = listOf("o", "d")
        val buildDirectory = buildTask.get().buildDirectoryFor(abi)
        push(
            srcDir.resolve("test/ct/log_list.conf"), File("log_list.conf")
        )
        for (file in buildDirectory.walk()) {
            if (!file.isFile) {
                continue
            }

            if (file.extension in ignoredExtensions) {
                continue
            }

            push(file, file.relativeTo(buildDirectory))
        }
        for (file in testSrc.walk()) {
            if (file.extension == "conf") {
                push(
                    file, deviceTestRelPath.resolve(file.relativeTo(testSrc))
                )
            }
        }
        push(srcDir.resolve("test/certs"), File("certs"))
    }

    run {
        // https://github.com/openssl/openssl/blob/master/test/README.ssltest.md
        val sslTest = deviceDirectory.resolve("test/ssl_test")
        val ctlogFile = deviceDirectory.resolve("log_list.conf")
        val testCertDir = deviceDirectory.resolve("certs")
        for (file in testSrc.walk()) {
            val test = deviceDirectory.resolve(deviceTestRelPath)
                .resolve(file.relativeTo(testSrc))
            if (file.extension == "conf" && file.name !in unsupportedTests) {
                shellTest(
                    file.relativeTo(testSrc).toString(), listOf(
                        "LD_LIBRARY_PATH=$deviceDirectory",
                        "CTLOG_FILE=$ctlogFile",
                        "TEST_CERTS_DIR=$testCertDir",
                        sslTest.toString(),
                        test.toString()
                    )
                )
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["prefab"])
            pom {
                name.set("OpenSSL")
                description.set("The ndkports AAR for OpenSSL.")
                url.set(
                    "https://android.googlesource.com/platform/tools/ndkports"
                )
                licenses {
                    license {
                        name.set("Dual OpenSSL and SSLeay License")
                        url.set("https://www.openssl.org/source/license-openssl-ssleay.txt")
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
