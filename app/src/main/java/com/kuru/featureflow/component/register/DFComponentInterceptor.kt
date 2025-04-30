package com.kuru.featureflow.component.register

data class DFComponentInterceptor(val preInstall: Boolean, val task: () -> Boolean)