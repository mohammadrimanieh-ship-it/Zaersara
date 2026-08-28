@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.imanieh.zaersara.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.navigation.compose.*
import com.imanieh.zaersara.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun App(vm:AppViewModel){
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){
        MaterialTheme{
            val nav=rememberNavController(); val msg by vm.message.collectAsState(); val busy by vm.busy.collectAsState()
            LaunchedEffect(Unit){vm.refresh()}
            val start=when{!vm.configured->"config";!vm.loggedIn->"login";else->"home"}
            Box(Modifier.fillMaxSize()){
                NavHost(nav,startDestination=start){
                    composable("config"){ConfigScreen(vm){ if(vm.loggedIn) nav.popBackStack() else nav.navigate("login"){popUpTo("config"){inclusive=true}} }}
                    composable("login"){LoginScreen(vm){nav.navigate("home"){popUpTo("login"){inclusive=true}}}}
                    composable("home"){HomeScreen(vm,{nav.navigate("new")},{nav.navigate("people")},{nav.navigate("config")})}
                    composable("new"){NewReservationScreen(vm){nav.popBackStack()}}
                    composable("people"){PeopleScreen(vm){nav.popBackStack()}}
                }
                if(busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
                if(msg!=null) AlertDialog(onDismissRequest={vm.clearMessage()},confirmButton={TextButton(onClick={vm.clearMessage()}){Text("باشه")}},text={Text(msg!!)})
            }
        }
    }
}

@Composable fun ConfigScreen(vm:AppViewModel,onDone:()->Unit){
    var url by remember{mutableStateOf(vm.prefs.baseUrl)};var key by remember{mutableStateOf(vm.prefs.anonKey)}
    Form("تنظیم اتصال آنلاین"){
        OutlinedTextField(url,{url=it},label={Text("Supabase URL")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(key,{key=it},label={Text("Anon key")},modifier=Modifier.fillMaxWidth())
        Button(onClick={vm.saveConfig(url,key);onDone()},enabled=url.isNotBlank()&&key.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("ذخیره و ادامه")}
        Text("این اطلاعات فقط روی دستگاه ذخیره می‌شود. فایل backend/schema.sql را یک‌بار در Supabase اجرا کنید.",style=MaterialTheme.typography.bodySmall)
    }
}

@Composable fun LoginScreen(vm:AppViewModel,onOk:()->Unit){var email by remember{mutableStateOf("")};var pass by remember{mutableStateOf("")};Form("ورود کاربران"){
    OutlinedTextField(email,{email=it},label={Text("ایمیل")},modifier=Modifier.fillMaxWidth())
    OutlinedTextField(pass,{pass=it},label={Text("رمز عبور")},modifier=Modifier.fillMaxWidth())
    Button(onClick={vm.login(email,pass,onOk)},enabled=email.isNotBlank()&&pass.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("ورود")}
}}

@Composable fun HomeScreen(vm:AppViewModel,onNew:()->Unit,onPeople:()->Unit,onConfig:()->Unit){
    val units by vm.units.collectAsState();val rs by vm.reservations.collectAsState()
    Scaffold(topBar={TopAppBar(title={Text("مدیریت زائرسرا مشهد")})},floatingActionButton={FloatingActionButton(onClick=onNew){Text("+")}}){p->
        LazyColumn(Modifier.padding(p).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Summary("واحدها",units.size.toString(),Modifier.weight(1f));Summary("رزروهای فعال",rs.size.toString(),Modifier.weight(1f))}}
            item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick=onPeople,modifier=Modifier.weight(1f)){Text("سوابق افراد")};OutlinedButton(onClick=onConfig,modifier=Modifier.weight(1f)){Text("تنظیمات")}}}
            item{Text("رزروها",style=MaterialTheme.typography.titleLarge)}
            if(rs.isEmpty()) item{Text("هنوز رزروی ثبت نشده است.")}
            items(rs){r->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(r.title.ifBlank{"رزرو"},style=MaterialTheme.typography.titleMedium);Text("${r.unitName} • ${r.guestCount} نفر");Text("${r.startDate} تا ${r.endDate}");if(r.reservationType=="caravan")Text("کاروانی • سرپرست: ${r.leaderName}");Text(if(r.isPaid)"پولی • ${r.amount} تومان • ${r.paymentStatus}" else "رایگان")}}}
        }
    }
}

