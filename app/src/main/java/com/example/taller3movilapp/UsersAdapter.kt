package com.example.taller3movilapp

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide

class UsersAdapter(
    private val context: Context,
    private val users: MutableList<User>,
    private val onTrackUser: (User) -> Unit
) : BaseAdapter() {

    override fun getCount(): Int = users.size

    override fun getItem(position: Int): Any = users[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View
        val holder: ViewHolder

        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(R.layout.user_item, parent, false)
            holder = ViewHolder(view)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        val user = users[position]

        // Configurar nombre
        val fullName = "${user.firstName ?: ""} ${user.lastName ?: ""}".trim()
        holder.tvUserName.text = if (fullName.isNotEmpty()) fullName else "Usuario Sin Nombre"
        holder.tvUserEmail.text = user.email ?: "Sin email"

        // Cargar imagen con Glide
        if (!user.imageUrl.isNullOrEmpty()) {
            Glide.with(context)
                .load(user.imageUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .circleCrop()
                .into(holder.ivUserImage)
        } else {
            holder.ivUserImage.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        // Evento onClick del botón - uno por cada línea
        holder.btnTrackUser.setOnClickListener {
            onTrackUser(user)
        }

        return view
    }

    private class ViewHolder(view: View) {
        val ivUserImage: ImageView = view.findViewById(R.id.ivUserImage)
        val tvUserName: TextView = view.findViewById(R.id.tvUserName)
        val tvUserEmail: TextView = view.findViewById(R.id.tvUserEmail)
        val btnTrackUser: Button = view.findViewById(R.id.btnTrackUser)
    }

    fun updateUsers(newUsers: List<User>) {
        users.clear()
        users.addAll(newUsers)
        notifyDataSetChanged()
    }
}