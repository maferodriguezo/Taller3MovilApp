package com.example.taller3movilapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class UsersListActivity : AppCompatActivity() {

    private lateinit var listViewUsers: ListView
    private lateinit var llNoUsers: LinearLayout
    private lateinit var usersAdapter: UsersAdapter
    private val availableUsers = mutableListOf<User>()
    private lateinit var auth: FirebaseAuth

    // Map para almacenar usuarios con sus IDs reales
    private val usersMap = mutableMapOf<String, User>()
    private var usersListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_users_list)

        supportActionBar?.apply {
            title = "Usuarios Disponibles"
            setDisplayHomeAsUpEnabled(true)
        }

        initializeViews()
        initializeFirebase()
        setupAdapter()
        loadAvailableUsers()
    }

    private fun initializeViews() {
        listViewUsers = findViewById(R.id.listViewUsers)
        llNoUsers = findViewById(R.id.llNoUsers)
    }

    private fun initializeFirebase() {
        auth = FirebaseAuth.getInstance()
    }

    private fun setupAdapter() {
        usersAdapter = UsersAdapter(
            this,
            availableUsers
        ) { user ->
            // Buscar el ID real del usuario en el mapa
            val userId = usersMap.entries.find { it.value == user }?.key

            if (userId != null) {
                val intent = Intent(this, TrackingActivity::class.java).apply {
                    putExtra("TARGET_USER_ID", userId)
                    putExtra("TARGET_USER_NAME", "${user.firstName} ${user.lastName}")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Error: No se pudo encontrar el usuario", Toast.LENGTH_SHORT).show()
            }
        }

        listViewUsers.adapter = usersAdapter
    }

    private fun loadAvailableUsers() {
        val currentUserId = auth.currentUser?.uid

        val usersRef = FirebaseDatabase.getInstance().getReference("users")

        usersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                availableUsers.clear()
                usersMap.clear()

                for (userSnapshot in snapshot.children) {
                    val userId = userSnapshot.key
                    val user = userSnapshot.getValue(User::class.java)
                    user?.let {
                        if (it.status == "available" && userId != currentUserId) {
                            usersMap[userId!!] = it
                            availableUsers.add(it)
                        }
                    }
                }

                usersAdapter.updateUsers(availableUsers.toList())
                usersAdapter.notifyDataSetChanged()

                if (availableUsers.isEmpty()) {
                    llNoUsers.visibility = View.VISIBLE
                    listViewUsers.visibility = View.GONE
                } else {
                    llNoUsers.visibility = View.GONE
                    listViewUsers.visibility = View.VISIBLE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@UsersListActivity, "Error cargando usuarios: ${error.message}", Toast.LENGTH_SHORT).show()
                llNoUsers.visibility = View.VISIBLE
                listViewUsers.visibility = View.GONE
            }
        }

        usersRef.addValueEventListener(usersListener!!)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        usersListener?.let {
            FirebaseDatabase.getInstance().getReference("users").removeEventListener(it)
        }
    }
}