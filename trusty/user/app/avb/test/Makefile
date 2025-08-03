CC := g++
CXXFLAGS += -g -O0 -Wall -Werror -Wno-unused -Winit-self \
	-Wpointer-arith -Werror=sign-compare -std=gnu++11 \
	-Werror=return-type -fno-permissive -Wno-deprecated-declarations \
	-fno-exceptions
TARGET := unittest
BASE := ../../..
AVBDIR := $(BASE)/app/avb

INCLUDES := \
	-I $(BASE)/lib/include \
	-I $(AVBDIR) \
	-I $(BASE)/lib/lib/libstdc++-trusty/include \
	-I $(BASE)/$(LKROOT)/include/uapi \
	-I $(BASE)/lib/include/user \

DEFINES := -DSTORAGE_FAKE
SOURCES := \
	$(AVBDIR)/avb_manager.cpp \
	$(AVBDIR)/avb_messages.cpp \
  unittest.cpp
OBJECTS := $(SOURCES:%.cpp=%.o)

$(TARGET): $(OBJECTS)
	$(CC) $(OBJECTS) -o $(TARGET)

%.o: %.cpp
	$(CC) $(INCLUDES) $(DEFINES) $(CXXFLAGS) -c $< -o $@

clean:
	rm $(AVBDIR)/*.o *.o $(TARGET)
