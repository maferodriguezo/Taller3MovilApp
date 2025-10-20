package com.example.taller3movilapp

import android.Manifest
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
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
    private var currentStatus: String = "disconnected"
    private lateinit var statusMenuItem: MenuItem
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    private lateinit var tvStatus: TextView

    private var availabilityListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, getSharedPreferences("osm_prefs", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_home)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Mapa de la App"

        tvStatus = findViewById(R.id.tvStatus)

        auth = FirebaseAuth.getInstance()
        mapView = findViewById(R.id.mapView)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(13.5)
        mapView.controller.setCenter(GeoPoint(4.65, -74.08))

        loadUserStatus()
        loadLocationsFromJSON()
        requestUserLocation()

        startAvailabilityListener()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        statusMenuItem = menu.findItem(R.id.menu_status)
        updateStatusMenuTitle()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_status -> {
                toggleStatus()
                true
            }
            R.id.menu_users -> {
                val intent = Intent(this, UsersListActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.menu_logout -> {
                logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadUserStatus() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            FirebaseDatabase.getInstance().getReference("users")
                .child(userId)
                .child("status")
                .get()
                .addOnSuccessListener { snapshot ->
                    snapshot.getValue(String::class.java)?.let { status ->
                        currentStatus = status
                        updateStatusDisplay()
                    }
                }
        }
    }

    private fun toggleStatus() {
        currentStatus = if (currentStatus == "disconnected") {
            "available"
        } else {
            "disconnected"
        }

        updateStatusDisplay()
        updateStatusInDatabase()

        val statusMessage = if (currentStatus == "available") {
            "Ahora estás disponible"
        } else {
            "Ahora estás desconectado"
        }

        Toast.makeText(this, statusMessage, Toast.LENGTH_SHORT).show()
    }

    private fun updateStatusDisplay() {
        if (currentStatus == "available") {
            tvStatus.text = "Disponible"
            tvStatus.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            tvStatus.setCompoundDrawablesWithIntrinsicBounds(
                ContextCompat.getDrawable(this, android.R.drawable.presence_online),
                null, null, null
            )
        } else {
            tvStatus.text = "Desconectado"
            tvStatus.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            tvStatus.setCompoundDrawablesWithIntrinsicBounds(
                ContextCompat.getDrawable(this, android.R.drawable.presence_offline),
                null, null, null
            )
        }
    }

    private fun updateStatusMenuTitle() {
        val statusText = if (currentStatus == "available") {
            "Estado: Disponible"
        } else {
            "Estado: Desconectado"
        }
        statusMenuItem.title = statusText
    }

    private fun updateStatusInDatabase() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)
            userRef.child("status").setValue(currentStatus)
                .addOnSuccessListener {
                    updateStatusMenuTitle()
                }
        }
    }

    private fun logout() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            FirebaseDatabase.getInstance().getReference("users")
                .child(userId)
                .child("status")
                .setValue("disconnected")
                .addOnCompleteListener {
                    auth.signOut()
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
        } else {
            auth.signOut()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun loadLocationsFromJSON() {
        try {
            val inputStream = assets.open("locations.json")
            val jsonText = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonText)
            val locationsArray = jsonObject.getJSONArray("locationsArray")

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

    private fun startAvailabilityListener() {
        val currentUserId = auth.currentUser?.uid ?: return
        val usersRef = FirebaseDatabase.getInstance().getReference("users")

        val lastStatus = mutableMapOf<String, String?>()

        availabilityListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (userSnapshot in snapshot.children) {
                    val userId = userSnapshot.key ?: continue
                    val user = userSnapshot.getValue(User::class.java) ?: continue

                    val oldStatus = lastStatus[userId]
                    val newStatus = user.status
                    lastStatus[userId] = newStatus

                    if (userId == currentUserId) continue

                    if (newStatus == "available" && oldStatus != "available") {
                        val fullName = listOfNotNull(user.firstName, user.lastName)
                            .joinToString(" ")
                            .ifEmpty { user.email ?: "Usuario" }
                        Toast.makeText(
                            this@HomeActivity,
                            "$fullName se conectó",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    if (newStatus != "available" && oldStatus == "available") {
                        val fullName = listOfNotNull(user.firstName, user.lastName)
                            .joinToString(" ")
                            .ifEmpty { user.email ?: "Usuario" }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@HomeActivity, "Error escuchando usuarios: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }

        usersRef.addValueEventListener(availabilityListener!!)
    }

    private fun stopAvailabilityListener() {
        val usersRef = FirebaseDatabase.getInstance().getReference("users")
        availabilityListener?.let { usersRef.removeEventListener(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAvailabilityListener()
    }
}
