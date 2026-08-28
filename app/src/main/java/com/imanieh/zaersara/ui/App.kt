@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.imanieh.zaersara.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.navigation.compose.*
import com.imanieh.zaersara.model.*
import com.imanieh.zaersara.util.JalaliCalendar
import com.imanieh.zaersara.util.JalaliDate
import java.time.LocalDate

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
        OutlinedTextField(key,{key=it},label={Text("Publishable / Anon key")},modifier=Modifier.fillMaxWidth())
        Button(onClick={vm.saveConfig(url,key);onDone()},enabled=url.isNotBlank()&&key.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("ذخیره و ادامه")}
        Text("فقط آدرس اصلی پروژه مثل https://xxxx.supabase.co را وارد کنید.",style=MaterialTheme.typography.bodySmall)
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
            itemsIndexed(rs){_,r->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(r.title.ifBlank{"رزرو"},style=MaterialTheme.typography.titleMedium);Text("${r.unitName} • ${r.guestCount} نفر");Text("${JalaliCalendar.isoToJalali(r.startDate)} تا ${JalaliCalendar.isoToJalali(r.endDate)}");if(r.reservationType=="caravan")Text("کاروانی • سرپرست: ${r.leaderName}");Text(if(r.isPaid)"پولی • ${r.amount} تومان • ${r.paymentStatus}" else "رایگان")}}}
        }
    }
}

