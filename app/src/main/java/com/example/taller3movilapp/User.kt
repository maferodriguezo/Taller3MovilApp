package com.example.taller3movilapp

data class User(
    var firstName: String? = null,
    var lastName: String? = null,
    var identification: String? = null,
    var email: String? = null,
    var latitude: Double? = null,
    var longitude: Double? = null,
    var imageUrl: String? = null,
    var status: String? = "disconnected"
)
