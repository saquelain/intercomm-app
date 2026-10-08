package com.ridecomm.app.ui.map

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.ridecomm.app.BuildConfig
import com.ridecomm.app.Prefs
import com.ridecomm.app.R
import com.ridecomm.app.group.GroupState

/** Which map the Group map shows (Settings → Map). */
enum class MapProvider(val label: String) {
    GOOGLE("Google Maps"),
    OSM("OpenStreetMap"),
}

object GoogleMapSetup {
    /** Google Maps can run here: this build has a key and the phone has Google Play services. */
    fun available(context: Context): Boolean = BuildConfig.HAS_GOOGLE_MAPS && runCatching {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }.getOrDefault(false)

    /** The rider picked Google Maps and it can run; otherwise the Group map is OpenStreetMap. */
    fun use(context: Context): Boolean = Prefs.mapProvider(context) == MapProvider.GOOGLE && available(context)
}

/**
 * The Group map on Google Maps: the same rider photos, flags and hazards as the OpenStreetMap
 * version (drawn by [MarkerPainter] into marker pictures), so the two look alike apart from the map.
 * [topPx] / [bottomPx] are the parts covered by the top bar and bottom panel: the map keeps
 * Google's logo and the fitted riders clear of them. Dragging the map stops following me ([onGesture]).
 */
@Composable
internal fun GoogleLiveMap(
    group: GroupState,
    places: List<GroupOverlay.Place>,
    focus: Spot?,
    follow: Boolean,
    fitKey: Int,
    dark: Boolean,
    topPx: Int,
    bottomPx: Int,
    onGesture: () -> Unit,
    onLongPress: (Spot) -> Unit,
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(
            context,
            GoogleMapOptions()
                .camera(CameraPosition.fromLatLngZoom(LatLng(20.6, 78.9), 5f))
                .mapToolbarEnabled(false)
                .zoomControlsEnabled(false)
                .compassEnabled(true)
                .tiltGesturesEnabled(false),
        )
    }
    var map by remember { mutableStateOf<GoogleMap?>(null) }
    val painter = remember { MarkerPainter(context) }
    val held = remember { Held() }
    val shown = held.shown
    val gesture by rememberUpdatedState(onGesture)
    val longPress by rememberUpdatedState(onLongPress)

    MapLifecycle(mapView)
    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.isMapToolbarEnabled = false
            m.setOnMapLongClickListener { longPress(Spot(it.latitude, it.longitude)) }
            m.setOnCameraMoveStartedListener { reason ->
                if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) gesture()
            }
            // Tap a rider (or flag) to bring them to the middle.
            m.setOnMarkerClickListener { marker ->
                gesture()
                m.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.position, maxOf(m.cameraPosition.zoom, 16f)), 600, null)
                true
            }
            map = m
        }
    }
    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

    val m = map ?: return
    SideEffect {
        m.setPadding(0, topPx, 0, bottomPx)
        if (held.styled != dark) {
            held.styled = dark
            m.setMapStyle(if (dark) MapStyleOptions.loadRawResourceStyle(context, R.raw.map_night) else null)
        }

        val seen = HashSet<String>()
        places.forEach { p ->
            seen += p.key
            // Google places the picture; my GPS accuracy is a real circle on the map instead.
            val look = when (val made = p.make(0f, 0f, Float.MAX_VALUE)) {
                is MapMarker.Me -> made.copy(accuracyPx = 0f)
                else -> made
            }
            val at = LatLng(p.lat, p.lon)
            val old = shown[p.key]
            if (old == null) {
                val icon = painter.icon(look)
                val marker = m.addMarker(
                    MarkerOptions()
                        .position(at)
                        .icon(BitmapDescriptorFactory.fromBitmap(icon.bitmap))
                        .anchor(icon.anchorX, icon.anchorY)
                        .zIndex(layer(look)),
                ) ?: return@forEach
                shown[p.key] = marker to look
            } else {
                val (marker, was) = old
                if (marker.position != at) marker.position = at
                if (was != look) {
                    val icon = painter.icon(look)
                    marker.setIcon(BitmapDescriptorFactory.fromBitmap(icon.bitmap))
                    marker.setAnchor(icon.anchorX, icon.anchorY)
                    shown[p.key] = marker to look
                }
            }
        }
        shown.keys.filter { it !in seen }.forEach { shown.remove(it)?.first?.remove() }

        val me = group.me
        if (me != null && me.accuracyM > 0) {
            val circle = held.accuracy
            if (circle == null) {
                held.accuracy = m.addCircle(
                    CircleOptions()
                        .center(LatLng(me.lat, me.lon))
                        .radius(me.accuracyM.toDouble())
                        .fillColor(android.graphics.Color.argb(40, 0xFF, 0x8A, 0x1F))
                        .strokeWidth(0f),
                )
            } else {
                circle.center = LatLng(me.lat, me.lon)
                circle.radius = me.accuracyM.toDouble()
            }
        } else {
            held.accuracy?.remove()
            held.accuracy = null
        }

        val spots = spotsOf(group)
        when {
            focus != null && focus != held.lastFocus -> {
                held.lastFocus = focus
                m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(focus.lat, focus.lon), 16f), 600, null)
            }
            follow && me != null -> m.animateCamera(CameraUpdateFactory.newLatLng(LatLng(me.lat, me.lon)))
            fitKey != held.fittedKey && spots.isNotEmpty() -> {
                held.fittedKey = fitKey
                held.lastFocus = null
                mapView.post { fit(m, mapView, spots) }
            }
        }
    }
}

/** What's on the Google map now; plain fields, as changing them shouldn't redraw the screen. */
private class Held {
    val shown = mutableMapOf<String, Pair<Marker, MapMarker>>()
    var accuracy: Circle? = null
    var styled: Boolean? = null
    var fittedKey = -1
    var lastFocus: Spot? = null
}

/** Hazards at the back, then flags, riders, and me on top. */
private fun layer(m: MapMarker): Float = when (m) {
    is MapMarker.Hazard -> 1f
    is MapMarker.Regroup -> 2f
    is MapMarker.Rider -> 3f
    is MapMarker.Me -> 4f
}

/** Everyone on screen (or the one spot, closer in), never closer than street level. */
private fun fit(m: GoogleMap, view: MapView, spots: List<Spot>) {
    if (spots.size == 1) {
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(spots[0].lat, spots[0].lon), 15.5f), 500, null)
        return
    }
    val bounds = LatLngBounds.builder().apply { spots.forEach { include(LatLng(it.lat, it.lon)) } }.build()
    val edge = (56 * view.resources.displayMetrics.density).toInt()
    val update = runCatching { CameraUpdateFactory.newLatLngBounds(bounds, edge) }.getOrNull() ?: return
    runCatching {
        m.animateCamera(update, 500, object : GoogleMap.CancelableCallback {
            override fun onFinish() {
                if (m.cameraPosition.zoom > 17f) m.animateCamera(CameraUpdateFactory.zoomTo(17f))
            }

            override fun onCancel() = Unit
        })
    }
}

/** Passes the screen's lifecycle on to the map, as Google's MapView needs. */
@Composable
private fun MapLifecycle(mapView: MapView) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        mapView.onCreate(null)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        // Replays start and resume when the screen is already showing.
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            val state = lifecycle.currentState
            if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }
}
