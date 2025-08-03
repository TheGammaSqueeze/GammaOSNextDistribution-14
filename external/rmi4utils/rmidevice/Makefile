CXX ?= g++
AR ?= ar
RANLIB ?= ranlib
CPPFLAGS += -I../include -I./include
CPPFLAGS += -D_FILE_OFFSET_BITS=64 -D_LARGEFILE_SOURCE -D_LARGEFILE64_SOURCE
CXXFLAGS += -fPIC -Wall
RMIDEVICESRC = rmifunction.cpp rmidevice.cpp hiddevice.cpp util.cpp
RMIDEVICEOBJ = $(RMIDEVICESRC:.cpp=.o)
LIBNAME = librmidevice.so
STATIC_LIBNAME = librmidevice.a
LDFLAGS = -shared -Wl,-soname,$(LIBNAME)

all: $(LIBNAME) $(STATIC_LIBNAME)

$(LIBNAME): $(RMIDEVICEOBJ)
	$(CXX) $(CXXFLAGS) $(LDFLAGS) $^ -o $@

$(STATIC_LIBNAME): $(RMIDEVICEOBJ)
	$(AR) crv $(STATIC_LIBNAME) $^
	$(RANLIB) $(STATIC_LIBNAME)

clean:
	rm -f $(RMIDEVICEOBJ) $(LIBNAME)* $(STATIC_LIBNAME)*
