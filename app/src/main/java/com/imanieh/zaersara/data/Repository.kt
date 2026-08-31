package com.imanieh.zaersara.data

import com.imanieh.zaersara.model.*
import org.json.JSONArray
import org.json.JSONObject

class Repository(private val api: SupabaseRest) {
    fun units(): List<UnitItem> {
        val a = api.get("/rest/v1/units?select=id,name,capacity,kind,unit_group,capacity_configured&active=eq.true&order=sort_order")
        return (0 until a.length()).map { i ->
            a.getJSONObject(i).let {
                UnitItem(
                    id = it.getString("id"), name = it.getString("name"), capacity = it.optInt("capacity"),
                    kind = it.optString("kind"), unitGroup = it.optString("unit_group", "original"),
                    capacityConfigured = it.optBoolean("capacity_configured", true)
                )
            }
        }
    }

    fun reservations(): List<Reservation> {
        val a = api.get("/rest/v1/reservations?select=id,booking_group_id,title,unit_id,start_date,end_date,guest_count,reservation_type,primary_last_name,leader_name,leader_phone,is_paid,amount,payment_status,notes,check_in_at,check_out_at,units(name,unit_group)&status=neq.cancelled&order=start_date")
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i); val u = o.optJSONObject("units")
            Reservation(
                id = o.getString("id"), bookingGroupId = o.optString("booking_group_id"), title = o.optString("title"),
                unitId = o.getString("unit_id"), unitName = u?.optString("name") ?: "", unitGroup = u?.optString("unit_group") ?: "",
                startDate = o.getString("start_date"), endDate = o.getString("end_date"), guestCount = o.getInt("guest_count"),
                reservationType = o.optString("reservation_type"), primaryLastName = o.optString("primary_last_name"),
                leaderName = o.optString("leader_name"), leaderPhone = o.optString("leader_phone"),
                isPaid = o.optBoolean("is_paid"), amount = o.optLong("amount"), paymentStatus = o.optString("payment_status"), notes = o.optString("notes"),
                checkInAt = o.optString("check_in_at"), checkOutAt = o.optString("check_out_at")
            )
        }
    }

    fun createBooking(title: String, startDate: String, endDate: String, reservationType: String, leaderName: String, leaderPhone: String,
                      isPaid: Boolean, amount: Long, paymentStatus: String, notes: String, plan: List<PlanUnit>, guests: List<GuestInput>) {
        val units = JSONArray(); plan.forEach {
            val ug = JSONArray(); it.guests.forEach { g -> ug.put(JSONObject().put("first_name", g.firstName).put("last_name", g.lastName).put("national_id", g.nationalId.ifBlank { JSONObject.NULL }).put("phone", g.phone)) }
            units.put(JSONObject().put("unit_id", it.unitId).put("guest_count", it.guestCount).put("family_last_name", it.familyLastName).put("guests", ug))
        }
        val ga = JSONArray(); guests.forEach {
            ga.put(JSONObject().put("first_name", it.firstName).put("last_name", it.lastName)
                .put("national_id", it.nationalId.ifBlank { JSONObject.NULL }).put("phone", it.phone))
        }
        val body = JSONObject().put("p_title", title).put("p_start_date", startDate).put("p_end_date", endDate)
            .put("p_reservation_type", reservationType).put("p_leader_name", leaderName).put("p_leader_phone", leaderPhone)
            .put("p_is_paid", isPaid).put("p_amount", amount).put("p_payment_status", paymentStatus).put("p_notes", notes)
            .put("p_units", units).put("p_guests", ga)
        api.post("/rest/v1/rpc/create_booking_atomic", body)
    }

    fun updateBookingMeta(groupId: String, title: String, startDate: String, endDate: String, leaderName: String, leaderPhone: String,
                          isPaid: Boolean, amount: Long, paymentStatus: String, notes: String) {
        val body = JSONObject().put("p_booking_group_id", groupId).put("p_title", title)
            .put("p_start_date", startDate).put("p_end_date", endDate).put("p_leader_name", leaderName)
            .put("p_leader_phone", leaderPhone).put("p_is_paid", isPaid).put("p_amount", amount)
            .put("p_payment_status", paymentStatus).put("p_notes", notes)
        api.post("/rest/v1/rpc/update_booking_meta", body)
    }

    fun updateBookingUnit(reservationId: String, unitId: String, guestCount: Int, familyLastName: String) {
        api.post("/rest/v1/rpc/update_booking_unit", JSONObject().put("p_reservation_id", reservationId)
            .put("p_unit_id", unitId).put("p_guest_count", guestCount).put("p_family_last_name", familyLastName))
    }

    fun addBookingUnit(groupId: String, unitId: String, guestCount: Int, familyLastName: String) {
        api.post("/rest/v1/rpc/add_booking_unit", JSONObject().put("p_booking_group_id", groupId)
            .put("p_unit_id", unitId).put("p_guest_count", guestCount).put("p_family_last_name", familyLastName))
    }

    fun removeBookingUnit(reservationId: String) {
        api.post("/rest/v1/rpc/remove_booking_unit", JSONObject().put("p_reservation_id", reservationId))
    }

    fun cancelBooking(groupId: String) {
        api.post("/rest/v1/rpc/cancel_booking", JSONObject().put("p_booking_group_id", groupId))
    }

    fun markBookingStatus(groupId: String, action: String) {
        api.post("/rest/v1/rpc/mark_booking_status", JSONObject().put("p_booking_group_id", groupId).put("p_action", action))
    }

    fun reservationGuests(reservationId: String): List<GuestInput> {
        val body = JSONObject().put("p_reservation_id", reservationId)
        val a = api.post("/rest/v1/rpc/get_reservation_guests", body)
        val items = a.optJSONArray("items") ?: a.optJSONObject("get_reservation_guests")?.optJSONArray("items") ?: JSONArray()
        return (0 until items.length()).map { i ->
            val o = items.getJSONObject(i)
            GuestInput(o.optString("first_name"), o.optString("last_name"), o.optString("national_id"), o.optString("phone"))
        }
    }

    fun setReservationGuests(reservationId: String, guests: List<GuestInput>) {
        val a = JSONArray()
        guests.forEach { g -> a.put(JSONObject().put("first_name", g.firstName).put("last_name", g.lastName).put("national_id", g.nationalId.ifBlank { JSONObject.NULL }).put("phone", g.phone)) }
        api.post("/rest/v1/rpc/set_reservation_guests", JSONObject().put("p_reservation_id", reservationId).put("p_guests", a))
    }

    fun lookupPerson(nationalId: String): PersonLookup? = searchPeople("national", nationalId).firstOrNull()

    fun searchPeople(mode: String, rawQuery: String): List<PersonLookup> {
        val q = rawQuery.trim(); if (q.isBlank()) return emptyList()
        val body = JSONObject().put("p_mode", mode).put("p_query", q)
        val a = api.post("/rest/v1/rpc/search_person_history", body)
        val items = a.optJSONArray("items") ?: a.optJSONObject("search_person_history")?.optJSONArray("items") ?: JSONArray()
        return (0 until items.length()).map { i ->
            val o = items.getJSONObject(i)
            PersonLookup(
                id = o.optString("id"), firstName = o.optString("first_name"), lastName = o.optString("last_name"),
                nationalId = o.optString("national_id"), phone = o.optString("phone"), visitCount = o.optInt("visit_count"),
                lastDeparture = o.optString("last_departure")
            )
        }
    }

    fun updateUnitCapacity(unitId: String, capacity: Int) {
        api.patch("/rest/v1/units?id=eq.$unitId", JSONObject().put("capacity", capacity).put("capacity_configured", true))
    }
}
