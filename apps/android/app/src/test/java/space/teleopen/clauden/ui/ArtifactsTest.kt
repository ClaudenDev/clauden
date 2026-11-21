package space.teleopen.clauden.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactsTest {

    @Test
    fun legacyToolOutputFileIsHidden() {
        val text = """
            Ответ.

            <artifact identifier="file:" type="text/plain" title="Файл">
            {"returncode":0,"stdout":"platega.txt"}
            </artifact>
        """.trimIndent()
        assertEquals(0, parseArtifacts(text).size)
    }

    @Test
    fun realArtifactStillParsed() {
        val text = """
            Готово.

            <artifact identifier="file:main.py" type="text/x-python" title="main.py">
            print(1)
            </artifact>
        """.trimIndent()
        val artifacts = parseArtifacts(text)
        assertEquals(1, artifacts.size)
        assertEquals("main.py", artifacts[0].title)
        assertTrue(artifacts[0].content.contains("print(1)"))
    }
}
