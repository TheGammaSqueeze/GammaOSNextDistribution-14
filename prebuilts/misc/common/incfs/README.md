The object in this prebuilt directory can be built from
	platform/tools/base

Jar is obtained from studio_linux@7320826[] - git_studio-master-dev

Note: Before dropping the libincfs-prebuilt.jar, please run
the following command to strip the jar manifest file,
which causes hidden dependency on jars in the Class-Path.
$ zip -d libincfs-prebuilt.jar META-INF/MANIFEST.MF
