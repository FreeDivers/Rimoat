package org.qinyan.rimoat

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.qinyan.rimoat.data.OpenSourceLicenseCatalog

class OpenSourceLicensesTest {
    private fun catalog() = Json { ignoreUnknownKeys = true }.decodeFromString<OpenSourceLicenseCatalog>(
        File("src/main/assets/open_source_licenses.json").readText()
    )

    @Test fun everyLibraryHasUniqueIdentityAndReadableBundledTerms() {
        val catalog = catalog()
        assertTrue(catalog.libraries.isNotEmpty())
        assertEquals(catalog.libraries.size, catalog.libraries.map { it.id }.toSet().size)
        catalog.libraries.forEach { library ->
            assertTrue(library.name.isNotBlank())
            assertTrue(library.version.isNotBlank())
            assertTrue(library.documents.isNotEmpty())
            library.documents.forEach { id ->
                assertTrue("Missing terms for ${library.id}", catalog.documents[id].orEmpty().length > 500)
            }
        }
    }

    @Test fun usbNoticePreservesCopyrightAndDebugToolsAreMarked() {
        val catalog = catalog()
        val usb = catalog.libraries.single { it.id.startsWith("com.github.mik3y:usb-serial-for-android:") }
        assertEquals("MIT", usb.license)
        assertTrue(usb.documents.any { catalog.documents.getValue(it).contains("Copyright (c) 2013 Mike Wakerly") })
        assertTrue(catalog.libraries.single { it.id.startsWith("androidx.compose.ui:ui-tooling-android:") }.debugOnly)
        assertFalse(catalog.libraries.single { it.id.startsWith("androidx.glance:glance-appwidget:") }.debugOnly)
    }
}
