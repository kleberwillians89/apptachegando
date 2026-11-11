package org.example.project.core.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private fun Context.hasLocationPermission(): Boolean =
    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

class AndroidLocationTracker(
    private val context: Context,
    private val permissionRequester: suspend () -> Boolean
) : LocationTracker {
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private var locationCallback: LocationCallback? = null
    private var listener: ((android.location.Location) -> Unit)? = null
    private var foreground = true

    fun setForeground(value: Boolean) {
        if (foreground == value) return
        foreground = value
        if (!value) removeUpdates() else listener?.let { startTrackingInternal(it) }
    }
    private fun removeUpdates() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    override suspend fun requestLocationPermission(): Boolean {
        if (context.hasLocationPermission()) return true
        return permissionRequester()
    }

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): Location? = withTimeoutOrNull(12_000) {
        suspendCancellableCoroutine { cont ->
            if (!context.hasLocationPermission()) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val cancellation = CancellationTokenSource()
            cont.invokeOnCancellation { cancellation.cancel() }
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(30_000)
                .setDurationMillis(10_000)
                .build()
            try {
                fusedLocationClient.getCurrentLocation(request, cancellation.token)
                    .addOnSuccessListener { location ->
                        if (cont.isActive) cont.resume(location?.let { Location(it.latitude, it.longitude) })
                    }.addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }.addOnCanceledListener {
                        if (cont.isActive) cont.resume(null)
                    }
            } catch (_: SecurityException) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun startTracking(onLocationUpdate: (Location) -> Unit) = startTrackingInternal {
        onLocationUpdate(Location(it.latitude, it.longitude))
    }

    override fun startTrackingFix(onFix: (org.example.project.core.domain.route.RouteGpsFix) -> Unit) = startTrackingInternal {
        onFix(org.example.project.core.domain.route.RouteGpsFix(
            Location(it.latitude, it.longitude), if (it.hasAccuracy()) it.accuracy.toDouble() else Double.NaN, it.time
        ))
    }

    @SuppressLint("MissingPermission")
    private fun startTrackingInternal(onLocationUpdate: (android.location.Location) -> Unit) {
        removeUpdates() // A new request must not leave the previous callback registered.
        listener = onLocationUpdate
        if (!foreground || !context.hasLocationPermission()) return
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(5000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    onLocationUpdate(it)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            locationCallback = null
        }
    }

    override fun stopTracking() {
        listener = null
        removeUpdates()
    }
}

@Composable
actual fun rememberLocationTracker(): LocationTracker {
    val context = LocalContext.current
    val pendingPermission = remember { mutableStateOf<CancellableContinuation<Boolean>?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        pendingPermission.value?.let { cont ->
            if (cont.isActive) cont.resume(granted)
        }
        pendingPermission.value = null
    }
    val tracker = remember(context, permissionLauncher) {
        AndroidLocationTracker(context) {
            if (context.hasLocationPermission()) {
                true
            } else {
                suspendCancellableCoroutine { cont ->
                    pendingPermission.value?.cancel()
                    pendingPermission.value = cont
                    cont.invokeOnCancellation {
                        if (pendingPermission.value === cont) {
                            pendingPermission.value = null
                        }
                    }
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, tracker) {
        tracker.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> tracker.setForeground(true)
                Lifecycle.Event.ON_STOP -> tracker.setForeground(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); tracker.stopTracking() }
    }
    return tracker
}
