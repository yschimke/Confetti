package dev.johnoreilly.confetti.livery

import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.RemoteDensityBehavior
import androidx.compose.remote.creation.compose.capture.captureCommonRemoteDocument
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.compose.RcImageLoader
import ee.schimke.livery.client.BindResult
import ee.schimke.livery.client.Bindings
import ee.schimke.livery.client.ContractBinder
import ee.schimke.livery.protocol.Contract
import ee.schimke.livery.protocol.NamedValue
import ee.schimke.livery.render.RenderSpec
import ee.schimke.livery.render.TemplateRenderer
import ee.schimke.livery.protocol.PlacementRule
import ee.schimke.livery.protocol.ThemePack
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File

@OptIn(ExperimentalSerializationApi::class)
private val json = Json { prettyPrint = true; prettyPrintIndent = "  "; ignoreUnknownKeys = true }

class CapturedTemplate(val design: DesignDef, val template: TemplateDef, val bytes: ByteArray, val reads: Set<String>, val emits: Set<String>)

/** Captures one template to Remote Compose bytes on the JVM, recording what it read and emitted. */
fun capture(design: DesignDef, template: TemplateDef): CapturedTemplate = runBlocking {
    val scope = TemplateScope(design.contract, design.contract.examples.getValue(design.example))
    val bytes = captureCommonRemoteDocument(
        creationDisplayInfo = RemoteCreationDisplayInfo(
            width = template.widthDp, height = template.heightDp, densityDpi = 160,
            densityBehavior = RemoteDensityBehavior.Dp,
        ),
    ) {
        CompositionLocalProvider(LocalTemplateScope provides scope) { template.content() }
    }
    CapturedTemplate(design, template, bytes, scope.reads, scope.emits)
}

/** The contract as Livery reads it, so binding here is exactly what the device does. */
fun ConfettiContract.toLivery(): Contract = json.decodeFromJsonElement(toJson())

fun bind(contract: ConfettiContract, data: JsonObject): Bindings =
    when (val r = ContractBinder.bind(contract.toLivery(), data)) {
        is BindResult.Ok -> r.bindings
        is BindResult.Invalid -> error("${contract.ref}: ${r.problems.joinToString { "${it.name}: ${it.message}" }}")
    }

/** Renders a captured template with its design's example data (and a theme pack's values) to PNG. */
fun render(captured: CapturedTemplate, data: JsonObject, theme: Bindings = Bindings.EMPTY): ByteArray =
    TemplateRenderer.png(
        template = captured.bytes,
        bindings = bind(captured.design.contract, data) + theme,
        spec = RenderSpec(captured.template.widthDp, captured.template.heightDp, density = 2f, dark = true),
        images = PreviewImages,
    )

/** Every image a preview asks for is the same placeholder avatar: renders never touch the network. */
private object PreviewImages : RcImageLoader {
    private val avatar by lazy {
        val bitmap = ImageBitmap(160, 160)
        Canvas(bitmap).apply {
            drawRect(0f, 0f, 160f, 160f, Paint().apply { color = Color(0xFF5B6B7F) })
            drawCircle(Offset(80f, 62f), 30f, Paint().apply { color = Color(0xFFE8EDF3) })
            drawCircle(Offset(80f, 160f), 58f, Paint().apply { color = Color(0xFFE8EDF3) })
        }
        bitmap
    }
    override suspend fun load(data: RcBitmapData): ImageBitmap = avatar
}

/** The contract checks Livery's publisher doesn't do because it can't see Kotlin. */
fun checkContracts(captured: List<CapturedTemplate>, catalog: File): List<String> {
    val problems = mutableListOf<String>()
    // Every example binds through the same gate the device uses.
    AllContracts.forEach { c -> c.examples.forEach { (name, data) -> runCatching { bind(c, c.data(data)) }.onFailure { problems += "example $name: ${it.message}" } } }
    // Every design emits every action of its contract somewhere, and reads every required input.
    captured.groupBy { it.design }.forEach { (design, templates) ->
        val emitted = templates.flatMap { it.emits }.toSet()
        design.contract.actions.keys.filterNot { it in emitted }.forEach { problems += "${design.id}: never emits ${design.contract.ref} action '$it'" }
        templates.filter { it.template.name == "in-app" }.forEach { t ->
            design.contract.inputs.values.filter { it.required && it.name !in t.reads }
                .forEach { problems += "${design.id}/in-app: doesn't show required input '${it.name}'" }
        }
    }
    // Theme packs only set theme roles every contract declares.
    val roles = AllContracts.map { c -> c.inputs.values.filter { it.role != null }.map { it.name }.toSet() }.reduce { a, b -> a intersect b }
    catalog.resolve("themes").listFiles { f -> f.extension == "json" }.orEmpty().forEach { f ->
        val pack = json.decodeFromString<ThemePack>(f.readText())
        pack.values.keys.filterNot { it in roles }.forEach { problems += "themes/${f.name}: '$it' is not a theme role" }
    }
    // Placements name a design that exists and carry data its contract accepts.
    catalog.resolve("placements").listFiles { f -> f.extension == "json" }.orEmpty().forEach { f ->
        val rule = json.decodeFromString<PlacementRule>(f.readText())
        val design = AllDesigns.find { it.id == rule.template.designId }
        if (design == null) problems += "placements/${f.name}: no design ${rule.template.designId}"
        else runCatching { bind(design.contract, rule.data) }.onFailure { problems += "placements/${f.name}: ${it.message}" }
    }
    return problems
}

