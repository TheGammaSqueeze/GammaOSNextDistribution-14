#include "Path.h"

#include <string.h>
#include <unistd.h>

namespace gfxstream {
namespace guest {

std::string getProgramDirectory() {
    std::string res;
#if defined(__linux__)
    char path[1024];
    memset(path, 0, sizeof(path));  // happy valgrind!
    int len = readlink("/proc/self/exe", path, sizeof(path));
    if (len > 0 && len < (int)sizeof(path)) {
        char* x = ::strrchr(path, '/');
        if (x) {
            *x = '\0';
            res.assign(path);
        }
    }
#else
#error "Unsupported platform!"
#endif
    return res;
}

}  // namespace guest
}  // namespace gfxstream
