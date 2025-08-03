#include "android-base/thread_annotations.h"

// Encapsulates advisory file lock for a given field descriptor
class CAPABILITY("FileLock") FileLock {
public:
    FileLock(int fd);
    ~FileLock() = default;

    // Acquires advisory file lock. This will block only if called from different processes.
    int lock() ACQUIRE();
    // Releases advisory file lock.
    int unlock() RELEASE();

private:
    int fd_;
};