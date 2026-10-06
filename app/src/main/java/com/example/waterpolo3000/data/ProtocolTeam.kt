package com.example.waterpolo3000.data

data class ProtocolTeam(
    val number         : String,
    val cap            : String,
    val playerFirstName: String,
    val playerLastName : String,
    val playerId       : String,
    val P1             : String,
    val P2             : String,
    val P3             : String,
    val tore           : String
) {
    val playerDisplayName: String
        get() {
            val idPart = playerId.takeIf { it.isNotBlank() && it != "0" }
            return listOf(playerFirstName, playerLastName, idPart)
                .filter { !it.isNullOrBlank() }
                .joinToString(" ")
        }
}