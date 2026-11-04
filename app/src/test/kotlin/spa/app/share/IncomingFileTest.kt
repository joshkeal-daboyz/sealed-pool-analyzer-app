package spa.app.share

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import spa.app.IncomingFile
import spa.app.incomingFile

/** Criteria 2 and 4: which file a share or an "Open with" hands the app. */
@RunWith(AndroidJUnit4::class)
class IncomingFileTest {
    private val uri = Uri.parse("content://spa.app.test.shared/doc/trk-manabox-01.csv")

    @Test
    fun aShareGivesItsStream() {
        assertEquals(IncomingFile(uri), incomingFile(Shares.send(uri)))
    }

    @Test
    fun aShareWhoseFileIsOnlyInItsClipDataGivesThat() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/csv")
        intent.clipData = ClipData.newRawUri("pool", uri)
        assertEquals(IncomingFile(uri), incomingFile(intent))
    }

    @Test
    fun anOpenWithGivesItsData() {
        assertEquals(IncomingFile(uri), incomingFile(Shares.view(uri)))
    }

    @Test
    fun aShareOrOpenWithWithNoFileGivesNoUri() {
        assertEquals(IncomingFile(null), incomingFile(Shares.send(null)))
        assertEquals(IncomingFile(null), incomingFile(Shares.view(null)))
    }

    @Test
    fun aShareOfTextAloneGivesNoUri() {
        // Shared plain text is out of scope: a share whose only content is EXTRA_TEXT has no file.
        val intent =
            Intent(Intent.ACTION_SEND)
                .setType("text/csv")
                .putExtra(Intent.EXTRA_TEXT, "Name,Set code,Quantity\nShock,trk,1")
        assertEquals(IncomingFile(null), incomingFile(intent))
    }

    @Test
    fun anyOtherIntentIsNotAnIncomingFile() {
        assertNull(incomingFile(null))
        assertNull(incomingFile(Shares.launcher()))
        assertNull(incomingFile(Intent(Intent.ACTION_EDIT, uri)))
    }
}
