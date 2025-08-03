load("@bazel_skylib//rules:diff_test.bzl", "diff_test")

def apex_diff_test(
        name,
        apex1,
        apex2,
        target_compatible_with = None,
        expected_diff = None):
    """A test that compares the content list of two APEXes, determined by `deapexer`."""

    native.genrule(
        name = name + "_apex1_deapex",
        tools = [
            "//system/apex/tools:deapexer",
            "//external/e2fsprogs/debugfs:debugfs",
        ],
        srcs = [apex1],
        outs = [name + ".apex1.txt"],
        cmd = "$(location //system/apex/tools:deapexer) --debugfs_path=$(location //external/e2fsprogs/debugfs:debugfs) list $< > $@",
    )

    native.genrule(
        name = name + "_apex2_deapex",
        tools = [
            "//system/apex/tools:deapexer",
            "//external/e2fsprogs/debugfs:debugfs",
        ],
        srcs = [apex2],
        outs = [name + ".apex2.txt"],
        cmd = "$(location //system/apex/tools:deapexer) --debugfs_path=$(location //external/e2fsprogs/debugfs:debugfs) list $< > $@",
    )

    if expected_diff == None:
        diff_test(
            name = name,
            file1 = name + ".apex1.txt",
            file2 = name + ".apex2.txt",
            target_compatible_with = target_compatible_with,
        )
    else:
        # Make our own diff to compare against the expected one
        native.genrule(
            name = name + "_apex1_apex2_diff",
            srcs = [
                name + ".apex1.txt",
                name + ".apex2.txt",
            ],
            outs = [name + ".apex1.apex2.diff.txt"],
            # Expected to generate a diff (and return a failing exit status)
            cmd_bash = "diff $(SRCS) > $@ || true",
        )
        diff_test(
            name = name,
            file1 = name + ".apex1.apex2.diff.txt",
            file2 = expected_diff,
            target_compatible_with = target_compatible_with,
        )
