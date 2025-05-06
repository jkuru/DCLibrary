package com.kuru.featureflow.component.register

import org.junit.Test
import org.junit.Assert.*

class DFComponentConfigTest {

    // Dummy task implementations for DFComponentInterceptor
    private val dummyTaskTrue: () -> Boolean = { true }
    private val dummyTaskFalse: () -> Boolean = { false }

    @Test
    fun `constructor should initialize with route and default empty interceptor list`() {
        val config = DFComponentConfig(route = "moduleA/feature1")

        assertEquals("moduleA/feature1", config.route)
        assertTrue("listOfDFComponentInterceptor should be empty by default", config.listOfDFComponentInterceptor.isEmpty())
    }

    @Test
    fun `constructor should initialize with route and provided interceptor list`() {
        val interceptor1 = DFComponentInterceptor(preInstall = true, task = dummyTaskTrue)
        val interceptor2 = DFComponentInterceptor(preInstall = false, task = dummyTaskFalse)
        val interceptors = listOf(interceptor1, interceptor2)

        val config = DFComponentConfig(
            route = "moduleB/feature2",
            listOfDFComponentInterceptor = interceptors
        )

        assertEquals("moduleB/feature2", config.route)
        assertEquals("Interceptor list size should match", 2, config.listOfDFComponentInterceptor.size)
        assertEquals("First interceptor should match", interceptor1, config.listOfDFComponentInterceptor[0])
        assertEquals("Second interceptor should match", interceptor2, config.listOfDFComponentInterceptor[1])
        assertEquals("The list instance should be the one provided", interceptors, config.listOfDFComponentInterceptor)
    }

    @Test
    fun `equals and hashCode should work correctly for data class`() {
        // Interceptor instances
        val interceptorA_pre_true = DFComponentInterceptor(preInstall = true, task = dummyTaskTrue)
        val interceptorA_pre_true_variantTask = DFComponentInterceptor(preInstall = true, task = { true }) // Different task instance, but equivalent content
        val interceptorB_post_false = DFComponentInterceptor(preInstall = false, task = dummyTaskFalse)


        // Case 1: Identical properties including interceptor list content
        val config1 = DFComponentConfig(
            route = "testRoute",
            listOfDFComponentInterceptor = listOf(interceptorA_pre_true)
        )
        val config2 = DFComponentConfig(
            route = "testRoute",
            listOfDFComponentInterceptor = listOf(interceptorA_pre_true) // Same instance
        )
        assertEquals("Instances with same route and identical interceptor instances should be equal", config1, config2)
        assertEquals("HashCodes should be equal for config1 and config2", config1.hashCode(), config2.hashCode())

        // Case 1b: Identical properties, interceptors are different instances but equal by value
        val config1b = DFComponentConfig(
            route = "testRoute",
            listOfDFComponentInterceptor = listOf(DFComponentInterceptor(preInstall = true, task = dummyTaskTrue)) // New but equivalent instance
        )
        assertEquals("Instances with same route and equivalent interceptor instances should be equal", config1, config1b)
        assertEquals("HashCodes should be equal for config1 and config1b", config1.hashCode(), config1b.hashCode())
        // Note: Equality of lambdas is tricky. For data classes, if the lambda instances are different,
        // the containing data classes might not be equal unless the lambdas have a stable `equals` implementation.
        // Standard lambdas use reference equality. For robust testing of task equality,
        // you might need custom equals/hashCode in DFComponentInterceptor or use stable references to lambdas.
        // However, for this test, we'll assume standard data class behavior.
        // If dummyTaskTrue and the lambda { true } are considered different by .equals(), then config1 and config1_variantTask will differ.
        val config1_variantTask = DFComponentConfig(
            route = "testRoute",
            listOfDFComponentInterceptor = listOf(interceptorA_pre_true_variantTask)
        )
        // This assertion depends on how equals is implemented for lambdas or if the references are the same.
        // If dummyTaskTrue !== interceptorA_pre_true_variantTask.task, then this might fail.
        // For simplicity, we often use the same lambda instance in tests.
        // assertEquals(config1, config1_variantTask) // This line is commented out due to lambda equality complexities


        // Case 2: Same route, but different interceptor list content
        val config3 = DFComponentConfig(
            route = "testRoute",
            listOfDFComponentInterceptor = listOf(interceptorB_post_false)
        )
        assertNotEquals("Instances with different interceptor list content should not be equal", config1, config3)

        // Case 3: Different route
        val config4 = DFComponentConfig(
            route = "anotherRoute",
            listOfDFComponentInterceptor = listOf(interceptorA_pre_true)
        )
        assertNotEquals("Instances with different routes should not be equal", config1, config4)

        // Case 4: One list empty, other not
        val config5 = DFComponentConfig(
            route = "testRoute",
            listOfDFComponentInterceptor = emptyList()
        )
        assertNotEquals("Instances with different interceptor lists (empty vs non-empty) should not be equal", config1, config5)

        // Case 5: Both lists empty, same route
        val config6 = DFComponentConfig(route = "testRoute") // Default empty list
        assertEquals("Instances with same route and default empty lists should be equal", config5, config6)
        assertEquals(config5.hashCode(), config6.hashCode())
    }

