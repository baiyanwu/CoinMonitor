package io.baiyanwu.coinmonitor.clipboard

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardWebsiteMetadataTest {
    @Test fun gigaxDeclaredPngPrecedesConventionalFavicon() {
        val html = """<link rel="icon" type="image/png" href="/assets/logo-CElvZlIR.png" />
            <link rel="apple-touch-icon" href="/assets/logo-CElvZlIR.png" />"""
        assertEquals(listOf("https://gigax.fun/assets/logo-CElvZlIR.png", "https://gigax.fun/favicon.ico"),
            ClipboardWebsiteMetadata.iconCandidates(html, "https://gigax.fun/"))
    }

    @Test fun relativeAndCdnIconsResolveAndUnsafeSchemesAreIgnored() {
        val html = """<LINK REL='shortcut ICON' href='icons/logo.png?a=1&amp;b=2'>
            <link rel='icon' href='//cdn.example.com/icon.png'>
            <link rel='icon' href='javascript:alert(1)'>
            <link rel='stylesheet' href='/style.css'>"""
        assertEquals(listOf("https://example.com/icons/logo.png?a=1&b=2", "https://cdn.example.com/icon.png", "https://example.com/favicon.ico"),
            ClipboardWebsiteMetadata.iconCandidates(html, "https://example.com/"))
    }

    @Test fun missingDeclarationFallsBackToRootFavicon() {
        assertEquals(listOf("https://example.com/favicon.ico"), ClipboardWebsiteMetadata.iconCandidates("", "https://example.com/"))
        assertEquals(emptyList<String>(), ClipboardWebsiteMetadata.iconCandidates("", "file:///tmp/site"))
    }
}
