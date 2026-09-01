package io.baiyanwu.coinmonitor.ui.search

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchLazyRenderingPolicyTest {
    @Test
    fun `search results are emitted as individual lazy column items`() {
        val source = String(Files.readAllBytes(locateSearchRoute()))

        assertTrue(source.contains("itemsIndexed("))
        assertTrue(source.contains("items = results"))
        assertFalse(source.contains("results.forEach"))
    }

    private fun locateSearchRoute(): Path {
        val relative = "app/src/main/java/io/baiyanwu/coinmonitor/ui/search/SearchRoute.kt"
        val moduleRelative = "src/main/java/io/baiyanwu/coinmonitor/ui/search/SearchRoute.kt"
        val cwd = Paths.get("").toAbsolutePath()
        return listOf(
            cwd.resolve(relative),
            cwd.resolve(moduleRelative),
            cwd.parent?.resolve(relative)
        ).filterNotNull().firstOrNull(Files::exists)
            ?: error("Unable to locate SearchRoute.kt from $cwd")
    }
}
