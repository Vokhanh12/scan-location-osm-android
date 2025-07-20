package com.rahul.osmdroid

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.platform.ViewCompositionStrategy
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class ScreenshotService : Service() {

    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        startCapturing()
    }

    private fun startForegroundNotification() {
        val channelId = "screenshot_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Screenshot Background",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Running Screenshot Service")
            .setContentText("Capturing and uploading...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(1, notification)
    }

    private fun startCapturing() {
        scope.launch {
            while (isActive) {
                delay(5000)

                val view = createFakeViewForCapture()
                view?.let {
                    val base64 = captureViewBase64(it)
                    base64?.let { encoded ->
                        sendImageToServer(encoded)
                    }
                }
            }
        }
    }

    private fun createFakeViewForCapture(): View? {
        // 👇 Tạo view tạm (cần render nội dung bạn muốn chụp, ví dụ bản đồ hoặc OSM)
        val frame = FrameLayout(this)
        frame.setWillNotDraw(false)
        frame.setBackgroundColor(Color.TRANSPARENT)

        // Có thể gắn ComposeView, MapView,... nếu bạn cần
        return frame
    }

    private fun captureViewBase64(view: View): String? {
        val width = 500
        val height = 500
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.layout(0, 0, width, height)
        view.draw(canvas)

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun sendImageToServer(base64: String) {
        val json = JSONObject()
        json.put("hex", base64)

        val body = json.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://f5884c2a0b06.ngrok-free.app/upload")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e("SERVICE", "Upload failed: ${e.message}")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                Log.i("SERVICE", "Upload response: ${response.code}")
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
