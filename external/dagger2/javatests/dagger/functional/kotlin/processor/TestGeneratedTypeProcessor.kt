package dagger.functional.kotlin.processor

import com.google.auto.service.AutoService
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.TypeSpec
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement

/**
 * A processor to be used in functional tests that will generate a simple class with fqname
 * 'dagger.functional.kotlin.GeneratedType'. This processor is useful for testing situations
 * with KAPT where a type is not resolvable and for which KAPT will generate stubs containing
 * the 'error.NonExistentClass' type.
 */
@AutoService(Processor::class)
class TestGeneratedTypeProcessor : AbstractProcessor() {

  private var isSourceGenerated = false

  override fun getSupportedAnnotationTypes() =
    mutableSetOf("dagger.functional.kotlin.processor.TriggerGeneratedTypeProcessor")

  override fun getSupportedSourceVersion() = SourceVersion.latestSupported()

  override fun process(
    annotations: MutableSet<out TypeElement>,
    roundEnv: RoundEnvironment
  ): Boolean {
    if (isSourceGenerated) {
      return false
    }

    JavaFile.builder(
      "dagger.functional.kotlin",
      TypeSpec.classBuilder("GeneratedType")
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .build()
    ).build().writeTo(processingEnv.filer)

    isSourceGenerated = true

    return false
  }
}
