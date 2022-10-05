package com.avito.emcee.worker.internal.di

import com.avito.emcee.worker.Config
import com.avito.emcee.worker.WorkerQueueApi.Companion.createWorkerQueueApi
import com.avito.emcee.worker.internal.TestJobProducer
import com.avito.emcee.worker.internal.TestJobProducerImpl
import com.avito.emcee.worker.internal.consumer.FakeTestJobConsumer
import com.avito.emcee.worker.internal.consumer.TestJobConsumer
import com.avito.emcee.worker.internal.identifier.HostnameWorkerIdProvider
import com.avito.emcee.worker.internal.identifier.WorkerIdProvider
import com.avito.emcee.worker.internal.networking.SocketAddressResolver
import com.avito.emcee.worker.internal.rest.HttpServer
import com.avito.emcee.worker.internal.rest.handler.HealthCheckRequestHandler
import com.avito.emcee.worker.internal.rest.handler.ProcessingBucketsRequestHandler
import com.avito.emcee.worker.internal.storage.ProcessingBucketsStorage
import com.avito.emcee.worker.internal.storage.SingleElementProcessingBucketsStorage
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.logging.Logger
import kotlin.time.ExperimentalTime

@ExperimentalTime
internal class WorkerDI(
    private val config: Config,
    private val debugMode: Boolean
) {

    private val workerIdProvider: WorkerIdProvider = HostnameWorkerIdProvider()
    private val okHttpClient = OkHttpClient.Builder().apply {
        if (debugMode) {
            val logger = Logger.getLogger("HTTP")
            addInterceptor(HttpLoggingInterceptor { message ->
                logger.fine(message)
            }.apply { level = HttpLoggingInterceptor.Level.BODY })
        }
    }.build()
    private val socketAddressResolver = SocketAddressResolver()
    private val bucketsStorage: ProcessingBucketsStorage = SingleElementProcessingBucketsStorage()

    private val api =
        Retrofit.Builder()
            .createWorkerQueueApi(okHttpClient, config.queueUrl)

    fun producer(): TestJobProducer {
        return TestJobProducerImpl(
            api = api,
            workerId = workerIdProvider.provide(),
            workerAddress = socketAddressResolver.resolve(config.workerPort)
        )
    }

    fun consumer(): TestJobConsumer {
        return FakeTestJobConsumer(
            api = api,
            bucketsStorage = bucketsStorage,
        )
    }

    fun httpServer(): HttpServer {
        return HttpServer(
            handlers = listOf(
                ProcessingBucketsRequestHandler(bucketsStorage),
                HealthCheckRequestHandler,
            ),
            debug = debugMode,
            port = config.workerPort,
        )
    }
}
