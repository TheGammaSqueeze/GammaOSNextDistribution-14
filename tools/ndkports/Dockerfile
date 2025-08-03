FROM ubuntu:22.04

# Install tools required for port tasks.
RUN apt-get update && apt-get install -y \
    cmake \
    curl \
    ninja-build \
    openjdk-8-jdk \
    python3-pip \
    zip
RUN pip3 install meson

# Install ADB for tests.
RUN curl -L -o platform-tools.zip \
    https://dl.google.com/android/repository/platform-tools-latest-linux.zip
RUN unzip platform-tools.zip platform-tools/adb
RUN mv platform-tools/adb /usr/bin/adb
RUN mkdir -m 0750 /.android

# Build release artifacts.
WORKDIR /src
ENTRYPOINT ["./gradlew", "--no-daemon", "--gradle-user-home=.gradle_home", "--stacktrace", "-PndkPath=/ndk"]
CMD ["-Prelease", "clean", "release"]
