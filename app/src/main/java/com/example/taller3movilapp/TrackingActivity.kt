package com.example.taller3movilapp

import android.Manifest
import android.location.Location
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.single.PermissionListener
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class TrackingActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var tvDistance: TextView
    private lateinit var auth: FirebaseAuth

    private var targetUserId: String = ""
    private var targetUserName: String = ""
    private var currentUserLocation: Location? = null
    private var targetUserMarker: Marker? = null
    private var currentUserMarker: Marker? = null
    private var targetUserLatitude: Double = 0.0
    private var targetUserLongitude: Double = 0.0
    private var isFirstLocationUpdate = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configurar OSMDroid ANTES de setContentView
        Configuration.getInstance().load(this, getSharedPreferences("osm_prefs", MODE_PRIVATE))

        setContentView(R.layout.activity_tracking)

        // Obtener datos del intent
        targetUserId = intent.getStringExtra("TARGET_USER_ID") ?: ""
        targetUserName = intent.getStringExtra("TARGET_USER_NAME") ?: "Usuario"

        // DIAGNÓSTICO: Ver qué ID estamos recibiendo
        println("TrackingActivity - ID recibido: $targetUserId")
        println("TrackingActivity - Nombre recibido: $targetUserName")

        auth = FirebaseAuth.getInstance()

        // Configurar toolbar
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Siguiendo a $targetUserName"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mapView = findViewById(R.id.mapView)
        tvDistance = findViewById(R.id.tvDistance)

        // Configurar mapa BÁSICO - sin ubicación por defecto
        setupMap()

        // INMEDIATAMENTE cargar la ubicación del objetivo desde Firebase
        loadTargetUserLocation()

        // Luego obtener nuestra ubicación actual
        requestUserLocation()
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // El mapa se centrará automáticamente cuando carguemos la ubicación real

        // Solo configuraciones básicas del mapa
        mapView.setBuiltInZoomControls(true)
        mapView.setMultiTouchControls(true)

        // Zoom inicial genérico, será ajustado con la ubicación real
        mapView.controller.setZoom(15.0)
    }

    private fun loadTargetUserLocation() {
        if (targetUserId.isEmpty()) {
            Toast.makeText(this, "Error: ID de usuario no válido", Toast.LENGTH_SHORT).show()
            return
        }

        val targetUserRef = FirebaseDatabase.getInstance().getReference("users").child(targetUserId)

        // DIAGNÓSTICO: Ver qué estamos buscando en Firebase
        println("🔍 Buscando usuario en Firebase: users/$targetUserId")

        targetUserRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // DIAGNÓSTICO: Ver qué devuelve Firebase
                println("🔍 Firebase devolvió: ${snapshot.value}")

                if (snapshot.exists()) {
                    val user = snapshot.getValue(User::class.java)
                    println("🔍 Usuario parseado: $user")

                    if (user != null) {
                        println("🔍 Latitud: ${user.latitude}, Longitud: ${user.longitude}")

                        if (user.latitude != null && user.longitude != null) {
                            targetUserLatitude = user.latitude!!
                            targetUserLongitude = user.longitude!!

                            // CENTRAR EL MAPA EXCLUSIVAMENTE EN LA UBICACIÓN REAL DEL OBJETIVO
                            val targetPoint = GeoPoint(targetUserLatitude, targetUserLongitude)
                            mapView.controller.setCenter(targetPoint)
                            mapView.controller.setZoom(15.0)

                            // Crear marcador del objetivo
                            updateTargetUserLocation(targetUserLatitude, targetUserLongitude)

                            Toast.makeText(
                                this@TrackingActivity,
                                "Ubicación cargada: ${"%.4f".format(targetUserLatitude)}, ${"%.4f".format(targetUserLongitude)}",
                                Toast.LENGTH_LONG
                            ).show()

                            // Iniciar seguimiento en tiempo real
                            startRealTimeTracking()

                        } else {
                            Toast.makeText(
                                this@TrackingActivity,
                                "El usuario no tiene coordenadas de ubicación registradas",
                                Toast.LENGTH_LONG
                            ).show()
                            println("Usuario sin coordenadas: latitude=${user.latitude}, longitude=${user.longitude}")

                            // Si no hay coordenadas, usar una ubicación genérica como fallback
                            val fallbackLocation = GeoPoint(40.4168, -3.7038) // Madrid como fallback
                            mapView.controller.setCenter(fallbackLocation)
                            mapView.controller.setZoom(10.0)
                        }
                    } else {
                        Toast.makeText(
                            this@TrackingActivity,
                            "Error: No se pudo leer los datos del usuario",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this@TrackingActivity,
                        "Error: Usuario no encontrado en la base de datos",
                        Toast.LENGTH_LONG
                    ).show()
                    println("Usuario no encontrado en Firebase: $targetUserId")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@TrackingActivity,
                    "Error de conexión con Firebase",
                    Toast.LENGTH_SHORT
                ).show()
                println("Error Firebase: ${error.message}")
            }
        })
    }

    private fun requestUserLocation() {
        Dexter.withContext(this)
            .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object : PermissionListener {
                override fun onPermissionGranted(response: PermissionGrantedResponse?) {
                    val provider = LocationProvider(this@TrackingActivity)
                    provider.getLocation { location ->
                        currentUserLocation = location
                        if (location != null) {
                            addCurrentUserMarker(location)
                            // Calcular distancia si ya tenemos la ubicación del objetivo
                            if (targetUserLatitude != 0.0 && targetUserLongitude != 0.0) {
                                calculateAndUpdateDistance(location)
                                updateMapView() // Ajustar vista para mostrar ambos marcadores
                            }
                        } else {
                            Toast.makeText(
                                this@TrackingActivity,
                                "No se pudo obtener tu ubicación actual",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                override fun onPermissionDenied(response: PermissionDeniedResponse?) {
                    Toast.makeText(
                        this@TrackingActivity,
                        "Permiso de ubicación denegado. No podrás ver la distancia en tiempo real.",
                        Toast.LENGTH_LONG
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

    private fun startRealTimeTracking() {
        // Escuchar cambios en tiempo real del usuario objetivo
        val targetUserRef = FirebaseDatabase.getInstance().getReference("users").child(targetUserId)

        targetUserRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(User::class.java)
                if (user != null && user.latitude != null && user.longitude != null) {
                    val newLat = user.latitude!!
                    val newLon = user.longitude!!

                    // Actualizar ubicación del objetivo
                    targetUserLatitude = newLat
                    targetUserLongitude = newLon
                    updateTargetUserLocation(newLat, newLon)

                    // Calcular distancia si tenemos nuestra ubicación actual
                    currentUserLocation?.let { currentLoc ->
                        calculateAndUpdateDistance(currentLoc)
                        updateMapView()
                    }

                    // DIAGNÓSTICO: Mostrar actualización en tiempo real
                    println("Actualización en tiempo real: $newLat, $newLon")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Error silencioso para no molestar con muchos Toast
                println("Error en tiempo real: ${error.message}")
            }
        })
    }

    private fun updateTargetUserLocation(lat: Double, lon: Double) {
        val targetPoint = GeoPoint(lat, lon)

        if (targetUserMarker == null) {
            // Crear marcador si no existe
            targetUserMarker = Marker(mapView).apply {
                position = targetPoint
                title = targetUserName
                snippet = "Usuario seguido - Ubicación en tiempo real"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = resources.getDrawable(android.R.drawable.star_big_on, theme)
                mapView.overlays.add(this)
            }

            // Solo centrar la primera vez
            if (isFirstLocationUpdate) {
                mapView.controller.setCenter(targetPoint)
                mapView.controller.setZoom(15.0)
                isFirstLocationUpdate = false
            }
        } else {
            // Actualizar posición del marcador existente
            targetUserMarker?.position = targetPoint
        }

        mapView.invalidate() // Refrescar mapa
    }

    private fun addCurrentUserMarker(location: Location) {
        val currentPoint = GeoPoint(location.latitude, location.longitude)

        if (currentUserMarker == null) {
            currentUserMarker = Marker(mapView).apply {
                position = currentPoint
                title = "Mi ubicación"
                snippet = "Tú estás aquí"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = resources.getDrawable(android.R.drawable.ic_menu_mylocation, theme)
                mapView.overlays.add(this)
            }
        } else {
            currentUserMarker?.position = currentPoint
        }

        mapView.invalidate()
    }

    private fun calculateAndUpdateDistance(currentLoc: Location) {
        val targetLoc = Location("target").apply {
            latitude = targetUserLatitude
            longitude = targetUserLongitude
        }

        val distance = currentLoc.distanceTo(targetLoc)
        updateDistance(distance)

        // DIAGNÓSTICO: Mostrar distancia calculada
        println("📏 Distancia calculada: $distance metros")
    }

    private fun updateDistance(distance: Float) {
        val distanceText = when {
            distance < 1000 -> "${"%.0f".format(distance)} metros"
            else -> "${"%.2f".format(distance / 1000)} km"
        }
        tvDistance.text = "Distancia: $distanceText"
    }

    private fun updateMapView() {
        // Asegurarse de que ambos marcadores estén visibles en el mapa
        val markers = arrayListOf<GeoPoint>()

        currentUserMarker?.position?.let { markers.add(it) }
        targetUserMarker?.position?.let { markers.add(it) }

        if (markers.size == 2) {
            // Calcular el centro entre los dos puntos
            val centerLat = (markers[0].latitude + markers[1].latitude) / 2
            val centerLon = (markers[0].longitude + markers[1].longitude) / 2
            val centerPoint = GeoPoint(centerLat, centerLon)

            // Calcular distancia para ajustar zoom
            val distance = markers[0].distanceToAsDouble(markers[1])
            val zoomLevel = when {
                distance > 20000 -> 9.0
                distance > 10000 -> 10.0
                distance > 5000 -> 11.0
                distance > 2000 -> 12.0
                distance > 1000 -> 13.0
                else -> 15.0
            }

            mapView.controller.setCenter(centerPoint)
            mapView.controller.setZoom(zoomLevel)

            // DIAGNÓSTICO: Mostrar ajuste de mapa
            println("Mapa ajustado - Centro: $centerLat, $centerLon - Zoom: $zoomLevel")
        }

        mapView.invalidate()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}