#include "FileLock.h"

#include <bits/lockf.h>

FileLock::FileLock(int fd) : fd_(fd) {}

int FileLock::lock() {
    return lockf(fd_, F_LOCK, 0);
}

int FileLock::unlock() {
    return lockf(fd_, F_ULOCK, 0);
}
