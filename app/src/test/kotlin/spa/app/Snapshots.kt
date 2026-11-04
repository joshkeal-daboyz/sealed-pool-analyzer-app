package spa.app

import com.github.takahirom.roborazzi.RoborazziOptions

/** Where snapshot baselines live and how a capture is compared with its baseline. */
object Snapshots {
    /** Baselines are committed here, one PNG per snapshot name. */
    const val DIR = "src/test/snapshots"

    fun path(name: String): String = "$DIR/$name.png"

    val options = RoborazziOptions()
}
