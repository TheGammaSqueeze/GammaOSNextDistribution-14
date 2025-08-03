#!/usr/bin/python3
import maven


def test_maven_path_for_artifact():
    maven_path = maven.maven_path_for_artifact(
        "gmaven", "androidx/core", "core-splashscreen", "1.0.0", "pom", "/")
    assert maven_path == "gmaven/androidx/core/core-splashscreen/1.0.0/core-splashscreen-1.0.0.pom"


def test_artifact_getters():
    maven_artifact = maven.GMavenArtifact("androidx.core:core-splashscreen:1.0.0:aar")
    assert maven_artifact.get_pom_file_url() == ("https://maven.google.com/androidx/core/core-"
                                                 "splashscreen/1.0.0/core-splashscreen-1.0.0.pom")
    assert maven_artifact.get_artifact_url() == ("https://maven.google.com/androidx/core/core-"
                                                 "splashscreen/1.0.0/core-splashscreen-1.0.0.aar")


if __name__ == "__main__":
    test_maven_path_for_artifact()
    test_artifact_getters()
