package com.airplane.automat

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

  private lateinit var statusText: TextView
  private lateinit var actionButton: Button
  private val helper = AirplaneModeHelper()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    statusText = findViewById(R.id.statusText)
    actionButton = findViewById(R.id.actionButton)

    // Iniciar el servidor web en segundo plano
    startService(Intent(this, WebServerService::class.java))

    actionButton.setOnClickListener {
      performReconnect()
    }
  }

  private fun performReconnect() {
    actionButton.isEnabled = false
    statusText.setText(R.string.status_toggling)

    lifecycleScope.launch {
      try {
        withContext(Dispatchers.IO) {
          // 1. Guardar estado del hotspot
          val hotspotWasOn = helper.isHotspotEnabled(applicationContext)

          // 2. Activar modo avión
          helper.setAirplaneMode(applicationContext, true)

          // 3. Esperar 3 segundos
          withContext(Dispatchers.Main) {
            statusText.setText(R.string.status_waiting)
          }
          Thread.sleep(3000)

          // 4. Desactivar modo avión
          helper.setAirplaneMode(applicationContext, false)

          // 5. Restaurar hotspot si estaba activo
          if (hotspotWasOn) {
            withContext(Dispatchers.Main) {
              statusText.setText(R.string.status_restoring)
            }
            helper.restoreHotspot(applicationContext)
          }

          // 6. Reproducir sonido
          helper.playReconnectSound(applicationContext)
        }

        withContext(Dispatchers.Main) {
          statusText.setText(R.string.status_done)
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          statusText.setText(getString(R.string.status_error, e.message))
        }
      } finally {
        withContext(Dispatchers.Main) {
          actionButton.isEnabled = true
        }
      }
    }
  }
}