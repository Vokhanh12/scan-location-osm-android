package com.rahul.osmdroid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import kotlin.math.ln
import kotlin.math.tan

class OnMarkerDragListenerDrawer(private val map: MapView) : Marker.OnMarkerDragListener {
    private val mTrace: ArrayList<GeoPoint> = ArrayList()
    private val mPolyline: Polygon = Polygon(map).apply {
        outlinePaint.color = Color.Red.toArgb()
        outlinePaint.strokeWidth = 2.0f
        isGeodesic = true
    }

    init {
        map.overlays.add(mPolyline)
    }

    override fun onMarkerDrag(marker: Marker) {
        // Do nothing
    }

    override fun onMarkerDragEnd(marker: Marker) {
        mTrace.add(marker.position)
        mPolyline.points = mTrace
        calculateAreaPolygon(mTrace)
        map.invalidate()
    }

    override fun onMarkerDragStart(marker: Marker) {
        // Do nothing
    }

    private fun calculateAreaPolygon(polygonVertices: ArrayList<GeoPoint>) {
        // Convert latitude/longitude coordinates to meters
        val verticesInMeters = polygonVertices.map { geoPoint ->
            val x = geoPoint.longitude * (Math.PI / 180) * 6378137.0
            val y = ln(tan((90 + geoPoint.latitude) * Math.PI / 360)) / (Math.PI / 180) * 6378137.0
            Pair(x, y)
        }

        // Calculate the area of the polygon using the shoelace formula
        var area = 0.0
        for (i in 0 until verticesInMeters.size - 1) {
            val (x1, y1) = verticesInMeters[i]
            val (x2, y2) = verticesInMeters[i + 1]
            area += x1 * y2 - x2 * y1
        }
        area /= 2

        // Ensure the area is positive
        if (area < 0) {
            area *= -1
        }

        println("Area of the polygon: $area square meters")
    }
}