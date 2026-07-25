package com.example.kmpnativefirst.task.data

internal suspend fun createWebTaskRepository(
    baseUrl: String,
    databaseName: String,
): TaskRepository {
    val stateStore = IndexedDbTaskStateStore.open(databaseName)
    return try {
        val local = InMemoryTaskLocalDataSource(
            restoredState = stateStore.load(),
            persistState = stateStore::save,
            closeState = stateStore::close,
        )
        OfflineFirstTaskRepository(
            local = local,
            remote = KtorTaskRemoteDataSource(
                baseUrl = baseUrl,
                client = createPlatformTaskHttpClient(),
            ),
            projectLocal = local,
            projectRemote = KtorTaskProjectRemoteDataSource(
                baseUrl = baseUrl,
                client = createPlatformTaskHttpClient(),
            ),
        ).initialize()
    } catch (error: Throwable) {
        stateStore.close()
        throw error
    }
}