private fun JsonElement.pretty() = json.encodeToString(JsonElement.serializer(), this) + "\n"

/**
 * Writes the generated half of the catalog source tree: contracts/ from Contracts.kt, and per design
 * design.json, <template>.rc and <template>.json. themes/ and placements/ are hand-edited data
 * (what an admin's agent changes most) and are only checked. Renders go to [renders].
 */
fun main(args: Array<String>) {
    val catalog = File(args.getOrElse(0) { "livery-catalog/catalog" })
    val renders = args.getOrNull(1)?.let(::File)

    catalog.resolve("contracts").apply { mkdirs() }.let { dir ->
        AllContracts.forEach { dir.resolve("${it.fileName}.json").writeText(it.toJson().pretty()) }
    }

    val captured = AllDesigns.flatMap { d -> d.templates.map { capture(d, it) } }
    val problems = checkContracts(captured, catalog)
    if (problems.isNotEmpty()) error("Contract problems:\n  " + problems.joinToString("\n  "))

    val designsDir = catalog.resolve("designs")
    designsDir.deleteRecursively()
    captured.groupBy { it.design }.forEach { (design, templates) ->
        val dir = designsDir.resolve(design.id).apply { mkdirs() }
        dir.resolve("design.json").writeText(buildJsonObject {
            put("id", design.id); put("contract", design.contract.ref); put("title", design.title)
            put("author", "confetti"); put("variant", design.variant); put("install", design.install)
            put("tags", buildJsonObject { put("series", design.brand.series) })
        }.pretty())
        templates.forEach { t ->
            dir.resolve("${t.template.name}.rc").writeBytes(t.bytes)
            dir.resolve("${t.template.name}.json").writeText(buildJsonObject {
                put("target", json.parseToJsonElement(t.template.target))
                put("reads", buildJsonArray { t.reads.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
                put("emits", buildJsonArray { t.emits.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            }.pretty())
            println("${design.id}/${t.template.name}.rc  ${t.bytes.size} bytes  reads=${t.reads.size} emits=${t.emits}")
        }
    }

    renders?.let { out ->
        out.mkdirs()
        captured.forEach { t ->
            val c = t.design.contract
            out.resolve("${t.design.id}-${t.template.name}.png").writeBytes(render(t, c.data(c.examples.getValue(t.design.example))))
            c.examples["long"]?.takeIf { t.template.name == "in-app" }?.let { long ->
                out.resolve("${t.design.id}-${t.template.name}-long.png").writeBytes(render(t, c.data(long)))
            }
        }
        // What an admin's data-only change looks like: the same droidcon documents, recoloured by
        // a different theme pack's values (here a venue blue for droidcon Berlin), no new capture.
        val berlin = Bindings(mapOf(
            "theme.primary" to NamedValue.Color(0xFF4FC3F7), "theme.secondary" to NamedValue.Color(0xFFFFD54F),
            "theme.tertiary" to NamedValue.Color(0xFF7E57C2), "theme.onPrimary" to NamedValue.Color(0xFF0B1A2A),
        ))
        captured.filter { it.design.brand == Droidcon && it.template.name == "in-app" && it.design.contract != PromoContract }.forEach { t ->
            val c = t.design.contract
            out.resolve("${t.design.id}-in-app-berlin-theme.png").writeBytes(render(t, c.data(c.examples.getValue(t.design.example)), berlin))
        }
        println("renders: ${out.absolutePath}")
    }
}
