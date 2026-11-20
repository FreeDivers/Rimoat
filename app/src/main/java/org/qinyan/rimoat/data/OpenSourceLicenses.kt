package org.qinyan.rimoat.data

import kotlinx.serialization.Serializable

@Serializable
data class OpenSourceLibrary(
    val id: String,
    val name: String,
    val version: String,
    val license: String,
    val debugOnly: Boolean = false,
    val documents: List<String>
)

@Serializable
data class OpenSourceLicenseCatalog(
    val libraries: List<OpenSourceLibrary>,
    val documents: Map<String, String>
)
