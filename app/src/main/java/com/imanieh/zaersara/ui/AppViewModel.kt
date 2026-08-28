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

class AppViewModel(app: Application): AndroidViewModel(app) {
    val prefs=AppPrefs(app); private fun repo()=Repository(SupabaseRest(prefs))
    private val _units=MutableStateFlow<List<UnitItem>>(emptyList()); val units=_units.asStateFlow()
    private val _reservations=MutableStateFlow<List<Reservation>>(emptyList()); val reservations=_reservations.asStateFlow()
    private val _busy=MutableStateFlow(false); val busy=_busy.asStateFlow()
    private val _message=MutableStateFlow<String?>(null); val message=_message.asStateFlow()
    val configured get()=prefs.configured; val loggedIn get()=prefs.accessToken.isNotBlank()
    fun saveConfig(url:String,key:String){prefs.baseUrl=url.trim().trimEnd('/');prefs.anonKey=key.trim();_message.value="تنظیمات ذخیره شد"}
    fun login(email:String,password:String,onOk:()->Unit)=viewModelScope.launch(Dispatchers.IO){
        runCatching{_busy.value=true;SupabaseRest(prefs).signIn(email,password)}.onSuccess{_busy.value=false;viewModelScope.launch(Dispatchers.Main){onOk()};refresh()}.onFailure{_busy.value=false;_message.value=it.message}}
    fun refresh(){if(!configured||!loggedIn)return;viewModelScope.launch(Dispatchers.IO){runCatching{_busy.value=true;repo().units() to repo().reservations()}.onSuccess{_units.value=it.first;_reservations.value=it.second;_busy.value=false}.onFailure{_busy.value=false;_message.value=it.message}}}
    fun create(r:Reservation,g:List<GuestInput>,onOk:()->Unit)=viewModelScope.launch(Dispatchers.IO){runCatching{_busy.value=true;repo().createReservation(r,g)}.onSuccess{_busy.value=false;refresh();viewModelScope.launch(Dispatchers.Main){onOk()}}.onFailure{_busy.value=false;_message.value=it.message}}
    fun checkPerson(id:String,onResult:(String)->Unit)=viewModelScope.launch(Dispatchers.IO){val t=runCatching{repo().personHistory(id)}.fold({if(it==null)"سابقه‌ای پیدا نشد" else "${it.first} قبلاً ${it.second} بار اقامت داشته است"},{"خطا: ${it.message}"});viewModelScope.launch(Dispatchers.Main){onResult(t)}}
    fun lookupPerson(id:String,onResult:(PersonLookup?)->Unit)=viewModelScope.launch(Dispatchers.IO){val result=runCatching{repo().lookupPerson(id)}.getOrNull();viewModelScope.launch(Dispatchers.Main){onResult(result)}}
    fun clearMessage(){_message.value=null}
}
