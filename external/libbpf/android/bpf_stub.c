#include "../src/libbpf_internal.h"

enum libbpf_strict_mode libbpf_mode = 0;


// Another approach would be to log here, but we just return in order to avoid
// spamming logs since some paths use libbpf_print fairly heavily. Actual error
// cases generally return informative errors anyway.
__attribute__((format(printf, 2, 3)))
void libbpf_print(enum libbpf_print_level level, const char *format, ...)
{
    return;
}

bool kernel_supports(const struct bpf_object *obj, enum kern_feature_id feat_id)
{
    return false;
}


