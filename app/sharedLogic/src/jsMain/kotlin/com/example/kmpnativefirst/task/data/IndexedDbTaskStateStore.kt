@file:OptIn(ExperimentalWasmJsInterop::class)

package com.example.kmpnativefirst.task.data

import js.reflect.unsafeCast
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import web.events.EventHandler
import web.idb.IDBDatabase
import web.idb.IDBRequest
import web.idb.IDBTransaction
import web.idb.IDBTransactionMode
import web.idb.IDBValidKey
import web.idb.indexedDB
import web.idb.readwrite
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class IndexedDbTaskStateStore private constructor(
    private val database: IDBDatabase,
    private val json: Json,
) {
    suspend fun load(): TaskLocalState? {
        val transaction = database.transaction(STORE_NAME)
        val request = transaction
            .objectStore(STORE_NAME)
            .get(IDBValidKey(STATE_KEY))
            .unsafeCast<IDBRequest<JsString?>>()
        val encoded = request.awaitResult() ?: return null
        return json.decodeFromString(encoded)
    }

    suspend fun save(state: TaskLocalState) {
        val transaction = database.transaction(
            storeNames = STORE_NAME,
            mode = IDBTransactionMode.readwrite,
        )
        transaction.awaitCompletion {
            transaction
                .objectStore(STORE_NAME)
                .put(
                    value = json.encodeToString(state).unsafeCast<JsAny>(),
                    key = IDBValidKey(STATE_KEY),
                )
        }
    }

    fun close() {
        database.close()
    }

    companion object {
        private const val DATABASE_VERSION = 1
        private const val STORE_NAME = "task-state"
        private const val STATE_KEY = "current"

        suspend fun open(
            databaseName: String,
            json: Json = Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
            },
        ): IndexedDbTaskStateStore {
            val request = indexedDB.open(databaseName, DATABASE_VERSION.toDouble())
            request.onupgradeneeded = EventHandler {
                val database = request.result
                if (!database.objectStoreNames.contains(STORE_NAME)) {
                    database.createObjectStore(STORE_NAME)
                }
            }
            val database = request.awaitResult()
            database.onversionchange = EventHandler(database::close)
            return IndexedDbTaskStateStore(database, json)
        }
    }
}

private suspend fun <T : JsAny?> IDBRequest<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        onsuccess = EventHandler {
            if (continuation.isActive) {
                continuation.resume(result)
            }
        }
        onerror = EventHandler {
            if (continuation.isActive) {
                continuation.resumeWithException(
                    IllegalStateException(error?.message ?: "IndexedDB request failed."),
                )
            }
        }
    }

private suspend fun IDBTransaction.awaitCompletion(
    action: () -> Unit,
) {
    suspendCancellableCoroutine { continuation ->
        oncomplete = EventHandler {
            if (continuation.isActive) {
                continuation.resume(Unit)
            }
        }
        onabort = EventHandler {
            if (continuation.isActive) {
                continuation.resumeWithException(
                    IllegalStateException(error?.message ?: "IndexedDB transaction was aborted."),
                )
            }
        }
        onerror = EventHandler {
            if (continuation.isActive) {
                continuation.resumeWithException(
                    IllegalStateException(error?.message ?: "IndexedDB transaction failed."),
                )
            }
        }
        try {
            action()
        } catch (error: Throwable) {
            if (continuation.isActive) {
                continuation.resumeWithException(error)
            }
        }
    }
}
