package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api

import kotlinx.coroutines.delay
import kotlin.math.pow

class RetryOperation internal constructor(
    private val retries: Int,
    private val initialIntervalMilli: Long = 1000,
    private val retryStrategy: RetryStrategy = RetryStrategy.LINEAR,
    private val retry: suspend RetryOperation.() -> Unit
) {
    var tryNumber: Int = 0
        internal set

    suspend fun operationFailed() {
        tryNumber++
        if (tryNumber < retries) {
            delay(calculateDelay(tryNumber, initialIntervalMilli, retryStrategy))
            retry.invoke(this)
        }
    }
}

enum class RetryStrategy {
    CONSTANT, LINEAR, EXPONENTIAL
}

suspend fun retryOperation(
    retries: Int = 100,
    initialDelay: Long = 0,
    initialIntervalMilli: Long = 1000,
    retryStrategy: RetryStrategy = RetryStrategy.LINEAR,
    operation: suspend RetryOperation.() -> Unit
) {
    val retryOperation = RetryOperation(
        retries,
        initialIntervalMilli,
        retryStrategy,
        operation,
    )

    delay(initialDelay)

    operation.invoke(retryOperation)
}

internal fun calculateDelay(tryNumber: Int, initialIntervalMilli: Long, retryStrategy: RetryStrategy): Long {
    return when (retryStrategy) {
        RetryStrategy.CONSTANT -> initialIntervalMilli
        RetryStrategy.LINEAR -> initialIntervalMilli * tryNumber
        RetryStrategy.EXPONENTIAL -> 2.0.pow(tryNumber).toLong()
    }
}
