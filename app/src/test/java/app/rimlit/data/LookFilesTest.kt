package app.rimlit.data

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** [freshShareFile]: every share clears what an earlier one left, and never reuses its name. */
class LookFilesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun aFreshFileIsReadyToWriteTo() {
        val dir = File(tmp.root, "shared")
        val file = freshShareFile(dir)
        assertEquals(LOOK_FILE_NAME, file.name)
        assertTrue(file.parentFile!!.isDirectory)
        assertFalse(file.exists(), "not written yet")
    }

    @Test
    fun eachShareGetsItsOwnNameAndClearsWhatCameBefore() {
        val dir = File(tmp.root, "shared")
        val first = freshShareFile(dir).apply { writeText("one") }
        val second = freshShareFile(dir)
        assertNotEquals(first.parentFile, second.parentFile, "never the same file a grant might still point at")
        assertFalse(first.exists(), "the earlier share's content is gone")
        assertFalse(first.parentFile!!.exists())
    }

    @Test
    fun anythingElseUnderTheDirectoryIsClearedToo() {
        val dir = File(tmp.root, "shared").apply { mkdirs() }
        File(dir, "leftover.txt").writeText("stale")
        val file = freshShareFile(dir)
        assertEquals(listOf(file.parentFile!!.name), dir.list()?.toList())
    }
}
