package(default_visibility = ["//visibility:public"])

platform(
    name = "platform",
    constraint_values = [
        "@platforms//os:linux",
        "@platforms//cpu:x86_64",
    ],
    exec_properties = {
        "container-image": "docker://gcr.io/cloud-marketplace/google/rbe-ubuntu18-04@sha256:48b67b41118dbcdfc265e7335f454fbefa62681ab8d47200971fc7a52fb32054",
        "gceMachineType": "e2-standard-16",
        "OSFamily": "Linux",
    },
    parents = ["@local_config_platform//:host"],
)
