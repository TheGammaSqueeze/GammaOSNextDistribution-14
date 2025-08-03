def _single_file_impl(ctx):
    return DefaultInfo(
        files = depset(ctx.files.src),
    )

single_file = rule(
    implementation = _single_file_impl,
    attrs = {
        "src": attr.label(
            allow_single_file = True,
        ),
    },
)

# fdo_profile is a temporary wrapper of native.fdo_profile to remove hard-coded
# "<name>.afdo" pattern when getting the profile path in cc_library_shared macro
# TODO(b/267229066): Remove fdo_profile after long-term solution for afdo is
# implemented in Bazel
def fdo_profile(name, profile, **kwargs):
    single_file(
        name = name + "_file",
        src = profile,
        **kwargs
    )
    native.fdo_profile(
        name = name,
        profile = name + "_file",
        **kwargs
    )
