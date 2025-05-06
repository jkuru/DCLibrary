package com.kuru.featureflow.component.register

import org.junit.Assert.*
import org.junit.Test

class DFComponentInterceptorTest {

    @Test
    fun `constructor should correctly initialize properties`() {
        val taskLambda: () -> Boolean = { true }
        val interceptor = DFComponentInterceptor(preInstall = true, task = taskLambda)

        assertTrue("preInstall should be true", interceptor.preInstall)
        assertSame("task lambda should be the same instance", taskLambda, interceptor.task)
    }

    @Test
    fun `constructor should correctly initialize with preInstall false`() {
        val taskLambda: () -> Boolean = { false }
        val interceptor = DFComponentInterceptor(preInstall = false, task = taskLambda)

        assertFalse("preInstall should be false", interceptor.preInstall)
        assertSame("task lambda should be the same instance", taskLambda, interceptor.task)
    }

    @Test
    fun `task lambda should be executable and return its defined value - true`() {
        var taskExecuted = false
        val taskLambda: () -> Boolean = {
            taskExecuted = true
            true
        }
        val interceptor = DFComponentInterceptor(preInstall = true, task = taskLambda)

        val result = interceptor.task()

        assertTrue("Task should have been executed", taskExecuted)
        assertTrue("Task result should be true", result)
    }

    @Test
    fun `task lambda should be executable and return its defined value - false`() {
        var taskExecuted = false
        val taskLambda: () -> Boolean = {
            taskExecuted = true
            false
        }
        val interceptor = DFComponentInterceptor(preInstall = false, task = taskLambda)

        val result = interceptor.task()

        assertTrue("Task should have been executed", taskExecuted)
        assertFalse("Task result should be false", result)
    }

    @Test
    fun `equals should return true for instances with same property values`() {
        val taskLambda1: () -> Boolean = { true }
        val taskLambda2: () -> Boolean = { true } // Different instance, same behavior for equals

        val interceptor1a = DFComponentInterceptor(preInstall = true, task = taskLambda1)
        val interceptor1b = DFComponentInterceptor(preInstall = true, task = taskLambda1) // Same task instance
        val interceptor1c = DFComponentInterceptor(preInstall = true, task = taskLambda2) // Different task instance

        assertEquals("Instances with same property values (and same task instance) should be equal", interceptor1a, interceptor1b)
        // For data classes, lambdas are compared by reference for equals().
        // So interceptor1a and interceptor1c will NOT be equal if taskLambda1 and taskLambda2 are different instances.
        assertNotEquals("Instances with different task lambda instances should not be equal by default", interceptor1a, interceptor1c)
    }

    @Test
    fun `equals should return false for instances with different preInstall values`() {
        val taskLambda: () -> Boolean = { true }
        val interceptor1 = DFComponentInterceptor(preInstall = true, task = taskLambda)
        val interceptor2 = DFComponentInterceptor(preInstall = false, task = taskLambda)

        assertNotEquals("Instances with different preInstall values should not be equal", interceptor1, interceptor2)
    }


    @Test
    fun `equals should return false for null comparison`() {
        val taskLambda: () -> Boolean = { true }
        val interceptor = DFComponentInterceptor(preInstall = true, task = taskLambda)
        assertNotEquals("Instance should not be equal to null", interceptor, null)
    }

    @Test
    fun `equals should return false for different type comparison`() {
        val taskLambda: () -> Boolean = { true }
        val interceptor = DFComponentInterceptor(preInstall = true, task = taskLambda)
        val otherObject = Any()
        assertNotEquals("Instance should not be equal to an object of a different type", interceptor, otherObject)
    }
    @Test
    fun `hashCode should be consistent for equal objects`() {
        val taskLambda: () -> Boolean = { true }
        val interceptor1a = DFComponentInterceptor(preInstall = true, task = taskLambda)
        val interceptor1b = DFComponentInterceptor(preInstall = true, task = taskLambda)

        assertEquals("HashCodes for equal instances should be the same", interceptor1a.hashCode(), interceptor1b.hashCode())
    }

    @Test
    fun `hashCode consistency for different task instances leading to non-equal objects`() {
        // As noted in equals, data classes compare lambdas by reference.
        // If task instances are different, objects are not equal, and hashCodes may differ.
        val taskLambda1: () -> Boolean = { true }
        val taskLambda2: () -> Boolean = { true }

        val interceptor1 = DFComponentInterceptor(preInstall = true, task = taskLambda1)
        val interceptor2 = DFComponentInterceptor(preInstall = true, task = taskLambda2)

        // interceptor1 and interceptor2 are not equal due to different task instances
        assertNotEquals(interceptor1, interceptor2)
        // Their hashCodes are not guaranteed to be different but often will be.
        // No direct assertion on hashCodes being different, as that's not a strict requirement for non-equal objects.
        // The main point is that if they *were* equal, their hashCodes *would* be equal.
    }


    @Test
    fun `copy should create an equal instance when no properties are changed`() {
        val taskLambda: () -> Boolean = { true }
        val original = DFComponentInterceptor(preInstall = true, task = taskLambda)
        val copied = original.copy()

        assertEquals("Copied instance with no changes should be equal to original", original, copied)
        assertNotSame("Copied instance should be a different object reference", original, copied)
        assertSame("Task lambda should be the same instance in the copied object", original.task, copied.task)
    }

    @Test
    fun `copy should create an instance with changed preInstall property`() {
        val taskLambda: () -> Boolean = { true }
        val original = DFComponentInterceptor(preInstall = true, task = taskLambda)
        val copied = original.copy(preInstall = false)

        assertFalse("Copied instance's preInstall should be false", copied.preInstall)
        assertTrue("Original instance's preInstall should remain true", original.preInstall)
        assertSame("Task lambda should be the same instance", original.task, copied.task)
        assertNotEquals("Original and copied instance with changed preInstall should not be equal", original, copied)
    }

    @Test
    fun `copy should create an instance with changed task property`() {
        val originalTask: () -> Boolean = { true }
        val newTask: () -> Boolean = { false }
        val original = DFComponentInterceptor(preInstall = true, task = originalTask)
        val copied = original.copy(task = newTask)

        assertTrue("Copied instance's preInstall should be true", copied.preInstall)
        assertSame("Copied instance's task should be the new task", newTask, copied.task)
        assertNotSame("Copied instance's task should not be the original task", originalTask, copied.task)
        assertNotEquals("Original and copied instance with changed task should not be equal", original, copied)
    }

    @Test
    fun `toString should produce a non-empty string containing class name and properties`() {
        val taskLambda: () -> Boolean = { true }
        val interceptor = DFComponentInterceptor(preInstall = true, task = taskLambda)
        val interceptorString = interceptor.toString()

        assertTrue("toString should start with class name", interceptorString.startsWith("DFComponentInterceptor("))
        assertTrue("toString should contain preInstall property", interceptorString.contains("preInstall=true"))
        assertTrue("toString should contain task property", interceptorString.contains("task="))
        // Checking for a part of the lambda representation is more robust
        assertTrue("toString should indicate a function for task", interceptorString.contains("Function0") || interceptorString.contains("() -> kotlin.Boolean"))
        assertTrue("toString should end with a parenthesis", interceptorString.endsWith(")"))
    }
}