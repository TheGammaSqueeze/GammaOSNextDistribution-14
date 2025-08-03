FROM ubuntu:20.04
ARG userid
ARG groupid
ARG username
ARG ltproot

RUN apt-get update && apt-get install -y python autoconf build-essential libssl-dev pkgconf

# Set up the user so that files are owned by the proper user
RUN groupadd -g $groupid $username \
 && useradd -m -u $userid -g $groupid $username
RUN mkdir -p $ltproot && chown $userid $ltproot
ENV LTP_ROOT=$ltproot
USER $username

CMD /src/android/tools/docker_entry.sh
