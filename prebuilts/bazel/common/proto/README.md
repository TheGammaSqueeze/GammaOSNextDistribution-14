This directory and its subdirectories contains proto files which represent
Bazel's protocols.

TODO(b/254464719): Sync these protos as part of Bazel release process.

These proto files are obtained directly from cloning Bazel at the current
Bazel release commit, except that a workspace-relative `go_package` option
header is added to each proto file.

Note that proto files should exist in their own subdirectories, as this
is a microfactory requirement so that these proto files may exist in
different packages. (With different package,  there is no risk of
naming conflict between proto messages.)

Run `./regen.sh` to generate `.pb.go` files from proto files under this
directory.
