package org.qinyan.rimoat.testing

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.rules.ExternalResource
import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.w3c.dom.Element

/** Plain JVM tests resolve the same XML strings that Android packages for the app. */
class AppStringsResourceRule(private val languageTag: String = "zh-CN") : ExternalResource() {
    private var previous: AppStrings.ResourceResolver? = null

    private companion object {
        val resources = mutableMapOf<String, PackagedStringResources>()
    }

    override fun before() {
        val resolver = synchronized(resources) {
            resources.getOrPut(languageTag) { PackagedStringResources(languageTag) }
        }
        previous = AppStrings.setResourceResolverForTests(resolver)
    }

    override fun after() {
        AppStrings.setResourceResolverForTests(previous)
    }
}

internal class PackagedStringResources(languageTag: String) : AppStrings.ResourceResolver {
    private val locale = Locale.forLanguageTag(languageTag)
    private val strings = mutableMapOf<String, String>()
    private val plurals = mutableMapOf<String, Map<String, String>>()
    private val stringNames = R.string::class.java.fields.associate { it.getInt(null) to it.name }
    private val pluralNames = R.plurals::class.java.fields.associate { it.getInt(null) to it.name }

    init {
        val resources = listOf(File("src/main/res"), File("app/src/main/res"))
            .firstOrNull { it.isDirectory } ?: error("Packaged Android resources were not found")
        val directory = when (languageTag) {
            "zh", "zh-CN" -> "values-zh"
            "zh-Hant" -> "values-b+zh+Hant"
            "ja" -> "values-ja"
            "en" -> "values-en"
            else -> error("Unsupported fixture language: $languageTag")
        }
        load(File(resources, "values"))
        load(File(resources, directory))
    }

    private fun load(directory: File) {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        directory.listFiles { file -> file.extension == "xml" }.orEmpty().forEach { file ->
            val children = factory.newDocumentBuilder().parse(file).documentElement.childNodes
            for (index in 0 until children.length) {
                val element = children.item(index) as? Element ?: continue
                val name = element.getAttribute("name")
                when (element.tagName) {
                    "string" -> strings[name] = decode(element.textContent)
                    "plurals" -> {
                        val items = element.getElementsByTagName("item")
                        plurals[name] = (0 until items.length).associate { itemIndex ->
                            val item = items.item(itemIndex) as Element
                            item.getAttribute("quantity") to decode(item.textContent)
                        }
                    }
                }
            }
        }
    }

    override fun get(id: Int, vararg args: Any): String {
        val name = checkNotNull(stringNames[id]) { "Unknown string resource: $id" }
        val value = checkNotNull(strings[name]) { "Missing packaged string: $name" }
        return if (args.isEmpty()) value else String.format(locale, value, *args)
    }

    override fun quantity(id: Int, quantity: Int, vararg args: Any): String {
        val name = checkNotNull(pluralNames[id]) { "Unknown plural resource: $id" }
        val values = checkNotNull(plurals[name]) { "Missing packaged plurals: $name" }
        val form = if (locale.language == "en" && quantity == 1) "one" else "other"
        val value = checkNotNull(values[form] ?: values["other"]) { "Missing plural form: $name/$form" }
        return if (args.isEmpty()) value else String.format(locale, value, *args)
    }

    private fun decode(raw: String): String {
        val quoted = raw.startsWith('"') && raw.endsWith('"')
        val text = if (quoted) raw.substring(1, raw.length - 1) else raw.trim().replace(Regex("\\s+"), " ")
        return Regex("\\\\(u[0-9a-fA-F]{4}|.)").replace(text) { match ->
            when (val escaped = match.groupValues[1]) {
                "n" -> "\n"
                "t" -> "\t"
                else -> if (escaped.startsWith("u") && escaped.length == 5) {
                    escaped.substring(1).toInt(16).toChar().toString()
                } else escaped
            }
        }
    }
}
