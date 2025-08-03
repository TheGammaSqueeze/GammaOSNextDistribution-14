ShBinaryInfo = provider(
    "Info needed for sh_binary modules",
    fields = {
        "sub_dir": "Optional subdirectory to install into",
        "filename": "Optional name for the installed file",
    },
)

def sh_binary(
        name,
        srcs,
        sub_dir = None,
        filename = None,
        tags = [],
        **kwargs):
    "Bazel macro to correspond with the sh_binary Soong module."

    internal_name = name + "_internal"
    native.sh_binary(
        name = internal_name,
        srcs = srcs,
        tags = ["manual"],
        **kwargs
    )

    # We need this wrapper rule around native.sh_binary in order to provide extra
    # attributes such as filename and sub_dir that are useful when building apex.
    _sh_binary_combiner(
        name = name,
        sub_dir = sub_dir,
        filename = filename,
        dep = internal_name,
        tags = tags,
    )

def _sh_binary_combiner_impl(ctx):
    dep = ctx.attr.dep[DefaultInfo]
    output = ctx.outputs.executable

    ctx.actions.run_shell(
        outputs = [output],
        inputs = [dep.files_to_run.executable],
        command = "cp %s %s" % (dep.files_to_run.executable.path, output.path),
        mnemonic = "CopyNativeShBinary",
    )

    files = depset(direct = [output], transitive = [dep.files])

    return [
        DefaultInfo(
            files = files,
            runfiles = ctx.runfiles().merge(dep.data_runfiles).merge(dep.default_runfiles),
            executable = output,
        ),
        ShBinaryInfo(
            sub_dir = ctx.attr.sub_dir,
            filename = ctx.attr.filename,
        ),
    ]

_sh_binary_combiner = rule(
    implementation = _sh_binary_combiner_impl,
    attrs = {
        "sub_dir": attr.string(),
        "filename": attr.string(),
        "dep": attr.label(mandatory = True),
    },
    provides = [ShBinaryInfo],
    executable = True,
    doc = "Wrapper rule around native.sh_binary to provide extra attributes",
)
