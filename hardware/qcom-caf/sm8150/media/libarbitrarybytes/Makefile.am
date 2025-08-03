AM_CFLAGS = -Werror
AM_CPPFLAGS = -Werror
AM_CPPFLAGS += -D_ENABLE_QC_MSG_LOG_
AM_CPPFLAGS += -I$(top_srcdir)/mm-core/inc/
AM_CPPFLAGS += -I$(top_srcdir)/mm-video-v4l2/vidc/common/inc/
AM_CPPFLAGS += -I$(top_srcdir)/libarbitrarybytes/inc/
h_sources  =inc/frameparser.h
h_sources +=inc/h264_utils.h
h_sources +=inc/hevc_utils.h
h_sources +=inc/Map.h
h_sources +=inc/qtypes.h

c_sources  =src/frameparser.cpp
c_sources +=src/h264_utils.cpp
c_sources +=src/hevc_utils.cpp


lib_LTLIBRARIES = libarbitrarybytes.la
include_HEADERS = $(h_sources)
libarbitrarybytes_la_SOURCES = $(c_sources)
libarbitrarybytes_la_CFLAGS = $(AM_CFLAGS) $(AM_CPPFLAGS) -fPIC
libarbitrarybytes_la_CPPFLAGS = $(AM_CFLAGS) $(AM_CPPFLAGS) -fPIC
libarbitrarybytes_la_LDFLAGS = -llog -lcutils
