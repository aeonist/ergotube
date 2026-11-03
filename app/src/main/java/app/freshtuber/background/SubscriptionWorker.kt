package app.freshtuber.background

import android.content.Context
import androidx.work.*
import app.freshtuber.data.subscriptions.LocalSubscriptions
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackgroundDependencies {
    fun subscriptions(): LocalSubscriptions
}

class SubscriptionWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors.fromApplication(applicationContext, BackgroundDependencies::class.java).subscriptions()
        return if (repository.refresh(notify = true) == 0) Result.success() else Result.retry()
    }
    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SubscriptionWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("local-subscriptions", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
