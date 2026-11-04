package spa.app.results

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import org.json.JSONArray
import org.json.JSONObject
import spa.app.PoolUiState
import spa.app.data.TestFixtures
import spa.app.importing.ImportOutcome
import spa.app.importing.PoolSource
import spa.app.testing.TestImports
import spa.app.ui.theme.SpaTheme
import spa.core.baseline.Baseline
import spa.core.metrics.ColorPair

/**
 * Shared by the Results tests: pools imported through the app's import path, the baseline and the
 * goldens read from `fixtures/`, and helpers that read the semantics tree. Expected values are
 * always read from the files with org.json, independently of the app's model.
 */
object ResultsKit {
    /** The bundled baseline, decoded the way the app decodes it. */
    val baseline: Baseline by lazy { Baseline.decode(TestFixtures.text("baselines/trk.json")) }

    private val baselineJson by lazy { JSONObject(TestFixtures.text("baselines/trk.json")) }

    /** The baseline's `kits`, read with org.json. */
    val kits: Int
        get() = baselineJson.getInt("kits")

    /** `fixtures/golden/<file>.json`. */
    fun golden(file: String): JSONObject = JSONObject(TestFixtures.text("golden/$file.json"))

    /** The golden's metric value at [pointer] (under `/metrics`). */
    fun goldenValue(file: String, pointer: String): Double =
        (at(golden(file).getJSONObject("metrics"), pointer) as Number).toDouble()

    /** The value at JSON [pointer] below [root]. */
    fun at(root: JSONObject, pointer: String): Any {
        var node: Any = root
        pointer.removePrefix("/").split("/").forEach { segment ->
            node =
                when (val current = node) {
                    is JSONObject -> current.get(segment)
                    is JSONArray ->
                        if (segment == "count") current.length() else current.get(segment.toInt())
                    else -> error("$pointer: $segment is below a leaf")
                }
        }
        return node
    }

    /** The golden's `fixingSources` list, as (name, colours, count). */
    fun goldenFixing(file: String): List<Triple<String, List<String>, Int>> {
        val list = golden(file).getJSONObject("metrics").getJSONArray("fixingSources")
        return (0 until list.length()).map { at ->
            val source = list.getJSONObject(at)
            val colors = source.getJSONArray("colors")
            Triple(
                source.getString("name"),
                (0 until colors.length()).map { colors.getString(it) },
                source.getInt("count"),
            )
        }
    }

    /** The golden's unresolved lines, as (line, text, reason code). */
    fun goldenUnresolved(file: String): List<Triple<Int, String, String>> {
        val list = golden(file).getJSONObject("pool").getJSONArray("unresolved")
        return (0 until list.length()).map { at ->
            val line = list.getJSONObject(at)
            Triple(line.getInt("line"), line.getString("text"), line.getString("reason"))
        }
    }

    /** A golden kit warning. */
    data class Warning(
        val code: String,
        val min: Int,
        val max: Int,
        val actual: Int,
        val message: String,
    )

    fun goldenWarnings(file: String): List<Warning> {
        val list = golden(file).getJSONObject("pool").getJSONArray("kitWarnings")
        return (0 until list.length()).map { at ->
            val warning = list.getJSONObject(at)
            val expected = warning.getJSONObject("expected")
            Warning(
                warning.getString("code"),
                expected.getInt("min"),
                expected.getInt("max"),
                warning.getInt("actual"),
                warning.getString("message"),
            )
        }
    }

    /** The golden's ten pair codes sorted by cards, highest first, ties in WU ... RG order. */
    fun goldenPairOrder(file: String): List<String> =
        ColorPair.entries
            .map { it.name }
            .sortedByDescending { goldenValue(file, "/pairs/$it/cards") }

    /** One typical stat from the baseline, read with org.json, as whole numbers. */
    data class Typical(val median: Int, val p10: Int, val p90: Int)

    fun typical(pointer: String): Typical {
        val stat = baselineJson.getJSONObject("stats").getJSONObject(pointer)
        return Typical(
            stat.getDouble("median").toInt(),
            stat.getDouble("p10").toInt(),
            stat.getDouble("p90").toInt(),
        )
    }

    /** The baseline's stat at [pointer] as (median, p10, p90), exactly as stored. */
    fun typicalExact(pointer: String): Triple<Double, Double, Double> {
        val stat = baselineJson.getJSONObject("stats").getJSONObject(pointer)
        return Triple(stat.getDouble("median"), stat.getDouble("p10"), stat.getDouble("p90"))
    }

