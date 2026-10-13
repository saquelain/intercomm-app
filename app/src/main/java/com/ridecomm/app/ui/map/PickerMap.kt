package com.ridecomm.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController

/** Moves a picker map: set it and the map flies there (the "My location" button). */
class PickerControl {
    internal var goTo: ((Double, Double) -> Unit)? = null

    fun moveTo(lat: Double, lon: Double) = goTo?.invoke(lat, lon)
}

/**
 * A map to pick a spot on: the rider moves the map under a pin fixed in the middle, and [onCenter] gets
 * the middle each time the map comes to rest. Google Maps when in use, otherwise OpenStreetMap.
 * [start] is where it opens (zoomed in), or all of India when unknown.
 */
@Composable
fun PickerMap(start: Pair<Double, Double>?, control: PickerControl, onCenter: (Double, Double) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val zoom = if (start != null) 15f else 5f
    val (lat0, lon0) = start ?: (20.6 to 78.9)
    if (remember { GoogleMapSetup.use(context) }) {
        val mapView = remember { MapView(context, GoogleMapOptions().mapToolbarEnabled(false)) }
        MapLifecycle(mapView)
        LaunchedEffect(mapView) {
            mapView.getMapAsync { map ->
                map.uiSettings.isMapToolbarEnabled = false
                map.uiSettings.isRotateGesturesEnabled = false
                map.uiSettings.isTiltGesturesEnabled = false
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat0, lon0), zoom))
                map.setOnCameraIdleListener { map.cameraPosition.target.let { onCenter(it.latitude, it.longitude) } }
                control.goTo = { lat, lon -> map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 16f)) }
            }
        }
        AndroidView(factory = { mapView }, modifier = modifier)
    } else {
        val map = remember {
            MapSetup.configure(context)
            org.osmdroid.views.MapView(context).apply {
                setTileSource(MapSetup.tiles)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                isTilesScaledToDpi = true
                controller.setZoom(zoom.toDouble())
                controller.setCenter(GeoPoint(lat0, lon0))
            }
        }
        DisposableEffect(map) {
            val listener = object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean = report()
                override fun onZoom(event: ZoomEvent?): Boolean = report()
                private fun report(): Boolean {
                    map.mapCenter.let { onCenter(it.latitude, it.longitude) }
                    return false
                }
            }
            map.addMapListener(listener)
            control.goTo = { lat, lon ->
                map.controller.setZoom(16.0)
                map.controller.animateTo(GeoPoint(lat, lon))
            }
            map.onResume()
            onDispose {
                map.removeMapListener(listener)
                map.onPause()
                map.onDetach()
            }
        }
        AndroidView(factory = { map }, modifier = modifier)
    }
}
