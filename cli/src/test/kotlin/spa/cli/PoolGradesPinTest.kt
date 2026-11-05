package spa.cli

import java.security.MessageDigest
import kotlin.test.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * SPA-043 criterion 1: `spa pool-grades` prints byte for byte what it printed before the parts
 * became pluggable. Each SHA-256 is of the stdout of the installed `spa` at the branch's start
 * (`8444b06`), line endings as LF, on `--set trk --data fixtures`.
 */
class PoolGradesPinTest {
    @ParameterizedTest(name = "trk-manabox-{0} weights {1} pair {2}")
    @CsvSource(
        "01, default, all, 0ac16131b0bb13a623969de6932271e0f0cc9f4b65322192ed3bea56db22648d",
        "01, default, WU, b3261f26885574f4292a0b1433e584697b57a65b204f57b32786c3851147fc7a",
        "01, tuned, all, 0280f2fff66ac3590eb554ae779c80a6f7fa4a81045dc734fc50ec031ceb1a27",
        "01, tuned, WU, 3bc10597a885d59e5d9565cd66cda343dd952bdfc095b3d1e52bd8b54a359eae",
        "02, default, all, a2d4e3d3c728c2e2c8bc231994235c5e0a8e591f81dd07e78ffcfbc149bff91c",
        "02, default, WU, 6be1e1cf15ba0c1be9b4525c44da722caf6baaad7866e87d1f3b76d79b06a8cd",
        "02, tuned, all, 70fe1ddac9272cb227073f86d1bf9bce05de95d1fdf501f73d8b815a5d524305",
        "02, tuned, WU, 85ac8b534c0b633674ad18be99f2f4940ca757e293f6a4866f0c00dca5dcc3cf",
        "03, default, all, 52b4939fc041963052edf728f09fca0ae90075fa7c861f8c05fb69be16900d59",
        "03, default, WU, 8aaed211ed0a3f7f025c40e8d401f8e0a51f86c60456c746bd198c3b954fb588",
        "03, tuned, all, c139195412ef2c5d2bd5a58370ba37568754a963291242d0a07bf3f2e025f143",
        "03, tuned, WU, 7a498bd0b8752c6cef366bed082eb4490dd410f9f152c8f8b3c7b0b7433d0393",
        "04, default, all, a6417c64dbf559850ada2c9439e50b99277e8d679d4bc755724e4a67acc0da38",
        "04, default, WU, 2b9b89c45eba39d9d03fb3ee29d6fe73b43d1ea86de6332deb095814020159fa",
        "04, tuned, all, d2bd54aba772dc71c02bd14a295ffbe7351f708a0221b1e937fc03de13db3f7c",
        "04, tuned, WU, 2b217a8c3866d05e6a3bf6da503be51c3e0a0beab92326396b620c7217b00299",
        "05, default, all, eef41d987d2ee002e54ec384592a817d8ca9c7be5ae10ccde967d8ab5675688f",
        "05, default, WU, d3ade0abde4821a392834bd10bbc62fb94d6c54909dcd2280fe88373622bb47b",
        "05, tuned, all, 32722c59a1d0302fb7b17e7852170b373685c784c0217870889d9441150375f4",
        "05, tuned, WU, 94b5c36ae5617c5500a914c9a3fe32109504d6d66935c00625b82a9eb4d7c640",
    )
    fun `pool-grades output is unchanged`(
        pool: String,
        weights: String,
        pair: String,
        sha: String,
    ) {
        val args = buildList {
            add("pool-grades")
            add(Fixtures.pool("trk-manabox-$pool.csv"))
            addAll(listOf("--set", "trk", "--data", Fixtures.data))
            if (weights == "tuned") {
                addAll(listOf("--weights", Fixtures.root.resolve("weights/tuned.json").toString()))
            }
            if (pair != "all") addAll(listOf("--pair", pair))
        }
        val run = runCli(*args.toTypedArray())

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals(sha, sha256(run.stdout.replace("\r\n", "\n")))
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString(
            ""
        ) {
            "%02x".format(it)
        }
}
