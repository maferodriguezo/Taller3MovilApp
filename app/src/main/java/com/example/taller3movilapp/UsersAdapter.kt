package com.example.taller3movilapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class UsersAdapter(
    private var users: List<User>,
    private val onTrackUser: (User) -> Unit
) : RecyclerView.Adapter<UsersAdapter.UserViewHolder>() {

    // Clase ViewHolder
    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivUserImage: ImageView = itemView.findViewById(R.id.ivUserImage)
        val tvUserName: TextView = itemView.findViewById(R.id.tvUserName)
        val tvUserEmail: TextView = itemView.findViewById(R.id.tvUserEmail)
        val btnTrackUser: Button = itemView.findViewById(R.id.btnTrackUser)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.user_item, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]

        // Configurar datos del usuario - forma correcta sin concatenación
        val userName = "${user.firstName ?: ""} ${user.lastName ?: ""}".trim()
        holder.tvUserName.text = if (userName.isNotEmpty()) userName else "Usuario Sin Nombre"
        holder.tvUserEmail.text = user.email ?: "Sin email"

        // Cargar imagen - versión simple sin Glide
        // Puedes usar Picasso o cargar Glide después
        holder.ivUserImage.setImageResource(android.R.drawable.ic_menu_gallery)

        // Configurar botón de seguimiento
        holder.btnTrackUser.setOnClickListener {
            onTrackUser(user)
        }
    }

    override fun getItemCount(): Int = users.size

    // Metodo para actualizar la lista
    fun updateUsers(newUsers: List<User>) {
        users = newUsers
        notifyDataSetChanged() // Por ahora usamos este, luego optimizamos
    }
}