@Composable fun NewReservationScreen(vm:AppViewModel,onDone:()->Unit){
    val units by vm.units.collectAsState(); var title by remember{mutableStateOf("")};var unitId by remember{mutableStateOf("")};var start by remember{mutableStateOf("")};var end by remember{mutableStateOf("")};var count by remember{mutableStateOf("")};var caravan by remember{mutableStateOf(false)};var leader by remember{mutableStateOf("")};var phone by remember{mutableStateOf("")};var paid by remember{mutableStateOf(false)};var amount by remember{mutableStateOf("")};var notes by remember{mutableStateOf("")};var guests by remember{mutableStateOf(listOf<GuestInput>())};var fn by remember{mutableStateOf("")};var ln by remember{mutableStateOf("")};var nid by remember{mutableStateOf("")}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Text("رزرو جدید",style=MaterialTheme.typography.headlineSmall)}
        item{OutlinedTextField(title,{title=it},label={Text("عنوان / نام خانواده یا کاروان")},modifier=Modifier.fillMaxWidth())}
        item{Text("انتخاب واحد");units.forEach{u->FilterChip(selected=unitId==u.id,onClick={unitId=u.id},label={Text("${u.name} (${u.capacity} نفر)")},modifier=Modifier.padding(end=6.dp))}}
        item{OutlinedTextField(start,{start=it},label={Text("تاریخ ورود (YYYY-MM-DD)")},modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(8.dp));OutlinedTextField(end,{end=it},label={Text("تاریخ خروج (YYYY-MM-DD)")},modifier=Modifier.fillMaxWidth())}
        item{OutlinedTextField(count,{count=it.filter(Char::isDigit)},label={Text("تعداد نفرات")},modifier=Modifier.fillMaxWidth())}
        item{Row(verticalAlignment=Alignment.CenterVertically){Switch(caravan,{caravan=it});Spacer(Modifier.width(8.dp));Text(if(caravan)"رزرو کاروانی" else "رزرو خانوادگی / فردی")}}
        if(caravan)item{OutlinedTextField(leader,{leader=it},label={Text("نام سرپرست")},modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(8.dp));OutlinedTextField(phone,{phone=it},label={Text("شماره تماس سرپرست")},modifier=Modifier.fillMaxWidth())}
        item{Text("افراد رزرو",style=MaterialTheme.typography.titleMedium);Text("کد ملی برای هر نفر الزامی است.",style=MaterialTheme.typography.bodySmall)}
        items(guests){g->AssistChip(onClick={},label={Text("${g.firstName} ${g.lastName} • ${g.nationalId}")})}
        item{OutlinedTextField(fn,{fn=it},label={Text("نام")},modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(6.dp));OutlinedTextField(ln,{ln=it},label={Text("نام خانوادگی")},modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(6.dp));OutlinedTextField(nid,{nid=it.filter(Char::isDigit)},label={Text("کد ملی")},modifier=Modifier.fillMaxWidth());TextButton(onClick={if(fn.isNotBlank()&&ln.isNotBlank()&&nid.length==10){guests=guests+GuestInput(fn,ln,nid);fn="";ln="";nid=""}}){Text("افزودن فرد")}}
        item{Row(verticalAlignment=Alignment.CenterVertically){Switch(paid,{paid=it});Spacer(Modifier.width(8.dp));Text(if(paid)"اقامت پولی" else "اقامت رایگان")}}
        if(paid)item{OutlinedTextField(amount,{amount=it.filter(Char::isDigit)},label={Text("مبلغ کل (تومان)")},modifier=Modifier.fillMaxWidth())}
        item{OutlinedTextField(notes,{notes=it},label={Text("توضیحات")},modifier=Modifier.fillMaxWidth(),minLines=2)}
        item{Button(onClick={val r=Reservation(title=title,unitId=unitId,startDate=start,endDate=end,guestCount=count.toIntOrNull()?:guests.size,reservationType=if(caravan)"caravan" else "family",leaderName=leader,leaderPhone=phone,isPaid=paid,amount=amount.toLongOrNull()?:0,paymentStatus=if(paid)"پرداخت نشده" else "رایگان",notes=notes);vm.create(r,guests,onDone)},enabled=unitId.isNotBlank()&&start.isNotBlank()&&end.isNotBlank()&&guests.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text("ثبت رزرو")}}
        item{Spacer(Modifier.height(70.dp))}
    }
}

@Composable fun PeopleScreen(vm:AppViewModel,onBack:()->Unit){var id by remember{mutableStateOf("")};var result by remember{mutableStateOf("")};Form("بررسی سابقه زائر"){
    OutlinedTextField(id,{id=it.filter(Char::isDigit)},label={Text("کد ملی")},modifier=Modifier.fillMaxWidth())
    Button(onClick={vm.checkPerson(id){result=it}},enabled=id.length==10,modifier=Modifier.fillMaxWidth()){Text("بررسی سابقه")}
    if(result.isNotBlank())Card(Modifier.fillMaxWidth()){Text(result,Modifier.padding(16.dp))}
    OutlinedButton(onClick=onBack,modifier=Modifier.fillMaxWidth()){Text("بازگشت")}
}}

@Composable fun Form(title:String,content:@Composable ColumnScope.()->Unit){Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text(title,style=MaterialTheme.typography.headlineSmall);content()}}
@Composable fun Summary(label:String,value:String,m:Modifier=Modifier){Card(m){Column(Modifier.padding(14.dp)){Text(value,style=MaterialTheme.typography.headlineMedium);Text(label)}}}
