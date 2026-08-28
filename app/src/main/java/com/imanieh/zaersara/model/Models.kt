package com.imanieh.zaersara.model

data class UnitItem(val id: String, val name: String, val capacity: Int, val kind: String)

data class Person(
    val id: String = "",
    val firstName: String,
    val lastName: String,
    val nationalId: String,
    val phone: String = ""
)

data class PersonLookup(
    val firstName: String,
    val lastName: String,
    val nationalId: String,
    val visitCount: Int = 0,
    val lastDeparture: String = ""
)

data class Reservation(
    val id: String = "",
    val title: String,
    val unitId: String,
    val unitName: String = "",
    val startDate: String,
    val endDate: String,
    val guestCount: Int,
    val reservationType: String,
    val leaderName: String = "",
    val leaderPhone: String = "",
    val isPaid: Boolean = false,
    val amount: Long = 0,
    val paymentStatus: String = "رایگان",
    val notes: String = ""
)

data class GuestInput(val firstName: String, val lastName: String, val nationalId: String)
