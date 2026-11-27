package org.qinyan.rimoat.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings

class AppStringsResourceRuleTest {
    @Test
    fun packagedResourcesKeepNumberedArgumentsAndLocalePluralForms() {
        val english = PackagedStringResources("en")
        assertEquals("1 hr", english.quantity(R.plurals.control_hours, 1, 1))
        assertEquals("2 hrs", english.quantity(R.plurals.control_hours, 2, 2))
        assertEquals("1 hr 30 mins", english.get(R.string.control_hours_minutes, "1 hr", "30 mins"))
        val chinese = PackagedStringResources("zh-CN")
        assertEquals("1 小时", chinese.quantity(R.plurals.control_hours, 1, 1))
        assertEquals("2 小时", chinese.quantity(R.plurals.control_hours, 2, 2))
    }

    @Test
    fun ruleRestoresThePreviousResolverAfterFailure() {
        val sentinel = object : AppStrings.ResourceResolver {
            override fun get(id: Int, vararg args: Any) = "fixture"
            override fun quantity(id: Int, quantity: Int, vararg args: Any) = "fixture"
        }
        val previous = AppStrings.setResourceResolverForTests(sentinel)
        try {
            val wrapped = AppStringsResourceRule("en").apply(object : Statement() {
                override fun evaluate() {
                    assertEquals("Settings", AppStrings.get(R.string.settings_title))
                    throw IllegalArgumentException("Test failure")
                }
            }, Description.EMPTY)
            assertThrows(IllegalArgumentException::class.java) { wrapped.evaluate() }
            assertEquals("fixture", AppStrings.get(R.string.settings_title))
        } finally {
            AppStrings.setResourceResolverForTests(previous)
        }
    }
}
