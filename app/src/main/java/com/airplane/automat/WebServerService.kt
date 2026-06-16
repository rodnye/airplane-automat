package com.airplane.automat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import fi.iki.elonen.NanoHTTPD

class WebServerService : Service() {

  private lateinit var server: AirplaneServer
  private val PORT = 8080

  companion object {
    private const val CHANNEL_ID = "webserver_channel"
    private const val NOTIFICATION_ID = 1001
  }

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    startForeground(NOTIFICATION_ID, createNotification())

    server = AirplaneServer(PORT, applicationContext)
    try {
      server.start()
      Log.d("WebServerService", "Servidor HTTP iniciado en puerto $PORT")
    } catch (e: Exception) {
      Log.e("WebServerService", "Error al iniciar servidor", e)
    }
  }

  override fun onDestroy() {
    server.stop()
    Log.d("WebServerService", "Servidor HTTP detenido")
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Servidor HTTP",
        NotificationManager.IMPORTANCE_LOW
      )
      val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      manager.createNotificationChannel(channel)
    }
  }

  private fun createNotification(): Notification {
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("Airplane AutoMat")
      .setContentText("Servidor HTTP activo en puerto $PORT")
      .setSmallIcon(android.R.drawable.ic_menu_upload)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }

  /**
   * Clase interna que extiende NanoHTTPD para manejar las peticiones GET.
   */
  inner class AirplaneServer(port: Int, private val appContext: Context) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
      return when (session.method) {
        Method.GET -> {
          // Ejecutar la reconexión en un hilo separado para no bloquear la respuesta
          Thread {
            try {
              val helper = AirplaneModeHelper()
              val hotspotWasOn = helper.isHotspotEnabled(appContext)
              helper.setAirplaneMode(appContext, true)
              Thread.sleep(3000)
              helper.setAirplaneMode(appContext, false)
              if (hotspotWasOn) {
                helper.restoreHotspot(appContext)
              }
              helper.playReconnectSound(appContext)
            } catch (e: Exception) {
              Log.e("AirplaneServer", "Error en reconexión vía HTTP", e)
            }
          }.start()

          // Respuesta inmediata
          newFixedLengthResponse(
            Response.Status.OK,
            "text/plain",
            "Reconexión iniciada. Espera unos segundos..."
          )
        }
        else -> newFixedLengthResponse(
          Response.Status.METHOD_NOT_ALLOWED,
          "text/plain",
          "Método no permitido"
        )
      }
    }
  }
}