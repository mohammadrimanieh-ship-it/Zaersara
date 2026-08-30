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
                    id = it.getString("id"),
                    name = it.getString("name"),
                    capacity = it.optInt("capacity"),
                    kind = it.optString("kind"),
                    unitGroup = it.optString("unit_group", "original"),
                    capacityConfigured = it.optBoolean("capacity_configured", true)
                )
            }
        }
    }

    fun reservations(): List<Reservation> {
        val a = api.get("/rest/v1/reservations?select=id,booking_group_id,title,unit_id,start_date,end_date,guest_count,reservation_type,leader_name,leader_phone,is_paid,amount,payment_status,notes,units(name,unit_group)&status=neq.cancelled&order=start_date")
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val u = o.optJSONObject("units")
            Reservation(
                id = o.getString("id"),
                bookingGroupId = o.optString("booking_group_id"),
                title = o.optString("title"),
                unitId = o.getString("unit_id"),
                unitName = u?.optString("name") ?: "",
                unitGroup = u?.optString("unit_group") ?: "",
                startDate = o.getString("start_date"),
                endDate = o.getString("end_date"),
                guestCount = o.getInt("guest_count"),
                reservationType = o.optString("reservation_type"),
                leaderName = o.optString("leader_name"),
                leaderPhone = o.optString("leader_phone"),
                isPaid = o.optBoolean("is_paid"),
                amount = o.optLong("amount"),
                paymentStatus = o.optString("payment_status"),
                notes = o.optString("notes")
            )
        }
    }

    fun createBooking(
        title: String,
        startDate: String,
        endDate: String,
        reservationType: String,
        leaderName: String,
        leaderPhone: String,
        isPaid: Boolean,
        amount: Long,
        paymentStatus: String,
        notes: String,
        plan: List<PlanUnit>,
        guests: List<GuestInput>
    ) {
        val units = JSONArray()
        plan.forEach { units.put(JSONObject().put("unit_id", it.unitId).put("guest_count", it.guestCount)) }
        val ga = JSONArray()
        guests.forEach {
            ga.put(
                JSONObject()
                    .put("first_name", it.firstName)
                    .put("last_name", it.lastName)
                    .put("national_id", it.nationalId.ifBlank { JSONObject.NULL })
            )
        }
        val body = JSONObject()
            .put("p_title", title)
            .put("p_start_date", startDate)
            .put("p_end_date", endDate)
            .put("p_reservation_type", reservationType)
            .put("p_leader_name", leaderName)
            .put("p_leader_phone", leaderPhone)
            .put("p_is_paid", isPaid)
            .put("p_amount", amount)
            .put("p_payment_status", paymentStatus)
            .put("p_notes", notes)
            .put("p_units", units)
            .put("p_guests", ga)
        api.post("/rest/v1/rpc/create_booking_atomic", body)
    }

    fun personHistory(nationalId: String): Pair<String, Int>? {
        val a = api.get("/rest/v1/person_visit_summary?select=full_name,visit_count&national_id=eq.$nationalId")
        if (a.length() == 0) return null
        val o = a.getJSONObject(0)
        return o.optString("full_name") to o.optInt("visit_count")
    }

    fun lookupPerson(nationalId: String): PersonLookup? {
        if (nationalId.isBlank()) return null
        val p = api.get("/rest/v1/persons?select=first_name,last_name,national_id&national_id=eq.$nationalId&limit=1")
        if (p.length() == 0) return null
        val person = p.getJSONObject(0)
        val s = api.get("/rest/v1/person_visit_summary?select=visit_count,last_departure&national_id=eq.$nationalId&limit=1")
        val summary = if (s.length() > 0) s.getJSONObject(0) else null
        return PersonLookup(
            firstName = person.optString("first_name"),
            lastName = person.optString("last_name"),
            nationalId = person.optString("national_id"),
            visitCount = summary?.optInt("visit_count") ?: 0,
            lastDeparture = summary?.optString("last_departure") ?: ""
        )
    }

    fun updateUnitCapacity(unitId: String, capacity: Int) {
        api.patch("/rest/v1/units?id=eq.$unitId", JSONObject().put("capacity", capacity).put("capacity_configured", true))
    }
}
