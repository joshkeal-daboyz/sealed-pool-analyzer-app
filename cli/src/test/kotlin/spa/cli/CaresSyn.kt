package spa.cli

import java.nio.file.Path

/**
 * The hand-built `syn` inputs the cares tests share (SPA-055): a data directory with set `syn`'s
 * Scryfall cache and brief, an otags file, its tags file and a cares table. Names tags
 * (`name-matters`, its child `tutors-by-name` and its grandchild `tutors-exact`) are left out of
 * the packet's vocabulary; `named-things` only looks like one.
 *
 * | Card              | Otags                                      | Cares from the table |
 * |-------------------|--------------------------------------------|----------------------|
 * | syn/1 Grizzly     | cantrip, card-advantage, draw              | none                 |
 * | syn/2 Serra Angel | legends-matter                             | `otag:draw` 2        |
 * | syn/3 Air Elem.   | card-advantage, draw                       | none                 |
 * | syn/4 Counterspel | name-matters, tutors-by-name, tutors-exact | `creature` 1         |
 * | syn/5 Doom Blade  | named-things                               | none                 |
 */
internal object CaresSyn {
    const val CODE = "syn"

    val otags =
        """
        {
          "set": "syn",
          "source": "hand-built",
          "updatedAt": "2026-11-05T22:00:00Z",
          "attribution": "Card tags from Scryfall Tagger (tagger.scryfall.com)",
          "cards": [
            { "id": "syn/1", "otags": ["cantrip", "card-advantage", "draw"] },
            { "id": "syn/2", "otags": ["legends-matter"] },
            { "id": "syn/3", "otags": ["card-advantage", "draw"] },
            { "id": "syn/4", "otags": ["name-matters", "tutors-by-name", "tutors-exact"] },
            { "id": "syn/5", "otags": ["named-things"] }
          ]
        }
        """
            .trimIndent()

    val tags =
        """
        {
          "set": "syn",
          "source": "hand-built",
          "updatedAt": "2026-11-05T22:00:00Z",
          "attribution": "Card tags from Scryfall Tagger (tagger.scryfall.com)",
          "tags": [
            { "slug": "cantrip", "description": null, "parents": ["draw"] },
            { "slug": "card-advantage", "description": "Gains cards.", "parents": [] },
            { "slug": "draw", "description": "Draws cards.", "parents": ["card-advantage"] },
            { "slug": "legends-matter", "description": "Cares about legends.", "parents": [] },
            { "slug": "name-matters", "description": "Cares about names.", "parents": [] },
            { "slug": "named-things", "description": "Has a name.", "parents": [] },
            { "slug": "tutors-by-name", "description": "Tutors by name.", "parents": ["name-matters"] },
            { "slug": "tutors-exact", "description": "Tutors by exact name.", "parents": ["tutors-by-name"] }
          ]
        }
        """
            .trimIndent()

    val cares =
        """
        { "cares": {
          "legends-matter": [{ "fact": "otag:draw", "strength": 2 }],
          "tutors-by-name": [{ "fact": "creature", "strength": 1 }]
        } }
        """
            .trimIndent()

    /** The names of the name tags in [tags]. */
    val nameTags = listOf("name-matters", "tutors-by-name", "tutors-exact")

    /** Writes the otags file, tags file and cares table into the `syn` data directory [data]. */
    fun addFiles(data: Path) {
        writeFile(data, "otags/syn.json", otags)
        writeFile(data, "otags/syn.tags.json", tags)
        writeFile(data, "otags/cares.json", cares)
    }

    /** A data directory under [dir] for `syn` with the otags file, tags file and cares table. */
    fun data(dir: Path): Path = Fixtures.synDataDir(dir).also(::addFiles)
}
