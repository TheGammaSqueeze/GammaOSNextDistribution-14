#ifndef MALI_GRALLOC_ERROR
#define MALI_GRALLOC_ERROR

enum class Error : int32_t {
	/**
	 * No error.
	 */
	NONE = 0,
	/**
	 * Invalid BufferDescriptor.
	 */
	BAD_DESCRIPTOR = 1,
	/**
	 * Invalid buffer handle.
	 */
	BAD_BUFFER = 2,
	/**
	 * Invalid HardwareBufferDescription.
	 */
	BAD_VALUE = 3,
	/**
	 * Resource unavailable.
	 */
	NO_RESOURCES = 5,
	/**
	 * Permanent failure.
	 */
	UNSUPPORTED = 7,
};

#endif
