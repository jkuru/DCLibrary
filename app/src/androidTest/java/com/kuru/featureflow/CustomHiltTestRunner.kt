package com.kuru.featureflow

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

class CustomHiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application {
        println("CustomHiltTestRunner: Initializing HiltTestApplication")
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}