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
        val a = api.get("/rest/v1/reservations?select=id,booking_group_id,title,unit_id,start_date,end_date,guest_count,reservation_type,leader_name,leader_phone,is_paid,amount,payment_status,notes,units(name,unit_group)&status=neq.cancelled&order=start_date")
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i); val u = o.optJSONObject("units")
            Reservation(
                id = o.getString("id"), bookingGroupId = o.optString("booking_group_id"), title = o.optString("title"),
                unitId = o.getString("unit_id"), unitName = u?.optString("name") ?: "", unitGroup = u?.optString("unit_group") ?: "",
                startDate = o.getString("start_date"), endDate = o.getString("end_date"), guestCount = o.getInt("guest_count"),
                reservationType = o.optString("reservation_type"), leaderName = o.optString("leader_name"), leaderPhone = o.optString("leader_phone"),
                isPaid = o.optBoolean("is_paid"), amount = o.optLong("amount"), paymentStatus = o.optString("payment_status"), notes = o.optString("notes")
            )
        }
    }

    fun createBooking(title: String, startDate: String, endDate: String, reservationType: String, leaderName: String, leaderPhone: String,
                      isPaid: Boolean, amount: Long, paymentStatus: String, notes: String, plan: List<PlanUnit>, guests: List<GuestInput>) {
        val units = JSONArray(); plan.forEach { units.put(JSONObject().put("unit_id", it.unitId).put("guest_count", it.guestCount)) }
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

    fun lookupPerson(nationalId: String): PersonLookup? = searchPeople("national", nationalId).firstOrNull()

    fun searchPeople(mode: String, rawQuery: String): List<PersonLookup> {
        val q = rawQuery.trim(); if (q.isBlank()) return emptyList()
        val body = JSONObject().put("p_mode", mode).put("p_query", q)
        val a = api.post("/rest/v1/rpc/search_person_history", body)
        // RPC can be returned as object only by post(); use fallback GET-like RPC response parser is not available.
        // The function returns {items:[...]} for simple Android parsing.
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
