package com.rahul.osmdroid

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.osmdroid.config.Configuration
import org.osmdroid.library.BuildConfig
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.views.overlay.simplefastpoint.LabelledGeoPoint
import java.io.ByteArrayOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            // Location permission is granted. Proceed with displaying the map.
            setContent {
                OSMMapScreen()
            }
        } else {
            // Permission is denied. Handle the case accordingly.
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionRequest.launch(arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        } else {
            // Permissions are already granted
            setContent {
                OSMMapScreen()
            }
        }
    }
}
@Suppress("NAME_SHADOWING")
@Preview
@Composable
fun OSMMapScreen() {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val boxSizeDp = 120
    val boxSizePx = (boxSizeDp * density).toInt()

    val mapView = rememberMapViewWithLifecycle()
    mapView.setMultiTouchControls(false)
    mapView.isClickable = false
    mapView.isFocusable = false
    mapView.isFocusableInTouchMode = false
    mapView.isEnabled = false

    var locationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    var currentLocationMarker by remember { mutableStateOf<Marker?>(null) }

    val pointsList = listOf(
        SurveyDetailModel(1, "Point1", "northing1", "easting1", "elevation1", "zone1", "26.905734", "75.733757", "red"),
        SurveyDetailModel(2, "Point2", "northing2", "easting2", "elevation2", "zone2", "26.905964", "75.735688", "blue"),
        SurveyDetailModel(3, "Point3", "northing3", "easting3", "elevation3", "zone3", "26.905619", "75.742855", "red")
    )
    val points = pointsList.map {
        LabelledGeoPoint(
            it.latitude.toDoubleOrNull() ?: 0.0,
            it.longitude.toDoubleOrNull() ?: 0.0,
            it.pointName
        )
    }

    val pointPlot by remember {
        mutableStateOf(
            PointPlot { surveyDetailModel ->
                Log.i("MapScreen", "Point clicked: ${surveyDetailModel.pointName}")
            }
        )
    }

    val frameLayout = remember { FrameLayout(context) }

    // ⏱️ Lặp lại capture mỗi 5 giây
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)

            val screenWidth = frameLayout.width
            val screenHeight = frameLayout.height

            if (screenWidth > 0 && screenHeight > 0) {
                val cropLeft = (screenWidth - boxSizePx) / 2
                val cropTop = (screenHeight - boxSizePx) / 2

                val bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.TRANSPARENT)
                frameLayout.draw(canvas)

                try {
                    val cropped = Bitmap.createBitmap(bitmap, cropLeft, cropTop, boxSizePx, boxSizePx)
                    val stream = ByteArrayOutputStream()
                    cropped.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                    sendImageToServer(base64)
                    Log.i("SCREENSHOT", "Cropped image base64:\n$base64")
                } catch (e: Exception) {
                    Log.e("SCREENSHOT", "Cropping failed: ${e.message}")
                }
            } else {
                Log.e("SCREENSHOT", "Invalid width/height: $screenWidth x $screenHeight")
            }
        }
    }

    AndroidView(
        factory = {
            frameLayout.apply {
                val composeView = ComposeView(context).apply {
                    setContent {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { context ->
                                    createMapView(context).apply {
                                        locationOverlay = createLocationOverlay(this) { geoPoint ->
                                            // You can restore marker logic here
                                        }
                                        overlays.add(locationOverlay)

                                        val overlay = pointPlot.plotPointOnMap(context, mapView, points, pointsList, isClick = true)
                                        overlay?.let { overlays.add(it) }
                                        invalidate()
                                    }
                                },
                                update = { mapView ->
                                    locationOverlay?.runOnFirstFix {
                                        updateMarkerPosition(mapView, locationOverlay, currentLocationMarker)
                                    }
                                }
                            )

                            Box(
                                modifier = Modifier
                                    .size(boxSizeDp.dp)
                                    .align(Alignment.Center)
                                    .border(width = 2.dp, color = Color.Red)
                            )
                        }
                    }
                }

                addView(composeView)
                setWillNotDraw(false)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        modifier = Modifier.fillMaxSize()
    )

    DisposableEffect(mapView) {
        onDispose {
            locationOverlay?.disableMyLocation()
        }
    }
}

fun sendImageToServer(base64: String) {
    val client = OkHttpClient()

    val json = JSONObject()
    json.put("hex", base64) // key là hex theo yêu cầu

    val requestBody = json.toString()
        .toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
        .url("https://f5884c2a0b06.ngrok-free.app/upload")
        .post(requestBody)
        .build()

    client.newCall(request).enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: IOException) {
            Log.e("UPLOAD", "Failed to upload: ${e.message}")
        }

        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            if (response.isSuccessful) {
                Log.i("UPLOAD", "Upload successful: ${response.body?.string()}")
            } else {
                Log.e("UPLOAD", "Upload failed: ${response.code}")
            }
        }
    })
}