package com.kuru.featureflow.component.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DFProcessUriResultTest {

    @Test
    fun `FeatureRoute holds correct name and params`() {
        // Arrange
        val expectedName = "myFeature"
        val expectedParams = listOf("param1=value1", "param2=value2")

        // Act
        val result = DFProcessUriResult.FeatureRoute(expectedName, expectedParams)

        // Assert
        assertEquals(expectedName, result.name)
        assertEquals(expectedParams, result.params)
    }

    @Test
    fun `FeatureRoute can be created with empty params`() {
        // Arrange
        val expectedName = "anotherFeature"
        val expectedParams = emptyList<String>()

        // Act
        val result = DFProcessUriResult.FeatureRoute(expectedName, expectedParams)

        // Assert
        assertEquals(expectedName, result.name)
        assertTrue(result.params.isEmpty())
    }

    @Test
    fun `NavigationRoute holds correct key and params`() {
        // Arrange
        val expectedKey = "myNavigationKey"
        val expectedParams = listOf("id=123")

        // Act
        val result = DFProcessUriResult.NavigationRoute(expectedKey, expectedParams)

        // Assert
        assertEquals(expectedKey, result.key)
        assertEquals(expectedParams, result.params)
    }

    @Test
    fun `NavigationRoute can be created with empty params`() {
        // Arrange
        val expectedKey = "settingsNavigation"
        val expectedParams = emptyList<String>()

        // Act
        val result = DFProcessUriResult.NavigationRoute(expectedKey, expectedParams)

        // Assert
        assertEquals(expectedKey, result.key)
        assertTrue(result.params.isEmpty())
    }

    @Test
    fun `InvalidUri holds correct reason`() {
        // Arrange
        val expectedReason = "URI parsing failed due to invalid characters."

        // Act
        val result = DFProcessUriResult.InvalidUri(expectedReason)

        // Assert
        assertEquals(expectedReason, result.reason)
    }
}
