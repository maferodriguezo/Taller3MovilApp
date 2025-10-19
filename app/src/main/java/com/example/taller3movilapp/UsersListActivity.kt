package com.example.taller3movilapp

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class UsersListActivity : AppCompatActivity() {

    private lateinit var rvUsers: RecyclerView
    private lateinit var tvEmptyList: TextView
    private lateinit var usersAdapter: UsersAdapter
    private lateinit var auth: FirebaseAuth
    private val currentUserId get() = auth.currentUser?.uid

    // Map para almacenar usuarios con sus IDs reales
    private val usersMap = mutableMapOf<String, User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_users_list)

        auth = FirebaseAuth.getInstance()

        // Configurar toolbar
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        rvUsers = findViewById(R.id.rvUsers)
        tvEmptyList = findViewById(R.id.tvEmptyList)

        // Configurar RecyclerView
        usersAdapter = UsersAdapter(emptyList()) { user ->
            // Buscar el ID real del usuario en el mapa
            val userId = usersMap.entries.find { it.value == user }?.key
            if (userId != null) {
                val intent = Intent(this, TrackingActivity::class.java)
                intent.putExtra("TARGET_USER_ID", userId) // ID de Firebase
                intent.putExtra("TARGET_USER_NAME", "${user.firstName} ${user.lastName}")
                startActivity(intent)
            } else {
                Toast.makeText(this, "Error: No se pudo encontrar el usuario", Toast.LENGTH_SHORT).show()
            }
        }

        rvUsers.layoutManager = LinearLayoutManager(this)
        rvUsers.adapter = usersAdapter

        // Cargar usuarios disponibles
        loadAvailableUsers()
    }

    private fun loadAvailableUsers() {
        val usersRef = FirebaseDatabase.getInstance().getReference("users")

        usersRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val availableUsers = mutableListOf<User>()
                usersMap.clear()

                snapshot.children.forEach { userSnapshot ->
                    val user = userSnapshot.getValue(User::class.java)
                    user?.let {
                        // Filtrar: solo usuarios disponibles y excluir al usuario actual
                        if (it.status == "available" && userSnapshot.key != currentUserId) {
                            // Guardar usuario con su ID
                            usersMap[userSnapshot.key!!] = it
                            availableUsers.add(it)
                        }
                    }
                }

                // Actualizar adapter
                usersAdapter.updateUsers(availableUsers)

                // Mostrar mensaje si no hay usuarios
                if (availableUsers.isEmpty()) {
                    tvEmptyList.visibility = TextView.VISIBLE
                    rvUsers.visibility = TextView.GONE
                } else {
                    tvEmptyList.visibility = TextView.GONE
                    rvUsers.visibility = TextView.VISIBLE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@UsersListActivity, "Error cargando usuarios", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}