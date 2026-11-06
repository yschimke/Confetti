package dev.johnoreilly.confetti.livery

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The contract is enforced at three points: Kotlin types (designs and the app use Contracts.kt),
 * capture (a template can only read and emit what its contract declares), and here: the catalog
 * checked into the repository is exactly what the sources generate, and passes the checks the
 * Livery publisher can't make. Fix a failure with ./gradlew :livery-catalog:generateLiveryCatalog.
 */
class CatalogTest {
    private val catalog = File("catalog")
    private val captured by lazy { AllDesigns.flatMap { d -> d.templates.map { capture(d, it) } } }

    @Test fun contractChecksPass() {
        assertEquals(emptyList(), checkContracts(captured, catalog))
    }

    @Test fun checkedInCatalogMatchesTheSources() {
        val fresh = Files.createTempDirectory("catalog").toFile()
        catalog.copyRecursively(fresh, overwrite = true)
        main(arrayOf(fresh.absolutePath))
        val stale = listOf("contracts", "designs").flatMap { dir ->
            val a = fresh.resolve(dir).walk().filter { it.isFile }.associateBy { it.relativeTo(fresh).path }
            val b = catalog.resolve(dir).walk().filter { it.isFile }.associateBy { it.relativeTo(catalog).path }
            (a.keys + b.keys).filter { a[it]?.readBytes()?.contentEquals(b[it]?.readBytes()) != true }
        }
        assertTrue(stale.isEmpty(), "catalog/ is stale, run :livery-catalog:generateLiveryCatalog: $stale")
    }

    @Test fun aTemplateCannotReadAnotherContractsInput() {
        val bad = DesignDef("bad", SessionContract, Droidcon, "Bad", "default",
            listOf(TemplateDef("in-app", "{}", 100, 100) { input(SpeakerContract.name) }))
        val failure = runCatching { capture(bad, bad.templates.single()) }.exceptionOrNull()
        assertTrue(failure?.message?.contains("not an input of confetti/session@1") == true, "$failure")
    }

    @Test fun everyTemplatePlays() {
        captured.forEach { t ->
            val c = t.design.contract
            val png = render(t, c.data(c.examples.getValue(t.design.example)))
            assertTrue(png.size > 1000, "${t.design.id}/${t.template.name} rendered ${png.size} bytes")
        }
    }
}
