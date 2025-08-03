"""Deletes the existing bindings, then rebuild using the source .proto file."""

import os
from shutil import copyfile

UP = '../'
PROTO_PATH = 'src/config/proto/chromiumos/config/api/test/tls/'
PROTO_NAME = 'commontls.proto'
DEST_PROTO_NAME = 'autotest_common.proto'
DEP_PROTO_RELATIVE_PATH = 'dependencies/longrunning/'
DEP_PROTO_NAME = 'operations.proto'

BUILD_CMD = (
        'python -m grpc_tools.protoc -I. --python_out=. --grpc_python_out=. {} {}'
        .format(DEST_PROTO_NAME,
                os.path.join(DEP_PROTO_RELATIVE_PATH, DEP_PROTO_NAME)))


def delete_old_protos():
    """Delete any existing protos or built proto bindings."""
    for file in os.listdir('.'):
        if 'autotest_common' in file:
            os.remove(file)

    for file in os.listdir(DEP_PROTO_RELATIVE_PATH):
        if 'operations' in file:
            os.remove(os.path.join(DEP_PROTO_RELATIVE_PATH, file))


def copy_proto_from_src():
    """Copy the proto from the src dirs to the local dir."""
    copy_list = [(get_proto_path(), DEST_PROTO_NAME),
                 (get_proto_deps_dir(),
                  os.path.join(DEP_PROTO_RELATIVE_PATH, DEP_PROTO_NAME))]

    for src, dest in copy_list:
        if os.path.isfile(src):
            copyfile(src, dest)
        else:
            raise Exception('Proto missing at %s' % src)


def get_proto_path():
    """Return the full path of the commontls.proto from TLS."""
    return os.path.join(UP * get_current_depth(), PROTO_PATH, PROTO_NAME)


def get_proto_deps_dir():
    """Return the full path of the operations.proto from TLS."""
    return os.path.join(UP * get_current_depth(), PROTO_PATH,
                        DEP_PROTO_RELATIVE_PATH, DEP_PROTO_NAME)


def get_current_depth():
    """Return the current depth off /src/ within the file structure."""
    dirs = os.getcwd().split('/')
    src_level = dirs.index('src')
    return len(dirs) - src_level


def modify_proto():
    """Change the full path for the dependencies for a local one."""
    # This is likely a dirty hack, but compiling with the full src in autotest
    # doesn't work. Open to suggestions for alternatives.

    #TODO (dbeckett@) b/183220746, work on a better thats not a hack...
    with open(DEST_PROTO_NAME, 'r+') as f:
        original = f.read()
    new = original.replace(
            'import "chromiumos/config/api/test/tls/dependencies/longrunning/operations.proto";',
            'import "dependencies/longrunning/operations.proto";')
    with open(DEST_PROTO_NAME, 'w') as wf:
        wf.write(new)


def create_bindings():
    os.system(BUILD_CMD)


def main():
    delete_old_protos()
    copy_proto_from_src()
    modify_proto()
    create_bindings()


if __name__ == "__main__":
    main()
