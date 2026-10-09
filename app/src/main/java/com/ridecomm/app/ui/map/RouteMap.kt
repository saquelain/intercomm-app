package com.ridecomm.app.ui.map

import android.annotation.SuppressLint
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
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.ridecomm.app.trip.RideStop
import com.ridecomm.app.trip.RoutePoint
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private const val ROUTE_COLOR = 0xFFFF6A2B.toInt()

/**
 * A still map of a past ride: the route, where it started (green) and ended (pink), and the stops
 * (yellow). Google's "lite" map when Google Maps is in use (a picture of the map, free and light),
 * otherwise OpenStreetMap; both are fixed in place so the summary page scrolls normally.
 */
@Composable
fun RouteMap(route: List<RoutePoint>, stops: List<RideStop>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (remember { GoogleMapSetup.use(context) }) GoogleRouteMap(route, stops, modifier) else OsmRouteMap(route, stops, modifier)
}

@Composable
private fun GoogleRouteMap(route: List<RoutePoint>, stops: List<RideStop>, modifier: Modifier) {
    val context = LocalContext.current
    val mapView = remember { MapView(context, GoogleMapOptions().liteMode(true).mapToolbarEnabled(false)) }
    MapLifecycle(mapView)
    LaunchedEffect(mapView, route) {
        mapView.getMapAsync { map ->
            map.clear()
            map.uiSettings.isMapToolbarEnabled = false
            map.setOnMarkerClickListener { true }
            if (route.isEmpty()) return@getMapAsync
            val points = route.map { LatLng(it.lat, it.lon) }
            map.addPolyline(PolylineOptions().addAll(points).color(ROUTE_COLOR).width(10f))
            map.addMarker(MarkerOptions().position(points.first()).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))
            map.addMarker(MarkerOptions().position(points.last()).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE)))
            stops.forEach {
                map.addMarker(MarkerOptions().position(LatLng(it.lat, it.lon)).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)))
            }
            mapView.post {
                val bounds = LatLngBounds.builder().apply { points.forEach { include(it) } }.build()
                val pad = (32 * context.resources.displayMetrics.density).toInt()
                if (mapView.width > 0 && mapView.height > 0) {
                    runCatching { map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, mapView.width, mapView.height, pad)) }
                }
                if (map.cameraPosition.zoom > 16f) map.moveCamera(CameraUpdateFactory.zoomTo(16f))
            }
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

@SuppressLint("ClickableViewAccessibility") // a picture of the route: touches are for the page, not the map
@Composable
private fun OsmRouteMap(route: List<RoutePoint>, stops: List<RideStop>, modifier: Modifier) {
    val context = LocalContext.current
    val map = remember {
        MapSetup.configure(context)
        org.osmdroid.views.MapView(context).apply {
            setTileSource(MapSetup.tiles)
            setMultiTouchControls(false)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            setOnTouchListener { _, _ -> true }
        }
    }
    DisposableEffect(map) {
        map.onResume()
        onDispose {
            map.onPause()
            map.onDetach()
        }
    }
    AndroidView(
        factory = { map },
        modifier = modifier,
        update = { m ->
            m.overlays.clear()
            if (route.isEmpty()) return@AndroidView
            val points = route.map { GeoPoint(it.lat, it.lon) }
            m.overlays.add(Polyline(m).apply { setPoints(points); outlinePaint.color = ROUTE_COLOR; outlinePaint.strokeWidth = 10f })
            fun pin(at: GeoPoint, title: String) = Marker(m).apply {
                position = at
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                this.title = title
            }
            m.overlays.add(pin(points.first(), "Start"))
            m.overlays.add(pin(points.last(), "End"))
            stops.forEach { m.overlays.add(pin(GeoPoint(it.lat, it.lon), "Stop")) }
            m.post {
                if (points.size == 1) {
                    m.controller.setZoom(15.0)
                    m.controller.setCenter(points.first())
                } else {
                    val box = BoundingBox.fromGeoPoints(points)
                    m.zoomToBoundingBox(box.increaseByScale(1.25f), false, (24 * m.resources.displayMetrics.density).toInt())
                }
            }
            m.invalidate()
        },
    )
}