@Composable fun NewReservationScreen(vm:AppViewModel,onDone:()->Unit){
    val units by vm.units.collectAsState()
    var title by remember{mutableStateOf("")};var unitId by remember{mutableStateOf("")}
    var startDate by remember{mutableStateOf<LocalDate?>(null)};var endDate by remember{mutableStateOf<LocalDate?>(null)}
    var showStartPicker by remember{mutableStateOf(false)};var showEndPicker by remember{mutableStateOf(false)}
    var caravan by remember{mutableStateOf(false)};var leader by remember{mutableStateOf("")};var phone by remember{mutableStateOf("")}
    var paid by remember{mutableStateOf(false)};var amount by remember{mutableStateOf("")};var notes by remember{mutableStateOf("")}
    var guests by remember{mutableStateOf(listOf<GuestInput>())};var fn by remember{mutableStateOf("")};var ln by remember{mutableStateOf("")};var nid by remember{mutableStateOf("")}
    var personNote by remember{mutableStateOf("")}
    val selectedUnit=units.firstOrNull{it.id==unitId}; val capacity=selectedUnit?.capacity?:0
    val full=capacity>0 && guests.size>=capacity

    LaunchedEffect(nid){
        if(nid.length==10){
            vm.lookupPerson(nid){p->
                if(p!=null){fn=p.firstName;ln=p.lastName;personNote=if(p.visitCount>0)"این زائر قبلاً ${p.visitCount} بار اقامت داشته است." else "اطلاعات زائر از سابقه بازیابی شد."}
                else personNote="کد ملی جدید است؛ نام و نام خانوادگی را وارد کنید."
            }
        } else personNote=""
    }

    val datesValid=startDate!=null && endDate!=null && endDate!!.isAfter(startDate!!)
    val canSave=unitId.isNotBlank() && datesValid && guests.isNotEmpty() && guests.size<=capacity && (!paid || (amount.toLongOrNull()?:0)>0)

    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Text("رزرو جدید",style=MaterialTheme.typography.headlineSmall)}
        item{OutlinedTextField(title,{title=it},label={Text("عنوان / نام خانواده یا کاروان")},modifier=Modifier.fillMaxWidth())}
        item{Text("انتخاب واحد");units.forEach{u->FilterChip(selected=unitId==u.id,onClick={unitId=u.id},enabled=guests.size<=u.capacity,label={Text("${u.name} (${u.capacity} نفر)")},modifier=Modifier.padding(end=6.dp))}}
        if(selectedUnit!=null)item{Text("تعداد ثبت‌شده: ${guests.size} از ${selectedUnit.capacity} نفر",style=MaterialTheme.typography.bodyMedium)}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={showStartPicker=true},modifier=Modifier.weight(1f)){Text(startDate?.let{JalaliCalendar.fromGregorian(it).display()}?:"تاریخ ورود")};OutlinedButton(onClick={showEndPicker=true},enabled=startDate!=null,modifier=Modifier.weight(1f)){Text(endDate?.let{JalaliCalendar.fromGregorian(it).display()}?:"تاریخ خروج")}};Text("تاریخ‌ها شمسی هستند و تاریخ گذشته قابل انتخاب نیست.",style=MaterialTheme.typography.bodySmall)}
        item{Row(verticalAlignment=Alignment.CenterVertically){Switch(caravan,{caravan=it});Spacer(Modifier.width(8.dp));Text(if(caravan)"رزرو کاروانی" else "رزرو خانوادگی / فردی")}}
        if(caravan)item{OutlinedTextField(leader,{leader=it},label={Text("نام سرپرست")},modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(8.dp));OutlinedTextField(phone,{phone=it.filter(Char::isDigit)},label={Text("شماره تماس سرپرست")},modifier=Modifier.fillMaxWidth())}
        item{Text("افراد رزرو",style=MaterialTheme.typography.titleMedium);Text("برای هر نفر نام، نام خانوادگی و کد ملی ۱۰ رقمی ثبت می‌شود.",style=MaterialTheme.typography.bodySmall)}
        itemsIndexed(guests){index,g->Card(Modifier.fillMaxWidth()){Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("${g.firstName} ${g.lastName}");Text(g.nationalId,style=MaterialTheme.typography.bodySmall)};TextButton(onClick={guests=guests.toMutableList().also{it.removeAt(index)}}){Text("حذف")}}}}
        item{
            OutlinedTextField(nid,{nid=it.filter(Char::isDigit).take(10)},label={Text("کد ملی")},modifier=Modifier.fillMaxWidth(),enabled=!full)
            if(personNote.isNotBlank())Text(personNote,style=MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp));OutlinedTextField(fn,{fn=it},label={Text("نام")},modifier=Modifier.fillMaxWidth(),enabled=!full)
            Spacer(Modifier.height(6.dp));OutlinedTextField(ln,{ln=it},label={Text("نام خانوادگی")},modifier=Modifier.fillMaxWidth(),enabled=!full)
            if(full)Text("ظرفیت ${selectedUnit?.name} تکمیل است؛ حداکثر $capacity نفر.",color=MaterialTheme.colorScheme.error)
            Button(onClick={guests=guests+GuestInput(fn.trim(),ln.trim(),nid);fn="";ln="";nid="";personNote=""},enabled=!full&&selectedUnit!=null&&fn.isNotBlank()&&ln.isNotBlank()&&nid.length==10&&!guests.any{it.nationalId==nid},modifier=Modifier.fillMaxWidth()){Text("+ افزودن زائر")}
        }
        item{Row(verticalAlignment=Alignment.CenterVertically){Switch(paid,{paid=it});Spacer(Modifier.width(8.dp));Text(if(paid)"اقامت پولی" else "اقامت رایگان")}}
        if(paid)item{OutlinedTextField(amount,{amount=it.filter(Char::isDigit)},label={Text("مبلغ کل (تومان)")},modifier=Modifier.fillMaxWidth())}
        item{OutlinedTextField(notes,{notes=it},label={Text("توضیحات")},modifier=Modifier.fillMaxWidth(),minLines=2)}
        if(!canSave)item{Text(when{unitId.isBlank()->"برای ثبت رزرو ابتدا یک واحد انتخاب کنید.";!datesValid->"تاریخ ورود و خروج را انتخاب کنید.";guests.isEmpty()->"حداقل یک زائر اضافه کنید.";guests.size>capacity->"تعداد زائران از ظرفیت واحد بیشتر است.";paid&&(amount.toLongOrNull()?:0)<=0->"مبلغ اقامت پولی را وارد کنید.";else->"اطلاعات رزرو را کامل کنید."},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)}
        item{Button(onClick={val r=Reservation(title=title,unitId=unitId,startDate=startDate.toString(),endDate=endDate.toString(),guestCount=guests.size,reservationType=if(caravan)"caravan" else "family",leaderName=leader,leaderPhone=phone,isPaid=paid,amount=amount.toLongOrNull()?:0,paymentStatus=if(paid)"پرداخت نشده" else "رایگان",notes=notes);vm.create(r,guests,onDone)},enabled=canSave,modifier=Modifier.fillMaxWidth()){Text("ثبت رزرو (${guests.size} نفر)")}}
        item{Spacer(Modifier.height(70.dp))}
    }

    if(showStartPicker) PersianDateDialog(initial=startDate?:LocalDate.now(),minDate=LocalDate.now(),title="تاریخ ورود",onDismiss={showStartPicker=false}){startDate=it;if(endDate!=null&&!endDate!!.isAfter(it))endDate=null;showStartPicker=false}
    if(showEndPicker && startDate!=null) PersianDateDialog(initial=endDate?:startDate!!.plusDays(1),minDate=startDate!!.plusDays(1),title="تاریخ خروج",onDismiss={showEndPicker=false}){endDate=it;showEndPicker=false}
}

