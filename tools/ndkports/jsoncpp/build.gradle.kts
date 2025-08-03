import com.android.ndkports.AndroidExecutableTestTask
import com.android.ndkports.CMakeCompatibleVersion
import com.android.ndkports.MesonPortTask

val portVersion = "1.9.5"

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

tasks.extractSrc {
    doLast {
        // jsoncpp has a "version" file on the include path that conflicts with
        // https://en.cppreference.com/w/cpp/header/version. Remove it so we can
        // build.
        outDir.get().asFile.resolve("version").delete()
    }
}

val buildTask = tasks.register<MesonPortTask>("buildPort")

tasks.prefabPackage {
    version.set(CMakeCompatibleVersion.parse(portVersion))

    modules {
        create("jsoncpp")
    }
}

tasks.register<AndroidExecutableTestTask>("test") {
    push {
        push(
            buildTask.get().buildDirectoryFor(abi).resolve("jsoncpp_test"),
            File("jsoncpp_test")
        )
        push(
            buildTask.get().installDirectoryFor(abi)
                .resolve("lib/libjsoncpp.so"), File("libjsoncpp.so")
        )
        push(
            toolchain.sysrootLibs.resolve("libc++_shared.so"),
            File("libc++_shared.so")
        )
    }

    run {
        // JsonCpp has other tests, but they require running Python on the
        // device.
        shellTest(
            "jsoncpp_test", listOf(
                "LD_LIBRARY_PATH=$deviceDirectory",
                deviceDirectory.resolve("jsoncpp_test").toString()
            )
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["prefab"])
            pom {
                name.set("JsonCpp")
                description.set("The ndkports AAR for JsonCpp.")
                url.set(
                    "https://android.googlesource.com/platform/tools/ndkports"
                )
                licenses {
                    license {
                        name.set("The JsonCpp License")
                        url.set("https://github.com/open-source-parsers/jsoncpp/blob/master/LICENSE")
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
