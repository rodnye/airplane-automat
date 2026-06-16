package com.airplane.automat

import android.content.ContentResolver
import android.content.Context
import android.media.ToneGenerator
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.lang.reflect.Method

class AirplaneModeHelper {

    private val TAG = "AirplaneModeHelper"

    /**
     * Cambia el estado del modo avión.
     * Requiere el permiso WRITE_SECURE_SETTINGS (otorgado por ADB o root).
     */
    fun setAirplaneMode(context: Context, enabled: Boolean) {
        val resolver: ContentResolver = context.contentResolver
        Settings.Global.putInt(resolver, Settings.Global.AIRPLANE_MODE_ON, if (enabled) 1 else 0)

        // Enviar broadcast para que el sistema aplique el cambio
        val intent = android.content.Intent(android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED)
        intent.putExtra("state", enabled)
        context.sendBroadcast(intent)

        Log.d(TAG, "Airplane mode set to: $enabled")
    }

    /**
     * Verifica si el hotspot está activo usando reflexión
     */
    fun isHotspotEnabled(context: Context): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return try {
            val method: Method = wifiManager.javaClass.getMethod("getWifiApState")
            val state = method.invoke(wifiManager) as Int
            // 13 = WIFI_AP_STATE_ENABLED (según documentación interna)
            state == 13
        } catch (e: Exception) {
            Log.e(TAG, "Error checking hotspot state via reflection", e)
            false
        }
    }

    /**
     * Restaura el hotspot. En Android 8.0+ abre la configuración de anclaje,
     * en versiones anteriores intenta activarlo por reflexión.
     */
    fun restoreHotspot(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8.0+: no se puede activar programáticamente, abrimos settings
            val intent = android.content.Intent("android.settings.TETHER_SETTINGS")
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "Opening tether settings for user to re-enable hotspot")
        } else {
            // Versiones anteriores: reflexión para activar hotspot
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val configClass = Class.forName("android.net.wifi.WifiConfiguration")
                val config = configClass.newInstance()
                val ssidField = configClass.getField("SSID")
                ssidField.set(config, "\"AirplaneAutoMat\"")

                // Configuración básica WPA2
                val keyMgmtField = configClass.getField("allowedKeyManagement")
                val keyMgmt = keyMgmtField.get(config) as MutableSet<Any>
                val wpaPsk = Class.forName("android.net.wifi.WifiConfiguration\$KeyMgmt").getField("WPA_PSK").get(null)
                keyMgmt.add(wpaPsk)

                val method: Method = wifiManager.javaClass.getMethod("setWifiApEnabled", configClass, Boolean::class.javaPrimitiveType)
                method.invoke(wifiManager, config, true)
                Log.d(TAG, "Hotspot re-enabled via reflection")
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring hotspot via reflection", e)
            }
        }
    }

    /**
     * Reproduce un sonido simple usando ToneGenerator.
     */
    fun playReconnectSound(context: Context) {
        try {
            val toneGenerator = ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 500) // 500 ms
            toneGenerator.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing sound", e)
        }
    }
}