@Composable fun PersianDateDialog(initial:LocalDate,minDate:LocalDate,title:String,onDismiss:()->Unit,onSelect:(LocalDate)->Unit){
    val monthNames=listOf("فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند")
    val initialJ=JalaliCalendar.fromGregorian(if(initial.isBefore(minDate))minDate else initial)
    var year by remember{mutableIntStateOf(initialJ.year)};var month by remember{mutableIntStateOf(initialJ.month)};var day by remember{mutableIntStateOf(initialJ.day)}
    fun move(delta:Int){var m=month+delta;var y=year;if(m<1){m=12;y--};if(m>12){m=1;y++};year=y;month=m;day=day.coerceAtMost(JalaliCalendar.monthLength(y,m))}
    val selected=runCatching{JalaliCalendar.toGregorian(JalaliDate(year,month,day))}.getOrNull();val valid=selected!=null&&!selected.isBefore(minDate)
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){TextButton(onClick={move(-1)}){Text("ماه قبل")};Text("${monthNames[month-1]} $year",style=MaterialTheme.typography.titleMedium);TextButton(onClick={move(1)}){Text("ماه بعد")}}
        val max=JalaliCalendar.monthLength(year,month)
        Row(Modifier.fillMaxWidth()){listOf("ش","ی","د","س","چ","پ","ج").forEach{Text(it,modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)}}
        val firstGregorian=JalaliCalendar.toGregorian(JalaliDate(year,month,1))
        val offset=(firstGregorian.dayOfWeek.value+1)%7
        val cells=List(offset){0}+(1..max).toList()
        cells.chunked(7).forEach{week->Row(Modifier.fillMaxWidth()){week.forEach{d->if(d==0){Spacer(Modifier.weight(1f))}else{val g=runCatching{JalaliCalendar.toGregorian(JalaliDate(year,month,d))}.getOrNull();val enabled=g!=null&&!g.isBefore(minDate);TextButton(onClick={day=d},enabled=enabled,modifier=Modifier.weight(1f),contentPadding=PaddingValues(0.dp)){Text(if(d==day)"[$d]" else d.toString())}}};repeat(7-week.size){Spacer(Modifier.weight(1f))}}}
    }},confirmButton={Button(onClick={onSelect(selected!!)},enabled=valid){Text("انتخاب")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})
}

@Composable fun PeopleScreen(vm:AppViewModel,onBack:()->Unit){var id by remember{mutableStateOf("")};var result by remember{mutableStateOf("")};Form("بررسی سابقه زائر"){
    OutlinedTextField(id,{id=it.filter(Char::isDigit).take(10)},label={Text("کد ملی")},modifier=Modifier.fillMaxWidth())
    Button(onClick={vm.checkPerson(id){result=it}},enabled=id.length==10,modifier=Modifier.fillMaxWidth()){Text("بررسی سابقه")}
    if(result.isNotBlank())Card(Modifier.fillMaxWidth()){Text(result,Modifier.padding(16.dp))}
    OutlinedButton(onClick=onBack,modifier=Modifier.fillMaxWidth()){Text("بازگشت")}
}}

@Composable fun Form(title:String,content:@Composable ColumnScope.()->Unit){Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text(title,style=MaterialTheme.typography.headlineSmall);content()}}
@Composable fun Summary(label:String,value:String,m:Modifier=Modifier){Card(m){Column(Modifier.padding(14.dp)){Text(value,style=MaterialTheme.typography.headlineMedium);Text(label)}}}
