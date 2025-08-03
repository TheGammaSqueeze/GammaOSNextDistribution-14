// Package android provides android-specific extensions to the upstream module.
package android

import "google.golang.org/protobuf/internal/detrand"

// DisableRand disables the randomness introduced into JSON and Text encodings.
// This function is not concurrent-safe and must be called during program init.
//
// This does not guarantee long term stability of the JSON/Text encodings, but
// that isn't necessary in Soong or similar build tools. They are only
// interested in longer stable periods, as any change in the output may
// introduce significant extra building during incremental builds. That price
// is expected when upgrading library versions (and will be paid then even
// without a format change, as the reader and writer packages will often both
// change), but is not desired when changing other parts of the executables.
//
// See https://github.com/golang/protobuf/issues/1121 for more discussion.
func DisableRand() {
	detrand.Disable()
}
