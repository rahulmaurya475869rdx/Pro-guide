package com.example.proguide

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.BatteryManager
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import java.util.UUID

object MeshSosManager {
    private const val SERVICE_ID = "com.example.proguide.SOS_SERVICE"
    private const val TAG = "MeshSos"

    private val processedMessages = HashSet<String>()
    private var connectionsClient: ConnectionsClient? = null

    // Start Radar (Discovery + Advertising) - Call in onResume
    fun startRadar(context: Context, userName: String) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return
        connectionsClient = Nearby.getConnectionsClient(context)

        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()

        // 1. Discovery (Listen for SOS)
        connectionsClient?.startDiscovery(SERVICE_ID, object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                val payload = info.endpointName
                if (payload.startsWith("SOS|")) {
                    handleIncomingSos(context, payload)
                }
            }
            override fun onEndpointLost(endpointId: String) {}
        }, discoveryOptions)

        // 2. Advertising (Be visible to others)
        connectionsClient?.startAdvertising(userName, SERVICE_ID, object : ConnectionLifecycleCallback {
            override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
                // We don't need connection for just broadcasting, so we accept and disconnect
                connectionsClient?.acceptConnection(endpointId, object : PayloadCallback() {
                    override fun onPayloadReceived(endpointId: String, payload: Payload) {}
                    override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
                })
            }
            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {}
            override fun onDisconnected(endpointId: String) {}
        }, advertisingOptions)
    }

    // Trigger SOS
    fun broadcastSos(context: Context, userName: String, location: Location?) {
        val client = connectionsClient ?: Nearby.getConnectionsClient(context)
        connectionsClient = client
        
        val msgId = UUID.randomUUID().toString().take(6)
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val lat = location?.latitude ?: 0.0
        val lon = location?.longitude ?: 0.0
        
        // Payload Format: SOS|UniqueID|Name|Lat,Lon|Battery%
        val sosPayload = "SOS|$msgId|$userName|$lat,$lon|$battery%"
        
        processedMessages.add(msgId) // Mark as seen to prevent loops

        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        
        // Stop old advertising and start with SOS payload in the name
        client.stopAdvertising()
        client.startAdvertising(sosPayload, SERVICE_ID, null, options)
        
        // Send SMS Fallback
        sendSmsFallback(context, lat, lon)
        
        Toast.makeText(context, "SOS Broadcasted! Stay where you are.", Toast.LENGTH_LONG).show()
    }

    private fun handleIncomingSos(context: Context, payload: String) {
        try {
            val parts = payload.split("|")
            if (parts.size == 5) {
                val msgId = parts[1]
                val victimName = parts[2]
                val coords = parts[3].split(",")
                val lat = coords[0].toDouble()
                val lon = coords[1].toDouble()
                val battery = parts[4]

                // Loop Prevention
                if (processedMessages.contains(msgId)) return
                processedMessages.add(msgId)

                // Show Alert to User
                (context as? MainActivity)?.runOnUiThread {
                    Toast.makeText(context, "SOS DETECTED from $victimName! Nearby.", Toast.LENGTH_LONG).show()
                    // You can add a Dialog or Alarm here later
                }

                // Relay Logic
                if (!isInternetAvailable(context)) {
                    Log.d(TAG, "No internet. Relaying SOS: $msgId")
                    // Re-broadcast the same payload to hop to the next phone
                    connectionsClient?.stopAdvertising()
                    val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
                    connectionsClient?.startAdvertising(payload, SERVICE_ID, null, options)
                } else {
                    Log.d(TAG, "Internet available. Uploading to Cloud...")
                    // TODO: Upload to Firebase/Server
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing SOS", e)
        }
    }

    private fun sendSmsFallback(context: Context, lat: Double, lon: Double) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                
                // TODO: Yahan apna Emergency Contact Number daalna
                val emergencyNumber = "+919999999999" 
                val mapsLink = "https://maps.google.com/?q=$lat,$lon"
                val message = "EMERGENCY SOS from ProGuide! I need help. My location: $mapsLink"
                
                smsManager.sendTextMessage(emergencyNumber, null, message, null, null)
                Log.d(TAG, "SMS Sent successfully")
            } catch (e: Exception) {
                Log.e(TAG, "SMS Failed", e)
            }
        }
    }

    private fun isInternetAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    
    fun stopRadar() {
        connectionsClient?.stopAdvertising()
        connectionsClient?.stopDiscovery()
    }
}
