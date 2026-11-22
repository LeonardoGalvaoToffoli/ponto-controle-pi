package com.leonardogalvao.pontocontrole.feature.home

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import java.time.Instant
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

data class GpsReading(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val capturedAt: Instant,
)

sealed interface GpsResult {
    data class Success(val reading: GpsReading) : GpsResult
    data class Failure(val message: String) : GpsResult
}

class GpsLocationProvider(private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission")
    suspend fun current(): GpsResult {
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return GpsResult.Failure(
                "Ative o GPS do aparelho. No emulador, defina também a localização da demonstração.",
            )
        }
        val location = withTimeoutOrNull(30_000) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val cancellation = CancellationSignal()
                continuation.invokeOnCancellation { cancellation.cancel() }
                LocationManagerCompat.getCurrentLocation(
                    manager,
                    LocationManager.GPS_PROVIDER,
                    cancellation,
                    ContextCompat.getMainExecutor(context),
                ) { value -> if (continuation.isActive) continuation.resume(value) }
            }
        } ?: return GpsResult.Failure("Não foi possível obter uma leitura atual do GPS.")

        val ageNanos = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
        if (ageNanos < 0 || ageNanos > 30_000_000_000L) {
            return GpsResult.Failure("A leitura do GPS está antiga. Tente novamente.")
        }
        if (!location.hasAccuracy() || location.accuracy <= 0f || location.accuracy > 50f) {
            return GpsResult.Failure(
                "A precisão atual é ${location.accuracy.toInt()} m. Aguarde até ficar em 50 m ou menos.",
            )
        }
        return GpsResult.Success(
            GpsReading(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = location.accuracy,
                capturedAt = Instant.ofEpochMilli(location.time),
            ),
        )
    }
}
