package com.imanieh.zaersara.data

import com.imanieh.zaersara.model.*
import org.json.JSONArray
import org.json.JSONObject

class Repository(private val api: SupabaseRest) {
    fun units(): List<UnitItem> {
        val a = api.get("/rest/v1/units?select=id,name,capacity,kind&active=eq.true&order=sort_order")
        return (0 until a.length()).map { i -> a.getJSONObject(i).let { UnitItem(it.getString("id"), it.getString("name"), it.getInt("capacity"), it.optString("kind")) } }
    }

    fun reservations(): List<Reservation> {
        val a = api.get("/rest/v1/reservations?select=id,title,unit_id,start_date,end_date,guest_count,reservation_type,leader_name,leader_phone,is_paid,amount,payment_status,notes,units(name)&status=neq.cancelled&order=start_date")
        return (0 until a.length()).map { i ->
            val o=a.getJSONObject(i)
            Reservation(o.getString("id"),o.optString("title"),o.getString("unit_id"),o.optJSONObject("units")?.optString("name")?:"",o.getString("start_date"),o.getString("end_date"),o.getInt("guest_count"),o.optString("reservation_type"),o.optString("leader_name"),o.optString("leader_phone"),o.optBoolean("is_paid"),o.optLong("amount"),o.optString("payment_status"),o.optString("notes"))
        }
    }

    fun createReservation(r: Reservation, guests: List<GuestInput>) {
        val body=JSONObject().put("p_title",r.title).put("p_unit_id",r.unitId).put("p_start_date",r.startDate).put("p_end_date",r.endDate).put("p_guest_count",guests.size).put("p_reservation_type",r.reservationType).put("p_leader_name",r.leaderName).put("p_leader_phone",r.leaderPhone).put("p_is_paid",r.isPaid).put("p_amount",r.amount).put("p_payment_status",r.paymentStatus).put("p_notes",r.notes)
        val ga=JSONArray(); guests.forEach { ga.put(JSONObject().put("first_name",it.firstName).put("last_name",it.lastName).put("national_id",it.nationalId)) }; body.put("p_guests",ga)
        api.post("/rest/v1/rpc/create_reservation_atomic",body)
    }

    fun personHistory(nationalId:String):Pair<String,Int>? {
        val a=api.get("/rest/v1/person_visit_summary?select=full_name,visit_count&national_id=eq.$nationalId")
        if(a.length()==0)return null; val o=a.getJSONObject(0); return o.optString("full_name") to o.optInt("visit_count")
    }

    fun lookupPerson(nationalId: String): PersonLookup? {
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
}
