package spa.core.sim

import java.nio.file.Path
import spa.core.brief.BriefLoader
import spa.core.brief.KitTemplate
import spa.core.brief.briefFixturesDir
import spa.core.brief.synBriefPath
import spa.core.cards.CardIndex
import spa.core.cards.fixturesDir

/** The committed `fixtures/pools` directory, passed in by the Gradle test task. */
val poolFixturesDir: Path =
    Path.of(
        requireNotNull(System.getProperty("spa.pool.fixtures")) {
            "Run through Gradle: the test task sets spa.pool.fixtures."
        }
    )

/** `fixtures/briefs/syn.json`'s kit template. */
val synKit: KitTemplate by lazy { BriefLoader.load(synBriefPath).kitTemplate }

/** `fixtures/briefs/trk.json`'s kit template. */
val trkKit: KitTemplate by lazy {
    BriefLoader.load(briefFixturesDir.resolve("trk.json")).kitTemplate
}

/** The `trk` index with its companion `sds`. */
val trkIndex: CardIndex by lazy { CardIndex.load(fixturesDir, "trk") }
