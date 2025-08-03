FROM centos:7

# TODO: ncurses-devel results in a dependency on a libncursesw.so.5 (from
# _curses.cpython-39-x86_64-linux-gnu.so).
# TODO: There is no readline module, so the python3 REPL has no line editing.

RUN yum -y update && \
  yum -y install \
  autoconf \
  automake \
  bzip2 \
  bzip2-devel \
  gcc \
  git \
  libtool \
  make \
  ncurses-devel \
  python3 \
  zlib-devel \
