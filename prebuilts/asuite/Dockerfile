# Copyright 2019, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

FROM ubuntu:latest
ARG UID
ARG USER
ARG SRCTOP
ENV IS_CONTAINER=true
ENV USER=${USER}

# Installing AOSP essential packages and creating the local user.
# The UID inside and outside must be the same.
RUN useradd -mu ${UID} ${USER} && \
    export DEBIAN_FRONTEND=noninteractive && \
    apt-get update -qq && apt-get install -y \
        git-core \
        gnupg \
        flex \
        bison \
        gperf \
        build-essential \
        zip \
        unzip \
        curl \
        zlib1g-dev \
        gcc-multilib \
        g++-multilib \
        libc6-dev-i386 \
        lib32ncurses5-dev \
        x11proto-core-dev \
        libx11-dev \
        lib32z-dev \
        libgl1-mesa-dev \
        libxml2-utils \
        tzdata \
        python-dev \
        python3-dev \
        xsltproc \
        sudo

# Configuring tzdata noninteractively
RUN dpkg-reconfigure -f noninteractive tzdata && \
    echo "${USER} ALL=(ALL:ALL)NOPASSWD: ALL" > /etc/sudoers.d/${USER} && \
	mkdir ${SRCTOP} && chown -R ${USER} ${SRCTOP}
RUN curl https://storage.googleapis.com/git-repo-downloads/repo > /bin/repo && chmod +x /bin/repo
ENV LANG=C.UTF-8

USER ${USER}
WORKDIR ${SRCTOP}