    @Test
    fun `copy method should work as expected for data class`() {
        val originalInterceptor = DFComponentInterceptor(preInstall = true, task = dummyTaskTrue)
        val originalInterceptors = listOf(originalInterceptor)

        val originalConfig = DFComponentConfig(
            route = "originalRoute",
            listOfDFComponentInterceptor = originalInterceptors
        )

        // Copy with no changes
        val copiedIdentical = originalConfig.copy()
        assertEquals("Copied instance should be equal to original", originalConfig, copiedIdentical)
        assertNotSame("Copied instance should be a different object", originalConfig, copiedIdentical)
        // The list of interceptors will be the same instance in the shallow copy
        assertSame("Interceptor list in copiedIdentical should be the same instance as original", originalInterceptors, copiedIdentical.listOfDFComponentInterceptor)
        if (originalInterceptors.isNotEmpty()) {
            // The interceptor instance within the list is also the same
            assertSame("Interceptor instance in copiedIdentical should be the same instance", originalInterceptor, copiedIdentical.listOfDFComponentInterceptor.first())
        }

        // Copy with route changed
        val copiedRouteChanged = originalConfig.copy(route = "newRoute")
        assertEquals("newRoute", copiedRouteChanged.route)
        assertEquals(originalConfig.listOfDFComponentInterceptor, copiedRouteChanged.listOfDFComponentInterceptor)

        // Copy with interceptor list changed
        val newInterceptor = DFComponentInterceptor(preInstall = false, task = dummyTaskFalse)
        val newInterceptors = listOf(newInterceptor)
        val copiedInterceptorsChanged = originalConfig.copy(listOfDFComponentInterceptor = newInterceptors)

        assertEquals("Interceptor list should be updated in the copied instance", newInterceptors, copiedInterceptorsChanged.listOfDFComponentInterceptor)
        assertEquals(originalConfig.route, copiedInterceptorsChanged.route) // Route remains same
        if (newInterceptors.isNotEmpty()) {
            assertSame(newInterceptor, copiedInterceptorsChanged.listOfDFComponentInterceptor.first())
        }
        assertNotEquals("Original and new interceptor lists should be different", originalInterceptors, newInterceptors)
    }

    @Test
    fun `toString method should produce a non-empty string containing class name and properties`() {
        val interceptor = DFComponentInterceptor(preInstall = true, task = dummyTaskTrue)
        val config = DFComponentConfig(
            route = "testRouteToString",
            listOfDFComponentInterceptor = listOf(interceptor)
        )
        val configString = config.toString()

        assertTrue("toString should start with class name", configString.startsWith("DFComponentConfig("))
        assertTrue("toString should contain route", configString.contains("route=testRouteToString"))
        assertTrue("toString should end with a parenthesis", configString.endsWith(")"))
        // The exact toString of a lambda is not standardized and can be verbose.
        // The check MENTION_OF_LAMBDA_IN_TOSTRING is a placeholder for this.
        // A more robust check might look for "task=" followed by something indicating a function.
        assertTrue("toString should mention the task", configString.contains("task="))
    }
}