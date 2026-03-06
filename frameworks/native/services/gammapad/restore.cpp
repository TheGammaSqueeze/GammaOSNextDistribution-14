// gammapad_restore: Restore hidden device nodes after daemon shutdown.
// Reads /data/misc/gammapad/hidden_nodes state file, mknods any missing
// device nodes, then deletes the state file.

#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <unistd.h>

static const char* STATE_FILE = "/data/misc/gammapad/hidden_nodes";

int main() {
    FILE* f = fopen(STATE_FILE, "r");
    if (!f) return 0;  // No state file — nothing to do

    char path[256];
    unsigned int maj, min, mode;
    while (fscanf(f, "%255s %u %u %o", path, &maj, &min, &mode) == 4) {
        struct stat st;
        if (stat(path, &st) == 0) continue;  // Already exists

        dev_t dev = makedev(maj, min);
        if (mknod(path, S_IFCHR | (mode & 07777), dev) == 0) {
            chown(path, 1000, 1004);
        }
    }
    fclose(f);
    unlink(STATE_FILE);
    return 0;
}