    /** The spec's typical line for [pointer]: "typical <m> (<p10>–<p90>)", or "typical <m>". */
    fun typicalText(pointer: String): String {
        val t = typical(pointer)
        return if (t.p10 == t.p90) "typical ${t.median}"
        else "typical ${t.median} (${t.p10}–${t.p90})"
    }

    /** "below typical", "above typical", or null, for [value] against the baseline at [pointer]. */
    fun outsideWords(pointer: String, value: Double): String? {
        val (_, p10, p90) = typicalExact(pointer)
        return when {
            value < p10 -> "below typical"
            value > p90 -> "above typical"
            else -> null
        }
    }

    /** [file] imported through the app's import path. */
    fun success(file: String): ImportOutcome.Success = TestImports.success(TestImports.pool(file))

    /** [success] as the Loaded state, from a file named [file] unless [source] says otherwise. */
    fun loaded(
        success: ImportOutcome.Success,
        source: PoolSource = PoolSource.Pasted,
    ): PoolUiState.Loaded = PoolUiState.Loaded(success, source, success.format)

    fun loaded(file: String): PoolUiState.Loaded = loaded(success(file), PoolSource.File(file))

    /** Results for [state] against the fixtures' baseline. */
    @Composable
    fun Screen(state: PoolUiState, onPair: (ColorPair) -> Unit = {}) {
        SpaTheme {
            CompositionLocalProvider(LocalBaseline provides baseline) {
                ResultsScreen(
                    state = state,
                    actions = ResultsActions(onNewPool = {}, onAbout = {}, onPair = onPair),
                )
            }
        }
    }

    /** Every text in [node] and below it, in tree order. */
    fun texts(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap(::texts)

    fun texts(interaction: SemanticsNodeInteraction): List<String> =
        texts(interaction.fetchSemanticsNode())

    /** The merged node's texts, as the screen reader gets them. */
    fun mergedTexts(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    /** Every node below [root] in traversal (tree) order, [root] first. */
    fun inOrder(root: SemanticsNode): List<SemanticsNode> =
        listOf(root) + root.children.flatMap(::inOrder)

    /** The text layout of a plain `Text` node, as the semantics action reports it. */
    fun layoutOf(node: SemanticsNode): TextLayoutResult? {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        return layouts.firstOrNull()
    }

    /**
     * The style a plain `Text`'s modifier element holds (the semantics action drops it; see
     * `A11yChecks`), or null for other text.
     */
    fun textStyleOf(node: SemanticsNode): TextStyle? {
        val element =
            node.layoutInfo
                .getModifierInfo()
                .map { it.modifier }
                .firstOrNull { it.javaClass.name == PLAIN_TEXT_ELEMENT } ?: return null
        val field = element.javaClass.declaredFields.first { it.name == "style" }
        field.isAccessible = true
        return field.get(element) as TextStyle
    }

    private const val PLAIN_TEXT_ELEMENT =
        "androidx.compose.foundation.text.modifiers.TextStringSimpleElement"

    /** The seven colour rows' names, in the spec's order. */
    val COLOR_NAMES = listOf("White", "Blue", "Black", "Red", "Green", "Multicolour", "Colourless")

    /** The pointers of Pool at a glance, in the spec's order. */
    val GLANCE_POINTERS =
        listOf(
            "/totalCards",
            "/nonlandCards",
            "/basicLands",
            "/nonbasicLands",
            "/removalTotal",
            "/interactionTotal",
            "/fixingSources/count",
        )

    /** Every pointer the ticket's background lists, with each pair and colour. */
    val ALL_POINTERS: Set<String> by lazy {
        val pairs = ColorPair.entries.flatMap { listOf("/pairs/$it/cards", "/pairs/$it/creatures") }
        val rarities = listOf("common", "uncommon", "rare", "mythic")
        val colors =
            listOf("W", "U", "B", "R", "G", "MULTI", "COLORLESS").flatMap { c ->
                listOf(
                    "/byColor/$c/cards/total",
                    "/byColor/$c/playables/total",
                    "/byColor/$c/creatures",
                    "/byColor/$c/removal",
                    "/byColor/$c/interaction",
                ) +
                    rarities.map { "/byColor/$c/cards/$it" } +
                    rarities.map { "/byColor/$c/playables/$it" }
            }
        (GLANCE_POINTERS + pairs + colors).toSet()
    }

    const val TIMEOUT = 20_000L
}
