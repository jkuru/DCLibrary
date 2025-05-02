package com.kuru.featureflow.component.serviceloader

import android.content.Context

interface DFServiceLoader {

    suspend fun runServiceLoaderInitialization(feature: String, context: Context) : Boolean

}