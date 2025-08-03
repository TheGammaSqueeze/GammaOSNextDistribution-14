# NOTE: For now, don't be tempted to bump this to bullseye-slim, as Oxygen
# still uses a glibc based on Debian 10, not Debian 11
FROM debian:buster-slim AS docker_vmm

ENV container docker
ENV LC_ALL C.UTF-8
ENV DEBIAN_FRONTEND noninteractive

SHELL [ "/bin/bash", "-c" ]

# Set up the user to be the same as the user creating the container.  Not
# strictly necessary, but this way all the permissions of the generated files
# will match.

ARG USER
ARG UID

ENV USER $USER
ENV HOME /home/$USER
ENV CUSTOM_MANIFEST ""

RUN apt update \
    && apt install -y sudo

RUN useradd -m -s /bin/bash $USER -u $UID -d $HOME \
    && passwd -d $USER \
    && echo "$USER ALL=(ALL) NOPASSWD: ALL" >> /etc/sudoers

RUN mkdir /source && chown -R $USER /source
RUN mkdir /output && chown -R $USER /output
RUN mkdir /working && chown -R $USER /working
RUN mkdir /static && chown -R $USER /static

USER $USER
WORKDIR $HOME

COPY --chown=$USER manifest.xml /static/manifest.xml
COPY --chown=$USER custom.xml /static/custom.xml
COPY --chown=$USER rebuild-internal.sh /static/rebuild-internal.sh

RUN TOOLS_DIR=/static/tools /static/rebuild-internal.sh install_packages

VOLUME /source
VOLUME /working
VOLUME /output

FROM docker_vmm AS docker_vmm_persistent

ENV container docker
ENV LC_ALL C.UTF-8
ENV DEBIAN_FRONTEND noninteractive

SHELL [ "/bin/bash", "-c" ]

USER root

CMD ["sleep", "1d"]
