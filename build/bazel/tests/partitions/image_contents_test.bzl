load("@bazel_skylib//rules:diff_test.bzl", "diff_test")

def image_contents_test(
        name,
        image,
        path,
        expected,
        target_compatible_with = None,
        tags = []):
    """A test that extracts a file from a disk image file, and then asserts that it's identical to some other file."""

    extracted_path = name + path.replace("/", "_") + "_extracted.bin"

    native.genrule(
        name = name + "_extracted",
        tools = [
            "//external/e2fsprogs/debugfs:debugfs",
        ],
        srcs = [image],
        outs = [extracted_path],
        cmd = "$(location //external/e2fsprogs/debugfs:debugfs) -R 'dump " + path + " $@' $<",
        tags = ["manual"],
    )

    diff_test(
        name = name,
        file1 = extracted_path,
        file2 = expected,
        target_compatible_with = target_compatible_with,
        tags = tags,
    )
