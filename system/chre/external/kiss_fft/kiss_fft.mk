#
# Kiss FFT
#

# Common Compiler Flags ########################################################

# Include paths.
COMMON_CFLAGS += -I$(CHRE_PREFIX)/external/kiss_fft

# Macros.
COMMON_CFLAGS += -DFIXED_POINT

# Common Source Files ##########################################################

COMMON_SRCS += external/kiss_fft/kiss_fft.c
COMMON_SRCS += external/kiss_fft/kiss_fftr.c
