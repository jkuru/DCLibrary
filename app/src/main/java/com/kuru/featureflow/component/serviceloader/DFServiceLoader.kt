package com.kuru.featureflow.component.serviceloader

import android.content.Context
import com.kuru.featureflow.component.register.DFComponentEntry
import java.util.ServiceLoader

interface DFServiceLoader {

    suspend fun runServiceLoaderInitialization(feature: String, context: Context) : Boolean

}

interface ServiceLoaderWrapper {
    fun loadDFComponentEntry(service:Class<DFComponentEntry> , classLoader: ClassLoader): Iterable<DFComponentEntry>
}

class DefaultServiceLoaderWrapper : ServiceLoaderWrapper {
    override fun loadDFComponentEntry(service:Class<DFComponentEntry> , classLoader: ClassLoader): Iterable<DFComponentEntry> {
        return ServiceLoader.load(service, classLoader)
    }
}