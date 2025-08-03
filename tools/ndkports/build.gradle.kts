buildscript {
    val snapshotSuffix = if (hasProperty("release")) {
        // We're still tagging releases as betas until we have more thorough
        // test automation.
        "-beta-1"
    } else {
        "-SNAPSHOT"
    }

    extra.apply {
        set("snapshotSuffix", snapshotSuffix)
    }
}

group = "com.android"
version = "1.0.0${extra.get("snapshotSuffix")}"

repositories {
    mavenCentral()
    google()
}

tasks.register("release") {
    dependsOn(project.getTasksByName("test", true))
    dependsOn(project.getTasksByName("distZip", true))
}
