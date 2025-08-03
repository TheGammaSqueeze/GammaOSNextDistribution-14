# Makefile.am - Automake script for cld80211-lib
ACLOCAL_AMFLAGS = -I m4

AM_CFLAGS = -Wall

if DEBUG
AM_CFLAGS += -g
else
AM_CFLAGS += -O2
endif

AM_CFLAGS += -Wno-unused-parameter -Wno-int-to-pointer-cast \
             -Wno-maybe-uninitialized -Wno-parentheses -DLE_BUILD \
             -D_GNU_SOURCE=1

h_sources = cld80211_lib.h
library_includedir = $(pkgincludedir)
library_include_HEADERS = $(h_sources)

libcld80211_la_SOURCES = cld80211_lib.c
libcld80211_la_CFLAGS = ${AM_CFLAGS} ${LIBNL_CFLAGS}
libcld80211_la_LIBADD = ${LIBNL_LIBS}
lib_LTLIBRARIES = libcld80211.la

pkgconfigdir = $(libdir)/pkgconfig
pkgconfig_DATA = cld80211-lib.pc
EXTRA_DIST = $(pkgconfig_DATA)
