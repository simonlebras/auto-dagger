package se.ansman.dagger.auto.compiler

import org.jetbrains.kotlin.incremental.deleteRecursivelyOrThrow
import org.jetbrains.kotlin.incremental.mkdirsOrThrow
import org.junit.jupiter.api.function.Executable
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.fail

class AutoDaggerTestCase(
    private val testName: String,
    private val compilation: AutoDaggerCompilation,
    private val sources: List<TestSourceFile>,
    private val writeExpectedFilesTo: File?,
    private val expectedFiles: Map<String, String>,
) : Executable {
    override fun execute() {
        val result = compilation.compile(sources)
        result.assertIsSuccessful()
        if (writeExpectedFilesTo != null) {
            result.writeExpectedFiles(writeExpectedFilesTo)
            return
        }
        for ((fileName, contents) in expectedFiles) {
            val actual = result.readGeneratedFile(fileName)
            assertEquals(contents, actual, "$fileName did not match expected contents.")
            try {
                result.loadClass(buildString {
                    append("se.ansman.")
                    append(testName.replace('-', '.'))
                    append(".")
                    append(fileName.substringBeforeLast("."))
                })
            } catch (e: Exception) {
                throw AssertionError("Failed to load class $fileName", e)
            }
        }
        val generated = result.filesGeneratedByAnnotationProcessor.map { it.name }.toSet()
        val unexpected = generated - expectedFiles.keys
        if (unexpected.isNotEmpty()) {
            fail(unexpected.joinToString(
                prefix = "Unexpected files were generated: \n\n",
                separator = "\n\n",
                transform = { formatFile(it, result.readGeneratedFile(it), includeLineNumbers = false) }
            ))
        }
    }

    private fun AutoDaggerCompilation.Result.writeExpectedFiles(writeExpectedFilesTo: File) {
        writeExpectedFilesTo.deleteRecursivelyOrThrow()
        writeExpectedFilesTo.mkdirsOrThrow()
        filesGeneratedByAnnotationProcessor.forEach { file ->
            writeExpectedFilesTo.resolve("${file.name}.txt").writeText(file.readText().trim())
        }
    }

    override fun toString(): String = "AutoDaggerTestCase($testName)"
}