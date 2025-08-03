#ifndef RUST_DEMANGLE_H_
#define RUST_DEMANGLE_H_

#ifdef __cplusplus
extern "C" {
#endif

// For size_t
#include <stddef.h>

char *rustc_demangle(const char *mangled, char *out, size_t *len, int *status);

#ifdef __cplusplus
}
#endif

#endif // RUSTC_DEMANGLE_H_
