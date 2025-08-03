plugins {
    id("com.android.library")
}

android {
    sourceSets {
        sourceSets.forEach {
            it.java.setSrcDirs(emptyList<String>())
            it.kotlin.setSrcDirs(emptyList<String>())
            it.res.setSrcDirs(emptyList<String>())
            it.assets.setSrcDirs(emptyList<String>())
            it.aidl.setSrcDirs(emptyList<String>())
            it.renderscript.setSrcDirs(emptyList<String>())
            it.baselineProfiles.setSrcDirs(emptyList<String>())
            it.jni.setSrcDirs(emptyList<String>())
            it.jniLibs.setSrcDirs(emptyList<String>())
            it.resources.setSrcDirs(emptyList<String>())
        }
    }
}