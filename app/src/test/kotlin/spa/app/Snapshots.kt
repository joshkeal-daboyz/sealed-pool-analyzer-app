package spa.app

import com.github.takahirom.roborazzi.RoborazziOptions

/** Where snapshot baselines live and how a capture is compared with its baseline. */
object Snapshots {
    /** Baselines are committed here, one PNG per snapshot name. */
    const val DIR = "src/test/snapshots"

    /**
     * The share of pixels that may differ before a snapshot counts as changed: 0.05 %. Rendering is
     * pinned (Robolectric native graphics, API 36, `w411dp-h914dp-xxhdpi`, the fonts bundled with
     * Robolectric rather than the host's), so Windows and Linux should agree to within
     * anti-aliasing noise. A one-letter text change on `shell_placeholder` alters 0.18 % of its
     * pixels, more than three times this tolerance.
     */
    const val CHANGE_THRESHOLD = 0.0005F

    fun path(name: String): String = "$DIR/$name.png"

    val options =
        RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(changeThreshold = CHANGE_THRESHOLD)
        )
}
