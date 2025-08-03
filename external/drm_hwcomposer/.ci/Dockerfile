FROM ubuntu:22.10

ENV DEBIAN_FRONTEND=noninteractive

ENV PATH="/home/user/bin:${PATH}"

# Taking into account layer structure, everything should be done within one layer.
RUN apt-get update && apt-get upgrade -y && \
    apt-get install -y clang-15 clang-tidy-15 clang-format-15 git libdrm-dev blueprint-tools libgtest-dev clang \
    llvm make python3 python3-pip wget sudo rsync lld pkg-config ninja-build && \
    pip3 install mako meson jinja2 ply pyyaml

ENV RUN_USER user
ENV RUN_UID 1000

ENV USER_HOME /home/${RUN_USER}

RUN mkdir -pv ${USER_HOME}
# Create new user
RUN adduser \
    --gecos 'Build User' \
    --shell '/usr/bin/bash' \
    --uid ${RUN_UID} \
    --disabled-login \
    --disabled-password ${RUN_USER} \
    && adduser ${RUN_USER} sudo

RUN chown -R ${RUN_USER}:${RUN_USER} ${USER_HOME} && chmod -R 775 ${USER_HOME}

# Ensure sudo group users are not
# asked for a password when using
# sudo command by ammending sudoers file
RUN echo '%sudo ALL=(ALL) NOPASSWD:ALL' >> \
/etc/sudoers

# Pass control to a newly created user
USER ${RUN_USER}

# Install aospless package (produced by GloDroid/aospext)
RUN wget -P ${USER_HOME} https://gitlab.freedesktop.org/drm-hwcomposer/drm-hwcomposer/uploads/28ef9379b1a0ec1ee19a17825b0f3f3f/aospless_drm_hwcomposer_arm64.tar.xz && \
    cd ${USER_HOME} && \
    (echo 96b2148d04c50cf36d4151ae022e665764b8ca3317712e9467a433b62c545a43 aospless_drm_hwcomposer_arm64.tar.xz | sha256sum --check) && \
    tar xf aospless_drm_hwcomposer_arm64.tar.xz && \
    rm -r ${USER_HOME}/aospless/src && ln -s ../drm_hwcomposer/ ${USER_HOME}/aospless/src

# Create project path
RUN mkdir -pv ${USER_HOME}/drm_hwcomposer
WORKDIR ${USER_HOME}/drm_hwcomposer

RUN git config --global user.name "FIRST_NAME LAST_NAME" && git config --global user.email "MY_NAME@example.com"

CMD [ "/bin/bash" ]
