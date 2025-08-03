import org.gradle.api.file.FileTreeElement

import java.util.regex.Pattern

class ExcludeUtils {
    /**
     * Returns true if f should be excluded.
     * f is excluded only if:
     * - its absolute path contains [pathMustContain] AND
     * - its path matches one of the regex in [regexToKeep].
     */
    static boolean excludeIfNotIn(
            String pathMustContain,
            ArrayList<String> regexToKeep,
            FileTreeElement f) {
        if (f.isDirectory()) return false
        def absolutePath = f.file.absolutePath

        if (!absolutePath.contains(pathMustContain)) return false

        // keeping only those in regexToKeep
        def toRemove = !regexToKeep.any { absolutePath =~ Pattern.compile(it) }
        // To debug: println("file: ${f.getName()} to remove: ${toRemove}")
        return toRemove
    }

    /**
     * Returns true if f should be excluded.
     * f is excluded only if:
     * - its absolute path contains [pathMustContain] AND
     * - its path matches one of the regex in [regexToExclude].
     */
    static boolean excludeIfIn(
            String pathMustContain,
            ArrayList<String> regexToExclude,
            FileTreeElement f) {
        if (f.isDirectory()) return false
        def absolutePath = f.file.absolutePath

        if (!absolutePath.contains(pathMustContain)) return false

        // keeping only those in regexToKeep
        def toRemove = regexToExclude.any { absolutePath =~ Pattern.compile(it) }
        // To debug: println("file: ${f.getName()} to remove: ${toRemove}")
        return toRemove
    }
}