package com.example.taller3movilapp

import android.Manifest
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.single.PermissionListener

class HomeActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, getSharedPreferences("osm_prefs", MODE_PRIVATE))
        setContentView(R.layout.activity_home)

        auth = FirebaseAuth.getInstance()
        mapView = findViewById(R.id.mapView)

        // Configuración básica del mapa
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(13.5)
        mapView.controller.setCenter(GeoPoint(4.65, -74.08)) // Bogotá

        // Cargar los puntos desde el JSON local
        loadLocationsFromJSON()

        // Mostrar ubicación actual
        requestUserLocation()

        // Cerrar sesión
        findViewById<android.widget.Button>(R.id.btnLogout).setOnClickListener {
            auth.signOut()
            finish()
        }
    }

    private fun loadLocationsFromJSON() {
        try {
            val inputStream = assets.open("locations.json")
            val jsonText = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonText)
            val locationsArray = jsonObject.getJSONArray("locations") // 👈 nombre correcto del array

            if (locationsArray.length() == 0) {
                Toast.makeText(this, "No se encontraron puntos en el archivo JSON.", Toast.LENGTH_SHORT).show()
                return
            }

            for (i in 0 until locationsArray.length()) {
                val loc = locationsArray.getJSONObject(i)
                val name = loc.getString("name")
                val lat = loc.getDouble("latitude")
                val lon = loc.getDouble("longitude")

                val marker = Marker(mapView)
                marker.position = GeoPoint(lat, lon)
                marker.title = name
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                marker.icon = resources.getDrawable(android.R.drawable.star_big_on, theme)
                mapView.overlays.add(marker)
            }

            Toast.makeText(this, "Se cargaron ${locationsArray.length()} puntos de interés.", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error leyendo locations.json", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }


    private fun requestUserLocation() {
        Dexter.withContext(this)
            .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object : PermissionListener {
                override fun onPermissionGranted(response: PermissionGrantedResponse?) {
                    val provider = LocationProvider(this@HomeActivity)
                    provider.getLocation { loc ->
                        if (loc != null) {
                            addUserMarker(loc)
                        } else {
                            Toast.makeText(
                                this@HomeActivity,
                                "No se pudo obtener tu ubicación actual",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                override fun onPermissionDenied(response: PermissionDeniedResponse?) {
                    Toast.makeText(
                        this@HomeActivity,
                        "Permiso de ubicación denegado",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onPermissionRationaleShouldBeShown(
                    p0: com.karumi.dexter.listener.PermissionRequest?,
                    token: PermissionToken?
                ) {
                    token?.continuePermissionRequest()
                }
            }).check()
    }

    private fun addUserMarker(location: Location) {
        val geoPoint = GeoPoint(location.latitude, location.longitude)
        val marker = Marker(mapView)
        marker.position = geoPoint
        marker.title = "Mi ubicación actual"
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.icon = resources.getDrawable(android.R.drawable.ic_menu_mylocation, theme)
        mapView.overlays.add(marker)
        mapView.controller.setCenter(geoPoint)
    }
}
