#!/bin/bash

set -ex

readonly AGP_VERSION_INPUT=$1
readonly COMMON_GRADLE_ARGS="--no-daemon --stacktrace --configuration-cache"

readonly JAVA_ANDROID_GRADLE_PROJECTS=(
    "javatests/artifacts/dagger-android/simple"
    "javatests/artifacts/hilt-android/simple"
)
for project in "${JAVA_ANDROID_GRADLE_PROJECTS[@]}"; do
    echo "Running gradle tests for $project with AGP $AGP_VERSION_INPUT"
    AGP_VERSION=$AGP_VERSION_INPUT ./$project/gradlew -p $project assembleDebug $COMMON_GRADLE_ARGS
    AGP_VERSION=$AGP_VERSION_INPUT ./$project/gradlew -p $project testDebug --continue $COMMON_GRADLE_ARGS
done

readonly KOTLIN_ANDROID_GRADLE_PROJECTS=(
    "javatests/artifacts/hilt-android/simpleKotlin"
)
for project in "${KOTLIN_ANDROID_GRADLE_PROJECTS[@]}"; do
    echo "Running gradle tests for $project with AGP $AGP_VERSION_INPUT"
    AGP_VERSION=$AGP_VERSION_INPUT ./$project/gradlew -p $project assembleDebug $COMMON_GRADLE_ARGS
    AGP_VERSION=$AGP_VERSION_INPUT ./$project/gradlew -p $project testWithKaptDebugUnitTest --continue $COMMON_GRADLE_ARGS
    AGP_VERSION=$AGP_VERSION_INPUT ./$project/gradlew -p $project testWithKspDebugUnitTest --continue $COMMON_GRADLE_ARGS
done
