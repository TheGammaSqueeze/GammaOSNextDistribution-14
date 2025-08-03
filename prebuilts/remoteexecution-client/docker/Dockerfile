# This Dockerfile is used to build a Docker container for running remote
# actions for Android Platform build on RBE.
FROM ubuntu:14.04
RUN apt-get update && apt-get -y upgrade
# The language pack is required to avoid silent encoding errors due to missing locales.
RUN apt-get -y install python unzip language-pack-en-base
