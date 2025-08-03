package com.android.ndkports

import com.google.prefab.api.BuildSystemInterface
import com.google.prefab.api.Module
import com.google.prefab.api.Package
import com.google.prefab.api.PlatformDataInterface
import java.io.File

class PrefabSysrootPlugin(
    override val outputDirectory: File, override val packages: List<Package>
) : BuildSystemInterface {

    override fun generate(requirements: Collection<PlatformDataInterface>) {
        prepareOutputDirectory(outputDirectory)

        for (pkg in packages) {
            for (module in pkg.modules) {
                for (requirement in requirements) {
                    installModule(module, requirement)
                }
            }
        }
    }

    private fun installModule(
        module: Module, requirement: PlatformDataInterface
    ) {
        val installDir = outputDirectory.resolve(requirement.targetTriple)
        val includeDir = installDir.resolve("include")

        if (module.isHeaderOnly) {
            installHeaders(module.includePath.toFile(), includeDir)
            return
        }

        val library = module.getLibraryFor(requirement)
        installHeaders(module.includePath.toFile(), includeDir)
        val libDir = installDir.resolve("lib").apply {
            mkdirs()
        }
        library.path.toFile().apply { copyTo(libDir.resolve(name)) }
    }

    private fun installHeaders(src: File, dest: File) {
        src.copyRecursively(dest) { file, exception ->
            if (exception !is FileAlreadyExistsException) {
                throw exception
            }

            if (!file.readBytes().contentEquals(exception.file.readBytes())) {
                val path = file.relativeTo(dest)
                throw RuntimeException(
                    "Found duplicate headers with non-equal contents: $path"
                )
            }

            OnErrorAction.SKIP
        }
    }
}