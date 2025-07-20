package com.rahul.osmdroid

import android.annotation.SuppressLint
import android.content.ContentValues.TAG
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.api.IGeoPoint
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MinimapOverlay
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.views.overlay.mylocation.SimpleLocationOverlay
import org.osmdroid.views.overlay.simplefastpoint.LabelledGeoPoint
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlay
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlayOptions
import org.osmdroid.views.overlay.simplefastpoint.SimplePointTheme
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.view.MotionEvent

fun MapView.enableMyLocation(icon: Bitmap? = null): MapView {
    SimpleLocationOverlay(icon)
        .also { overlays.add(it) }
    return this
}

fun MapView.enableMyLocation(personIcon: Bitmap? = null, directionIcon: Bitmap? = null): MapView {
    MyLocationNewOverlay(GpsMyLocationProvider(context), this)
        .apply {
            personIcon?.also { setPersonIcon(it) }
            directionIcon?.also { setDirectionArrow(personIcon,it) }
            enableMyLocation()
            // enableFollowLocation()
        }
        .also { overlays.add(it) }
    return this
}

fun MapView.enableCompass(): MapView {
    CompassOverlay(context, InternalCompassOrientationProvider(context), this)
        .apply { enableCompass() }
        .also { overlays.add(it) }
    return this
}

fun MapView.enableScaleBar(): MapView {
    ScaleBarOverlay(this).apply {
        setCentred(true)
        setScaleBarOffset(context.resources.displayMetrics.widthPixels / 2, 10)
    }.also { overlays.add(it) }
    return this
}

fun MapView.enableMinimap(): MapView {
    MinimapOverlay(context, tileRequestCompleteHandler)
        .apply {
            width = context.resources.displayMetrics.widthPixels / 5
            height = context.resources.displayMetrics.heightPixels / 5
            //optionally, you can set the minimap to a different tile source
            //setTileSource(....)
        }.also { overlays.add(it) }
    return this
}

fun MapView.enableRotationGesture(): MapView {
    RotationGestureOverlay(this).apply {
        this.isEnabled = true
    }.also { overlays.add(it) }
    return this
}

fun createMapView(context: Context): MapView {
    return MapView(context).apply {
        enableCompass()
        enableScaleBar()
        setTileSource(TileSourceFactory.MAPNIK)
        isHorizontalMapRepetitionEnabled = false
        isVerticalMapRepetitionEnabled = false
        setScrollableAreaLimitLatitude(
            MapView.getTileSystem().maxLatitude,
            MapView.getTileSystem().minLatitude,
            0
        )
        controller.setZoom(22.0)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.ALWAYS)
        setBuiltInZoomControls(false)
        setMultiTouchControls(true)
    }
}


fun createRedDotBitmap(size: Int = 20): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.RED
        isAntiAlias = true
    }
    val radius = size / 2f
    canvas.drawCircle(radius, radius, radius, paint)
    return bitmap
}

fun createLocationOverlay(mapView: MapView, onFirstFix: (GeoPoint) -> Unit): MyLocationNewOverlay {
    val redDot = createRedDotBitmap()

    return MyLocationNewOverlay(mapView).apply {
        enableMyLocation()
        enableFollowLocation()

        setDrawAccuracyEnabled(false)
        setPersonIcon(redDot)
        setDirectionArrow(redDot, redDot)

        runOnFirstFix {
            myLocation?.let { location ->
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                Handler(Looper.getMainLooper()).post {
                    onFirstFix(geoPoint)
                }
            }
        }
    }
}


fun updateCurrentLocationMarker(
    mapView: MapView,
    geoPoint: GeoPoint,
    currentLocationMarker: Marker?,
    setCurrentLocationMarker: (Marker) -> Unit
) {
    if (currentLocationMarker == null) {
        val marker = Marker(mapView).apply {
            position = geoPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
//            setOnMarkerDragListener(OnMarkerDragListenerDrawer(mapView))
//            isDraggable = true // Enable marker dragging
            title = "Current Location"
            mapView.overlays.add(this)
        }
        setCurrentLocationMarker(marker)
    } else {
        currentLocationMarker.position = geoPoint
    }
    mapView.controller.setCenter(geoPoint)
    Log.i("TAG", "OSMMapScreen: Marker added/updated")
}

fun updateMarkerPosition(
    mapView: MapView,
    locationOverlay: MyLocationNewOverlay?,
    currentLocationMarker: Marker?
) {
    val location = locationOverlay?.myLocation
    if (location != null) {
        val geoPoint = GeoPoint(location.latitude, location.longitude)
        Handler(Looper.getMainLooper()).post {
            currentLocationMarker?.let { marker ->
                marker.position = geoPoint
                mapView.controller.setCenter(geoPoint)
                mapView.invalidate()
                Log.i("TAG", "OSMMapScreen: Marker position updated")
            }
        }
    }
}


@Composable
fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    DisposableEffect(mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {
                    Unit
                }
            }
        }

        val lifecycle = (context as ComponentActivity).lifecycle
        lifecycle.addObserver(observer)

        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    return mapView
}

data class SurveyDetailModel(
    val pointId: Int,
    val pointName: String,
    val northing: String,
    val easting: String,
    val elevation: String,
    val zone: String,
    val latitude: String,
    val longitude: String,
    val color: String
)

class PointPlot(private val pointPlot: (SurveyDetailModel) -> Unit) {
    @SuppressLint("SuspiciousIndentation")
    fun plotPointOnMap(
        context: Context,
        mapView: MapView,
        points: List<IGeoPoint>,
        pointsList: List<SurveyDetailModel>,
        isClick: Boolean
    ): SimpleFastPointOverlay? {
        try {
            val pt = SimplePointTheme(points, true)
            val textStyle = Paint().apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#FF000000")
                textAlign = Paint.Align.CENTER
                textSize = 24f
            }
            val opt = SimpleFastPointOverlayOptions.getDefaultStyle()
                .setAlgorithm(SimpleFastPointOverlayOptions.RenderingAlgorithm.MAXIMUM_OPTIMIZATION)
                .setRadius(10f).setIsClickable(isClick).setCellSize(15).setTextStyle(textStyle)
                .setIsClickable(isClick).setPointStyle(textStyle)
                .setSymbol(SimpleFastPointOverlayOptions.Shape.CIRCLE)
            return SimpleFastPointOverlay(pt, opt).apply {
                setOnClickListener { points, point ->
                    try {
                        val selectedValue = (points[point] as LabelledGeoPoint).label
                        val clickedPoint = pointsList.find { it.pointName == selectedValue }?.let { originalPoint ->
                            if (originalPoint.latitude.isEmpty() && originalPoint.longitude.isEmpty()) {
                                originalPoint.copy(
                                    latitude = points[point].latitude.toString(),
                                    longitude = points[point].longitude.toString()
                                )
                            } else {
                                originalPoint
                            }
                        }
                        clickedPoint?.let {
                            pointPlot.invoke(it)
                            selectedPoint = point
                        }

                    } catch (e: Exception) {
                        Log.e("PointPlot", "Exception in setOnClickListener: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PointPlot", "Exception in plotPointOnMap: ${e.message}")
            return null
        }
    }
}


class LockedMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : MapView(context, attrs) {

    var isMapLocked: Boolean = false

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        return if (isMapLocked) {
            // Bỏ qua mọi sự kiện touch nếu đã khóa
            true
        } else {
            super.dispatchTouchEvent(ev)
        }
    }
}
