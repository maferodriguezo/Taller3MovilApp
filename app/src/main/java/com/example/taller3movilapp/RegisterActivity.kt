package com.example.taller3movilapp

import android.Manifest
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.taller3movilapp.databinding.ActivityRegisterBinding
import com.github.dhaval2404.imagepicker.ImagePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.single.PermissionListener

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private var imageUri: Uri? = null
    private var latitude: Double? = null
    private var longitude: Double? = null

    // Nuevo metodo moderno para recibir la imagen sin que se reinicie la Activity
    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                imageUri = result.data?.data
                binding.ivProfilePreview.setImageURI(imageUri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // --- Botón de ubicación ---
        binding.btnGetLocation.setOnClickListener {
            Dexter.withContext(this)
                .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(object : PermissionListener {

                    override fun onPermissionGranted(response: PermissionGrantedResponse?) {
                        val locationProvider = LocationProvider(this@RegisterActivity)
                        locationProvider.getLocation { loc ->
                            if (loc != null) {
                                latitude = loc.latitude
                                longitude = loc.longitude
                                binding.etLatitude.setText(latitude.toString())
                                binding.etLongitude.setText(longitude.toString())

                                Toast.makeText(
                                    this@RegisterActivity,
                                    "Ubicación obtenida correctamente",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    this@RegisterActivity,
                                    "No se pudo obtener la ubicación. Verifica que el GPS esté encendido.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }

                    override fun onPermissionDenied(response: PermissionDeniedResponse?) {
                        Toast.makeText(
                            this@RegisterActivity,
                            "Debes permitir el acceso a la ubicación para continuar.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        p0: com.karumi.dexter.listener.PermissionRequest?,
                        token: PermissionToken?
                    ) {
                        Toast.makeText(
                            this@RegisterActivity,
                            "Es necesario el permiso de ubicación para obtener tus coordenadas.",
                            Toast.LENGTH_SHORT
                        ).show()
                        token?.continuePermissionRequest()
                    }
                }).check()
        }


        //Botón para seleccionar imagen (cámara o galería)
        binding.btnSelectImage.setOnClickListener {
            ImagePicker.with(this@RegisterActivity)
                .compress(1024)
                .maxResultSize(1080, 1080)
                .createIntent { intent ->
                    imagePickerLauncher.launch(intent)
                }
        }

        // --- Ir a login ---
        binding.btnGoToLogin.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // --- Registro ---
        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty() && imageUri != null) {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            uploadData()
                        } else {
                            Toast.makeText(
                                this,
                                "Error: ${task.exception?.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
            } else {
                Toast.makeText(
                    this,
                    "Completa todos los campos e imagen",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun uploadData() {
        val uid = auth.currentUser?.uid ?: return
        val storageRef = FirebaseStorage.getInstance().reference.child("profileImages/$uid.jpg")

        storageRef.putFile(imageUri!!)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    val user = User(
                        firstName = binding.etFirstName.text.toString(),
                        lastName = binding.etLastName.text.toString(),
                        identification = binding.etIdentification.text.toString(),
                        email = binding.etEmail.text.toString(),
                        latitude = latitude,
                        longitude = longitude,
                        imageUrl = uri.toString(),
                        status = "disconnected"
                    )

                    FirebaseDatabase.getInstance().getReference("users").child(uid).setValue(user)
                    Toast.makeText(this, "Usuario registrado correctamente", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                }.addOnFailureListener { e ->  // Agrega esto
                    Toast.makeText(this, "Error al obtener URL: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->  // Agrega esto
                Toast.makeText(this, "Error al subir imagen: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
