import org.gradle.api.Action
import org.gradle.api.DomainObjectSet
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

interface AConfigDeclaration {
    val packageName: Property<String>
    val containerName: Property<String>
    val srcFile: ConfigurableFileCollection
}

open class AConfigExtension(private val objectFactory: ObjectFactory) {

    val declarations: DomainObjectSet<AConfigDeclaration> = objectFactory.domainObjectSet(AConfigDeclaration::class.java)

    fun aconfigDeclaration(action: Action<AConfigDeclaration>) {
        val declaration = objectFactory.newInstance(AConfigDeclaration::class.java)
        action.execute(declaration)
        declarations.add(declaration)
    }
}