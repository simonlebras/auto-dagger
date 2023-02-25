package se.ansman.dagger.auto

import org.jetbrains.kotlin.incremental.mkdirsOrThrow
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.toPath
import kotlin.streams.asSequence


abstract class BaseTest(
    private val expectedFilesDirectoryName: String,
    private val compilation: (tempDirectory: File) -> AutoDaggerCompilation
) {
    @TestFactory
    fun resources(@TempDir tempDirectory: File): Iterable<DynamicNode> =
        Files.list(ClassLoader.getSystemResource("tests").toURI().toPath())
            .asSequence()
            .map { type ->
                val typeName = type.fileName.toString()
                DynamicContainer.dynamicContainer(
                    typeName,
                    testsFromResources(tempDirectory.resolve(typeName), typeName)
                )
            }
            .ifEmpty { error("No tests found") }
            .asIterable()

    private fun testsFromResources(tempDirectory: File, type: String): Iterable<DynamicTest> =
        Files.list(ClassLoader.getSystemResource("tests/$type").toURI().toPath())
            .asSequence()
            .mapNotNull { test ->
                val name = test.fileName.toString()
                val expectedDirectory = test.resolve(expectedFilesDirectoryName)
                if (!expectedDirectory.exists()) {
                    return@mapNotNull null
                }
                DynamicTest.dynamicTest(
                    name,
                    AutoDaggerTestCase(
                        testName = name,
                        compilation = compilation(tempDirectory.resolve(name).apply { mkdirsOrThrow() }),
                        sources = Files.list(test)
                            .asSequence()
                            .filter { it.isRegularFile() }
                            .map(Path::toFile)
                            .map(TestSourceFile::File)
                            .toList(),
                        expectedFiles = Files.list(expectedDirectory)
                            .asSequence()
                            .associateBy({ it.fileName.toString().removeSuffix(".txt") }, { it.readText() })
                    )
                )
            }
            .ifEmpty { error("No tests found") }
            .asIterable()

    @Nested
    @DisplayName("Auto Initialize")
    inner class AutoInitializeTestCase {
        @Test
        fun `the annotated class must be scoped`(@TempDir tempDirectory: File) {
            compilation(tempDirectory)
                .compile(
                    """
                    package se.ansman
        
                    object Outer {
                      @se.ansman.dagger.auto.AutoInitialize(priority = 4711)
                      class SomeThing @javax.inject.Inject constructor()
                    }
                    """
                )
                .assertFailedWithMessage(Errors.AutoInitialize.unscopedType)
        }

        @Test
        fun `the annotated class can only be scoped with @Singleton`(@TempDir tempDirectory: File) {
            compilation(tempDirectory)
                .compile(
                    """
                    package se.ansman
                    
                    @javax.inject.Scope
                    annotation class SomeScope
        
                    @se.ansman.dagger.auto.AutoInitialize
                    @SomeScope
                    class SomeThing @javax.inject.Inject constructor()
                    """
                )
                .assertFailedWithMessage(Errors.AutoInitialize.wrongScope)
        }

        @Test
        fun `the annotated method must be a provider or a binding`(@TempDir tempDirectory: File) {
            compilation(tempDirectory)
                .compile(
                    """
                    package se.ansman
            
                    @dagger.Module
                    object SomeModule {
                        @se.ansman.dagger.auto.AutoInitialize
                        @javax.inject.Singleton
                        fun provideString(): String = "string"
                    }
                    """
                )
                .assertFailedWithMessage(Errors.AutoInitialize.invalidAnnotatedMethod)
        }

        @Test
        fun `the annotated method must not be top level`(@TempDir tempDirectory: File) {
            compilation(tempDirectory)
                .compile(
                    """
                    package se.ansman
            
                    @dagger.Provides
                    @se.ansman.dagger.auto.AutoInitialize
                    @javax.inject.Singleton
                    fun provideString(): String = "string"
                    """
                )
                .assertFailedWithMessage(Errors.AutoInitialize.methodInNonModule)
        }

        @Test
        fun `the annotated method must not be in a module`(@TempDir tempDirectory: File) {
            compilation(tempDirectory)
                .compile(
                    """
                    package se.ansman
            
                    object SomeModule {
                        @dagger.Provides
                        @se.ansman.dagger.auto.AutoInitialize
                        @javax.inject.Singleton
                        fun provideString(): String = "string"
                    }
                    """
                )
                .assertFailedWithMessage(Errors.AutoInitialize.methodInNonModule)
        }
    }

    @Nested
    @DisplayName("Auto Bind")
    inner class AutoBindTestCase {
        @Test
        fun `supertype must not be generic`(@TempDir tempDirectory: File) {
            compilation(tempDirectory)
                .compile(
                    """
                    package se.ansman
        
                    @se.ansman.dagger.auto.AutoBind
                    class SomeThing<T> : Runnable
                    """
                )
                .assertFailedWithMessage(Errors.AutoBind.genericType)
        }
        
        @Test
        fun `no supertypes`(@TempDir tempDirectory: File) {
            compilation(tempDirectory)
                .compile(
                    """
                    package se.ansman
        
                    @se.ansman.dagger.auto.AutoBind
                    class SomeThing
                    """
                )
                .assertFailedWithMessage(Errors.AutoBind.noSuperTypes)
        }

        @Test
        fun `invalid asTypes`(@TempDir tempDirectory: File) {
            compilation(tempDirectory)
                .compile(
                    """
                    package se.ansman
        
                    @se.ansman.dagger.auto.AutoBind(asTypes = [Runnable::class])
                    class SomeThing
                    """
                )
                .assertFailedWithMessage(Errors.AutoBind.missingBoundType("java.lang.Runnable"))
        }

        @Test
        fun `indirect supertype`(@TempDir tempDirectory: File) {
            compilation(tempDirectory)
                .compile(
                    """
                    package se.ansman
        
                    @se.ansman.dagger.auto.AutoBind(asTypes = [AutoCloseable::class])
                    class SomeThing : java.io.Closeable
                    """
                )
                .assertFailedWithMessage(Errors.AutoBind.missingDirectSuperType("java.lang.AutoCloseable"))
        }

        @Test
        fun `missing binding key`(@TempDir tempDirectory: File) {
            compilation(tempDirectory)
                .compile(
                    """
                    package se.ansman
        
                    @se.ansman.dagger.auto.AutoBindIntoMap
                    class SomeThing : java.io.Closeable
                    """
                )
                .assertFailedWithMessage(Errors.AutoBind.missingBindingKey)
        }
    }
}