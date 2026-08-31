package com.imanieh.zaersara.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.imanieh.zaersara.data.*
import com.imanieh.zaersara.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class AppViewModel(app: Application): AndroidViewModel(app) {
    val prefs = AppPrefs(app)
    private fun repo() = Repository(SupabaseRest(prefs))

    private val _units = MutableStateFlow<List<UnitItem>>(emptyList()); val units = _units.asStateFlow()
    private val _reservations = MutableStateFlow<List<Reservation>>(emptyList()); val reservations = _reservations.asStateFlow()
    private val _busy = MutableStateFlow(false); val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null); val message = _message.asStateFlow()
    private val _serverState = MutableStateFlow("checking"); val serverState = _serverState.asStateFlow()
    private val _lastSync = MutableStateFlow<Long?>(null); val lastSync = _lastSync.asStateFlow()
    private val _sessionExpired = MutableStateFlow(false); val sessionExpired = _sessionExpired.asStateFlow()

    val configured get() = prefs.configured
    val loggedIn get() = prefs.accessToken.isNotBlank() || prefs.refreshToken.isNotBlank()

    fun saveConfig(url: String, key: String) { prefs.baseUrl = url.trim().trimEnd('/'); prefs.anonKey = key.trim() }

    fun login(email: String, password: String, onOk: () -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { _busy.value = true; SupabaseRest(prefs).signIn(email, password) }
            .onSuccess { _busy.value = false; _sessionExpired.value = false; viewModelScope.launch(Dispatchers.Main) { onOk() }; refresh() }
            .onFailure { _busy.value = false; _message.value = it.message ?: "ورود ناموفق بود" }
    }

    fun refresh() {
        if (!configured || !loggedIn) return
        viewModelScope.launch(Dispatchers.IO) {
            _serverState.value = "checking"
            runCatching { _busy.value = true; repo().units() to repo().reservations() }
                .onSuccess { _units.value = it.first; _reservations.value = it.second; _busy.value = false; _serverState.value = "connected"; _lastSync.value = System.currentTimeMillis() }
                .onFailure {
                    _busy.value = false
                    if (it is SessionExpiredException) { _serverState.value = "auth"; _sessionExpired.value = true }
                    else { _serverState.value = "offline"; _message.value = it.message ?: "ارتباط با سرور برقرار نشد" }
                }
        }
    }

    fun isUnitFree(unitId: String, start: LocalDate, end: LocalDate, excludeReservationId: String? = null): Boolean = _reservations.value.none { r ->
        if (r.unitId != unitId || r.id == excludeReservationId) false else {
            val rs = runCatching { LocalDate.parse(r.startDate) }.getOrNull()
            val re = runCatching { LocalDate.parse(r.endDate) }.getOrNull()
            rs != null && re != null && start.isBefore(re) && end.isAfter(rs)
        }
    }

    fun suggestions(start: LocalDate, end: LocalDate, people: Int, caravan: Boolean): List<UnitSuggestion> {
        if (people <= 0 || !end.isAfter(start)) return emptyList()
        val available = _units.value.filter { u -> isUnitFree(u.id, start, end) && (u.unitGroup == "apartment" || (u.capacityConfigured && u.capacity > 0)) }
        val out = mutableListOf<UnitSuggestion>()
        for ((group, groupUnits) in available.groupBy { it.unitGroup }) {
            if (group == "apartment") {
                groupUnits.forEach { out += UnitSuggestion(group, listOf(it), listOf(people), 0) }
                continue
            }
            if (!caravan) {
                groupUnits.filter { it.capacity >= people }
                    .sortedWith(compareBy<UnitItem> { it.capacity - people }.thenBy { it.capacity })
                    .take(3)
                    .forEach { out += UnitSuggestion(group, listOf(it), listOf(people), it.capacity - people) }
            } else {
                val sorted = groupUnits.sortedByDescending { it.capacity }
                val combos = mutableListOf<List<UnitItem>>()
                fun walk(index: Int, chosen: MutableList<UnitItem>) {
                    if (chosen.isNotEmpty() && chosen.sumOf { it.capacity } >= people) { combos += chosen.toList(); return }
                    for (i in index until sorted.size) {
                        chosen += sorted[i]; walk(i + 1, chosen); chosen.removeAt(chosen.lastIndex)
                    }
                }
                walk(0, mutableListOf())
                combos.distinctBy { it.map(UnitItem::id).sorted().joinToString() }
                    .sortedWith(compareBy<List<UnitItem>> { it.sumOf(UnitItem::capacity) - people }.thenBy { it.size })
                    .take(3)
                    .forEach { combo ->
                        var remaining = people
                        val allocations = combo.map { u -> minOf(u.capacity, remaining).also { remaining -= it } }
                        out += UnitSuggestion(group, combo, allocations, combo.sumOf { it.capacity } - people)
                    }
            }
        }
        val priority = mapOf("original" to 0, "fatemiyeh" to 1, "apartment" to 2)
        return out.sortedWith(compareBy<UnitSuggestion> { priority[it.group] ?: 9 }.thenBy { it.spareCapacity }.thenBy { it.units.size })
    }

    fun availableCapacityByGroup(start: LocalDate, end: LocalDate): Map<String, Int?> {
        val free = _units.value.filter { isUnitFree(it.id, start, end) }
        return mapOf(
            "original" to free.filter { it.unitGroup == "original" }.sumOf { it.capacity },
            "fatemiyeh" to free.filter { it.unitGroup == "fatemiyeh" }.sumOf { it.capacity },
            "apartment" to if (free.any { it.unitGroup == "apartment" }) null else 0
        )
    }

    fun createBooking(title: String, startDate: String, endDate: String, reservationType: String, leaderName: String, leaderPhone: String,
                      isPaid: Boolean, amount: Long, paymentStatus: String, notes: String, plan: List<PlanUnit>, guests: List<GuestInput> = emptyList(), onOk: () -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { _busy.value = true; repo().createBooking(title,startDate,endDate,reservationType,leaderName,leaderPhone,isPaid,amount,paymentStatus,notes,plan,guests) }
            .onSuccess { _busy.value = false; refresh(); viewModelScope.launch(Dispatchers.Main) { onOk() } }
            .onFailure { handleFailure(it) }
    }

    fun updateBookingMeta(groupId: String, title: String, startDate: String, endDate: String, leaderName: String, leaderPhone: String,
                          isPaid: Boolean, amount: Long, paymentStatus: String, notes: String, onOk: () -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { _busy.value = true; repo().updateBookingMeta(groupId,title,startDate,endDate,leaderName,leaderPhone,isPaid,amount,paymentStatus,notes) }
            .onSuccess { _busy.value = false; refresh(); viewModelScope.launch(Dispatchers.Main) { onOk() } }
            .onFailure { handleFailure(it) }
    }

    fun updateBookingUnit(reservationId: String, unitId: String, guestCount: Int, familyLastName: String, onOk: () -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { _busy.value = true; repo().updateBookingUnit(reservationId,unitId,guestCount,familyLastName) }
            .onSuccess { _busy.value = false; refresh(); viewModelScope.launch(Dispatchers.Main) { onOk() } }
            .onFailure { handleFailure(it) }
    }

    fun addBookingUnit(groupId: String, unitId: String, guestCount: Int, familyLastName: String, onOk: () -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { _busy.value = true; repo().addBookingUnit(groupId,unitId,guestCount,familyLastName) }
            .onSuccess { _busy.value = false; refresh(); viewModelScope.launch(Dispatchers.Main) { onOk() } }
            .onFailure { handleFailure(it) }
    }

    fun removeBookingUnit(reservationId: String, onOk: () -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { _busy.value = true; repo().removeBookingUnit(reservationId) }
            .onSuccess { _busy.value = false; refresh(); viewModelScope.launch(Dispatchers.Main) { onOk() } }
            .onFailure { handleFailure(it) }
    }

    fun cancelBooking(groupId: String, onOk: () -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { _busy.value = true; repo().cancelBooking(groupId) }
            .onSuccess { _busy.value = false; refresh(); viewModelScope.launch(Dispatchers.Main) { onOk() } }
            .onFailure { handleFailure(it) }
    }

    fun markBookingStatus(groupId: String, action: String, onOk: () -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { _busy.value = true; repo().markBookingStatus(groupId, action) }
            .onSuccess { _busy.value = false; refresh(); viewModelScope.launch(Dispatchers.Main) { onOk() } }
            .onFailure { handleFailure(it) }
    }

    fun reservationGuests(reservationId: String, onResult: (List<GuestInput>) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        val result = runCatching { repo().reservationGuests(reservationId) }.getOrElse { _message.value = it.message; emptyList() }
        viewModelScope.launch(Dispatchers.Main) { onResult(result) }
    }

    fun setReservationGuests(reservationId: String, guests: List<GuestInput>, onOk: () -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { _busy.value = true; repo().setReservationGuests(reservationId, guests) }
            .onSuccess { _busy.value = false; viewModelScope.launch(Dispatchers.Main) { onOk() } }
            .onFailure { handleFailure(it) }
    }

    fun lookupPerson(id: String, onResult: (PersonLookup?) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        val result = runCatching { repo().lookupPerson(id) }.getOrNull(); viewModelScope.launch(Dispatchers.Main) { onResult(result) }
    }

    fun searchPeople(mode: String, query: String, onResult: (List<PersonLookup>) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        val result = runCatching { repo().searchPeople(mode, query) }.getOrElse { _message.value = it.message; emptyList() }
        viewModelScope.launch(Dispatchers.Main) { onResult(result) }
    }

    fun updateUnitCapacity(unitId: String, capacity: Int) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { _busy.value = true; repo().updateUnitCapacity(unitId, capacity) }
            .onSuccess { _busy.value = false; refresh(); _message.value = "ظرفیت واحد ذخیره شد" }
            .onFailure { handleFailure(it) }
    }

    private fun handleFailure(t: Throwable) {
        _busy.value = false
        if (t is SessionExpiredException) { _sessionExpired.value = true; _serverState.value = "auth" }
        else _message.value = t.message ?: "عملیات انجام نشد"
    }

    fun clearMessage() { _message.value = null }
    fun clearSessionExpired() { _sessionExpired.value = false }
}
