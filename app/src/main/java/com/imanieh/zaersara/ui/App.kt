@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.imanieh.zaersara.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.imanieh.zaersara.model.*
import com.imanieh.zaersara.util.JalaliCalendar
import com.imanieh.zaersara.util.JalaliDate
import com.imanieh.zaersara.util.normalizeNumeric
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val Navy = Color(0xFF0D1326)
private val NavyCard = Color(0xFF171E36)
private val NavyCard2 = Color(0xFF202743)
private val Purple = Color(0xFF7C4DFF)
private val PurpleSoft = Color(0xFFB79CFF)
private val Mint = Color(0xFF64D6B5)
private val Gold = Color(0xFFE8B45A)
private val Pink = Color(0xFFEE7A9C)
private val TextMain = Color(0xFFF4F2FA)
private val TextMuted = Color(0xFFB8B7C7)

private val AppColors = darkColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF30265A),
    onPrimaryContainer = TextMain,
    secondary = Gold,
    secondaryContainer = Color(0xFF4B3A20),
    background = Navy,
    onBackground = TextMain,
    surface = NavyCard,
    onSurface = TextMain,
    surfaceVariant = NavyCard2,
    onSurfaceVariant = TextMuted,
    error = Color(0xFFFF6E77)
)

@Composable
fun App(vm: AppViewModel) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = AppColors) {
            val nav = rememberNavController()
            val msg by vm.message.collectAsState()
            val busy by vm.busy.collectAsState()
            val sessionExpired by vm.sessionExpired.collectAsState()
            LaunchedEffect(Unit) { vm.refresh() }
            LaunchedEffect(sessionExpired) {
                if (sessionExpired) {
                    vm.clearSessionExpired()
                    nav.navigate("login") { popUpTo("home") { inclusive = true } }
                }
            }
            val start = when {
                !vm.configured -> "config"
                !vm.loggedIn -> "login"
                else -> "home"
            }
            Box(Modifier.fillMaxSize()) {
                NavHost(nav, startDestination = start) {
                    composable("config") { ConfigScreen(vm) { if (vm.loggedIn) nav.navigate("home") { popUpTo("config") { inclusive = true } } else nav.navigate("login") { popUpTo("config") { inclusive = true } } } }
                    composable("login") { LoginScreen(vm) { nav.navigate("home") { popUpTo("login") { inclusive = true } } } }
                    composable("home") {
                        HomeScreen(vm,
                            onNew = { nav.navigate("new") },
                            onReports = { nav.navigate("reports") },
                            onPeople = { nav.navigate("people") },
                            onToday = { nav.navigate("today/all") },
                            onCalendar = { nav.navigate("occupancy") },
                            onSearch = { nav.navigate("search") },
                            onMetric = { nav.navigate("today/$it") },
                            onDetail = { nav.navigate("detail/$it") }
                        )
                    }
                    composable("new") { NewReservationScreen(vm) { nav.popBackStack() } }
                    composable("reports") { ReportsScreen(vm) { nav.popBackStack() } }
                    composable("units") { UnitsScreen(vm) { nav.popBackStack() } }
                    composable("people") { PeopleScreen(vm) { nav.popBackStack() } }
                    composable("today/{mode}") { back -> TodayScreen(vm, back.arguments?.getString("mode") ?: "all", { nav.popBackStack() }) { nav.navigate("detail/$it") } }
                    composable("occupancy") { OccupancyScreen(vm, { nav.popBackStack() }) { nav.navigate("detail/$it") } }
                    composable("search") { GlobalSearchScreen(vm, { nav.popBackStack() }) { nav.navigate("detail/$it") } }
                    composable("detail/{id}") { back ->
                        ReservationDetailsScreen(vm, back.arguments?.getString("id").orEmpty()) { nav.popBackStack() }
                    }
                }
                AnimatedVisibility(busy, modifier = Modifier.align(Alignment.Center)) {
                    Surface(shape = RoundedCornerShape(22.dp), tonalElevation = 8.dp, color = NavyCard2) {
                        Row(Modifier.padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                            Spacer(Modifier.width(12.dp)); Text("در حال پردازش…")
                        }
                    }
                }
                if (msg != null) AlertDialog(
                    onDismissRequest = { vm.clearMessage() },
                    confirmButton = { TextButton(onClick = { vm.clearMessage() }) { Text("باشه") } },
                    text = { Text(msg!!) }
                )
            }
        }
    }
}

@Composable
fun ConfigScreen(vm: AppViewModel, onDone: () -> Unit) {
    var url by remember { mutableStateOf(vm.prefs.baseUrl) }
    var key by remember { mutableStateOf(vm.prefs.anonKey) }
    Page("تنظیم اولیه") {
        InfoCard("اتصال به پایگاه داده", "این صفحه فقط برای راه‌اندازی اولیه است و پس از تنظیم از داشبورد در دسترس نیست.")
        OutlinedTextField(url, { url = it }, label = { Text("Supabase URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(key, { key = it }, label = { Text("Publishable / Anon key") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.saveConfig(url, key); onDone() }, enabled = url.isNotBlank() && key.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("ذخیره و ادامه") }
    }
}

@Composable
fun LoginScreen(vm: AppViewModel, onOk: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    Page("ورود کاربران") {
        InfoCard("زائرسرا مشهد", "برای دسترسی به رزروها وارد حساب کاربری شوید.")
        OutlinedTextField(email, { email = it }, label = { Text("ایمیل") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(pass, { pass = it }, label = { Text("رمز عبور") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = { vm.login(email, pass, onOk) }, enabled = email.isNotBlank() && pass.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("ورود") }
    }
}

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onNew: () -> Unit,
    onReports: () -> Unit,
    onPeople: () -> Unit,
    onToday: () -> Unit,
    onCalendar: () -> Unit,
    onSearch: () -> Unit,
    onMetric: (String) -> Unit,
    onDetail: (String) -> Unit
) {
    val units by vm.units.collectAsState()
    val rs by vm.reservations.collectAsState()
    val serverState by vm.serverState.collectAsState()
    val lastSync by vm.lastSync.collectAsState()
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(1000) } }
    val today = now.toLocalDate()
    fun activeToday(r: Reservation) = runCatching { !today.isBefore(LocalDate.parse(r.startDate)) && today.isBefore(LocalDate.parse(r.endDate)) }.getOrDefault(false)
    val occupied = units.count { u -> rs.any { it.unitId == u.id && activeToday(it) } }
    val checkedInRows = rs.filter { it.checkInAt.isNotBlank() && it.checkInAt != "null" && (it.checkOutAt.isBlank() || it.checkOutAt == "null") }
    val todayGuests = if (checkedInRows.isNotEmpty()) checkedInRows.sumOf { it.guestCount } else rs.filter(::activeToday).sumOf { it.guestCount }
    val todayArrivals = rs.filter { it.startDate == today.toString() }.map { it.bookingGroupId.ifBlank { it.id } }.distinct().size
    val activeBookings = rs.map { it.bookingGroupId.ifBlank { it.id } }.distinct().size
    val j = JalaliCalendar.fromGregorian(today)
    val dayName = listOf("دوشنبه","سه‌شنبه","چهارشنبه","پنج‌شنبه","جمعه","شنبه","یکشنبه")[today.dayOfWeek.value - 1]
    val timeText = now.format(DateTimeFormatter.ofPattern("HH:mm"))
    val grouped = rs.groupBy { it.bookingGroupId.ifBlank { it.id } }.values.sortedBy { it.minOfOrNull { r -> r.startDate } ?: "" }.take(20)

    Scaffold(
        containerColor = Navy,
        bottomBar = { HomeBottomBar(onToday, onReports, onPeople) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick=onNew,containerColor=Purple,contentColor=Color.White,icon={Icon(Icons.Default.Add,null)},text={Text("رزرو جدید",fontWeight=FontWeight.Bold)}) }
    ) { p ->
        LazyColumn(Modifier.padding(p).fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(top=14.dp,bottom=96.dp)) {
            item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { Column { Text("مدیریت زائرسرا مشهد",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold);Text("مرکز عملیات امروز",color=TextMuted) }; ServerStatusBadge(serverState,lastSync){vm.refresh()} } }
            item { Card(colors=CardDefaults.cardColors(containerColor=NavyCard2),shape=RoundedCornerShape(24.dp)) { Row(Modifier.fillMaxWidth().padding(18.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { Column { Text("$dayName، ${j.display()}",fontWeight=FontWeight.Bold);Text("تاریخ امروز",style=MaterialTheme.typography.bodySmall,color=TextMuted) };Column(horizontalAlignment=Alignment.End){Text(timeText,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold,color=PurpleSoft);Text("ساعت",style=MaterialTheme.typography.bodySmall,color=TextMuted)} } } }
            item { Card(colors=CardDefaults.cardColors(containerColor=NavyCard),shape=RoundedCornerShape(26.dp),border=BorderStroke(1.dp,Color.White.copy(alpha=.07f))) { Row(Modifier.fillMaxWidth().padding(vertical=18.dp),horizontalArrangement=Arrangement.SpaceEvenly) { DashboardMetric(Icons.Default.People,"زائر حاضر",todayGuests.toString(),Mint){onMetric("present")};DashboardDivider();DashboardMetric(Icons.Default.Home,"واحد اشغال","$occupied/${units.size}",Gold){onMetric("occupied")};DashboardDivider();DashboardMetric(Icons.Default.CheckCircle,"رزرو فعال",activeBookings.toString(),PurpleSoft){onMetric("active")};DashboardDivider();DashboardMetric(Icons.Default.DateRange,"ورودی امروز",todayArrivals.toString(),Pink){onMetric("arrivals")} } } }
            item { Text("دسترسی سریع",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold) }
            item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { QuickAction("امروز",Icons.Default.Today,onToday,Modifier.weight(1f));QuickAction("جستجو",Icons.Default.Search,onSearch,Modifier.weight(1f));QuickAction("تقویم اشغال",Icons.Default.CalendarMonth,onCalendar,Modifier.weight(1f)) } }
            item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("رزروهای نزدیک",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Icon(Icons.Default.DateRange,null,tint=PurpleSoft)} }
            if(grouped.isEmpty()) item { EmptyCard("هنوز رزروی ثبت نشده است.") }
            items(grouped){rows->BookingCard(rows){onDetail(rows.first().id)}}
        }
    }
}

@Composable
private fun HomeBottomBar(onToday:()->Unit,onReports:()->Unit,onPeople:()->Unit){
    NavigationBar(containerColor=Color(0xFF11182D),tonalElevation=0.dp){
        NavigationBarItem(selected=true,onClick={},icon={Icon(Icons.Default.Home,null)},label={Text("خانه")})
        NavigationBarItem(selected=false,onClick=onToday,icon={Icon(Icons.Default.Today,null)},label={Text("امروز")})
        NavigationBarItem(selected=false,onClick=onReports,icon={Icon(Icons.Default.List,null)},label={Text("گزارش")})
        NavigationBarItem(selected=false,onClick=onPeople,icon={Icon(Icons.Default.Person,null)},label={Text("سوابق")})
    }
}

@Composable
private fun DashboardMetric(icon:ImageVector,label:String,value:String,tint:Color,onClick:()->Unit){
    Surface(onClick=onClick,color=Color.Transparent,shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(horizontal=5.dp,vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)){Icon(icon,null,tint=tint,modifier=Modifier.size(26.dp));Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.ExtraBold);Text(label,style=MaterialTheme.typography.labelSmall,color=TextMuted)}}
}

@Composable private fun DashboardDivider(){Box(Modifier.width(1.dp).height(70.dp).padding(vertical=8.dp)){Surface(Modifier.fillMaxSize(),color=Color.White.copy(alpha=.10f)) {}}}

@Composable
private fun QuickAction(label:String,icon:ImageVector,onClick:()->Unit,modifier:Modifier=Modifier){Card(onClick=onClick,modifier=modifier,colors=CardDefaults.cardColors(containerColor=NavyCard2),shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,Purple.copy(alpha=.20f))){Column(Modifier.fillMaxWidth().padding(vertical=18.dp,horizontal=4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){Surface(shape=CircleShape,color=Purple.copy(alpha=.18f)){Icon(icon,null,tint=PurpleSoft,modifier=Modifier.padding(10.dp).size(24.dp))};Text(label,fontWeight=FontWeight.Medium,style=MaterialTheme.typography.bodySmall)}}}

@Composable
private fun ServerStatusBadge(state: String, lastSync: Long?, onRefresh: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "serverPulse")
    val pulse by transition.animateFloat(initialValue = .35f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
    val color = when (state) { "connected" -> Mint; "checking" -> Gold; "auth" -> Pink; else -> Color(0xFFFF7078) }
    val label = when (state) { "connected" -> "متصل"; "checking" -> "در حال اتصال"; "auth" -> "ورود مجدد"; else -> "قطع" }
    TextButton(onClick = onRefresh) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(Modifier.size(10.dp).alpha(if (state == "checking") pulse else 1f), shape = CircleShape, color = color) {}
            Text(label, color = color, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun BookingCard(rows: List<Reservation>, onClick: () -> Unit) {
    val first = rows.first()
    val totalGuests = rows.sumOf { it.guestCount }
    val names = rows.map { it.primaryLastName }.filter { it.isNotBlank() }.distinct()
    val title = when {
        first.reservationType == "caravan" && first.title.isNotBlank() -> first.title
        names.isNotEmpty() -> "خانواده ${names.first()}"
        first.title.isNotBlank() -> first.title
        else -> "رزرو"
    }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = NavyCard), shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, Color.White.copy(alpha=.06f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(rows.joinToString("، ") { it.unitName }, color = PurpleSoft, style = MaterialTheme.typography.bodyMedium)
                }
                Surface(shape = RoundedCornerShape(14.dp), color = Purple.copy(alpha=.16f)) { Text("$totalGuests نفر", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = PurpleSoft) }
            }
            Text("ورود: ${JalaliCalendar.isoToJalali(first.startDate)}   •   خروج: ${JalaliCalendar.isoToJalali(first.endDate)}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            if (rows.size > 1) Text("${rows.size} واحد در یک مجموعه", style = MaterialTheme.typography.labelMedium, color = Gold)
        }
    }
}

@Composable
fun NewReservationScreen(vm: AppViewModel, onDone: () -> Unit) {
    val units by vm.units.collectAsState(); val rs by vm.reservations.collectAsState()
    var startDate by remember { mutableStateOf<LocalDate?>(null) }; var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }; var showEndPicker by remember { mutableStateOf(false) }
    var peopleText by remember { mutableStateOf("") }; var caravan by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }; var selected by remember { mutableStateOf<UnitSuggestion?>(null) }; var stage by remember { mutableIntStateOf(1) }
    var title by remember { mutableStateOf("") }; var leader by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }
    var paid by remember { mutableStateOf(false) }; var paymentStatus by remember { mutableStateOf("پرداخت شده") }; var amount by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }
    var familyNames by remember { mutableStateOf<Map<String,String>>(emptyMap()) }; var allocationTexts by remember { mutableStateOf<Map<String,String>>(emptyMap()) }; var unitGuests by remember { mutableStateOf<Map<String,List<GuestInput>>>(emptyMap()) }; var editingUnitId by remember { mutableStateOf<String?>(null) }
    val people = peopleText.toIntOrNull() ?: 0
    val dateOk = startDate != null && endDate != null && endDate!!.isAfter(startDate!!)
    val suggestions = remember(startDate,endDate,people,caravan,searched,units,rs) { if (searched && dateOk && people>0) vm.suggestions(startDate!!,endDate!!,people,caravan) else emptyList() }
    val cap = remember(startDate,endDate,units,rs,searched) { if (searched && dateOk) vm.availableCapacityByGroup(startDate!!,endDate!!) else emptyMap() }

    Scaffold(containerColor = Navy, topBar = { DarkTopBar("رزرو جدید", onDone) }) { p ->
        LazyColumn(Modifier.padding(p).fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 34.dp)) {
            item { StepHeader("۱", "تاریخ و تعداد نفرات", "ابتدا ورود را انتخاب کنید؛ سپس خروج انتخاب می‌شود.") }
            item {
                Card(onClick = { showStartPicker = true }, colors = CardDefaults.cardColors(containerColor = NavyCard2), shape = RoundedCornerShape(22.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = CircleShape, color = Purple.copy(alpha=.18f)) { Icon(Icons.Default.DateRange, null, tint = PurpleSoft, modifier = Modifier.padding(10.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text("تاریخ ورود و خروج", fontWeight = FontWeight.Bold)
                            Text(when { startDate==null -> "برای انتخاب بازه لمس کنید"; endDate==null -> "ورود: ${JalaliCalendar.fromGregorian(startDate!!).display()} • حالا خروج را انتخاب کنید"; else -> "ورود ${JalaliCalendar.fromGregorian(startDate!!).display()}  ←  خروج ${JalaliCalendar.fromGregorian(endDate!!).display()}" }, color = TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.Edit, null, tint = PurpleSoft)
                    }
                }
            }
            item { OutlinedTextField(peopleText, { peopleText=normalizeNumeric(it,3); searched=false; selected=null; stage=1 }, label={Text("تعداد نفرات")}, modifier=Modifier.fillMaxWidth(), singleLine=true) }
            item { Row(verticalAlignment = Alignment.CenterVertically) { Switch(caravan,{caravan=it;searched=false;selected=null;stage=1}); Spacer(Modifier.width(10.dp)); Column { Text(if(caravan) "رزرو کاروانی" else "رزرو خانوادگی / فردی", fontWeight=FontWeight.Medium); Text(if(caravan) "چند واحد فقط از یک مجموعه" else "یک واحد به یک خانواده اختصاص می‌یابد", style=MaterialTheme.typography.bodySmall, color=TextMuted) } } }
            item { Button(onClick={searched=true;selected=null;stage=2}, enabled=dateOk&&people>0, modifier=Modifier.fillMaxWidth()) { Text("پیشنهاد واحدهای خالی") } }
            if (searched) {
                item { StepHeader("۲", "انتخاب پیشنهاد", "هر واحد در یک بازه فقط به یک خانواده یا کاروان اختصاص دارد.") }
                if (suggestions.isEmpty()) item { EmptyCard("هیچ واحد یا ترکیب خالی با ظرفیت کافی پیدا نشد.") }
                if (caravan && dateOk) item {
                    val originalCap = cap["original"] ?: 0; val fatCap = cap["fatemiyeh"] ?: 0
                    InfoCard("ظرفیت آزاد این بازه", "زائرسرا: $originalCap نفر • فاطمیه: $fatCap نفر${if (cap["apartment"] == null) " • آپارتمان خالی موجود است" else ""}")
                }
                itemsIndexed(suggestions) { index, s -> SuggestionCard(s, selected==s, {
                    selected=s; familyNames=s.units.associate { it.id to "" }; allocationTexts=s.units.zip(s.allocations).associate { it.first.id to it.second.toString() }
                }, index==0) }
                if (selected != null) item {
                    Button(onClick={stage=3}, modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.ArrowForward, null); Spacer(Modifier.width(8.dp)); Text("ادامه و ثبت اطلاعات") }
                }
            }
            if (selected != null && stage>=3) {
                item { StepHeader("۳", "اطلاعات رزرو", "برای هر واحد فقط نام خانوادگی و تعداد نفرات لازم است.") }
                if (caravan) item { OutlinedTextField(title,{title=it},label={Text("عنوان کاروان (اختیاری)")},modifier=Modifier.fillMaxWidth()) }
                items(selected!!.units) { u ->
                    val maxCap = if (u.unitGroup=="apartment") null else u.capacity
                    Card(colors=CardDefaults.cardColors(containerColor=NavyCard2), shape=RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(u.name, fontWeight=FontWeight.Bold, color=PurpleSoft)
                            OutlinedTextField(familyNames[u.id].orEmpty(), { v -> familyNames=familyNames.toMutableMap().also{it[u.id]=v} }, label={Text("نام خانوادگی اصلی")}, modifier=Modifier.fillMaxWidth(), singleLine=true)
                            OutlinedTextField(allocationTexts[u.id].orEmpty(), { v -> allocationTexts=allocationTexts.toMutableMap().also{it[u.id]=normalizeNumeric(v,3)} }, label={Text("تعداد نفرات")}, supportingText={Text(if(maxCap==null) "بدون محدودیت ظرفیت" else "ظرفیت واحد: $maxCap نفر")}, modifier=Modifier.fillMaxWidth(), singleLine=true)
                            OutlinedButton(onClick={editingUnitId=u.id}, modifier=Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Group,null); Spacer(Modifier.width(6.dp));
                                Text(if((unitGuests[u.id] ?: emptyList()).isEmpty()) "ثبت مشخصات نفر اصلی و همراهان" else "مشخصات افراد (${unitGuests[u.id]!!.size})")
                            }
                        }
                    }
                }
                item { OutlinedTextField(leader,{leader=it},label={Text("نام سرپرست (اختیاری)")},modifier=Modifier.fillMaxWidth(),singleLine=true) }
                item { OutlinedTextField(phone,{phone=normalizeNumeric(it,11)},label={Text("شماره موبایل سرپرست (اختیاری)")},modifier=Modifier.fillMaxWidth(),singleLine=true) }
                item { Row(verticalAlignment=Alignment.CenterVertically) { Switch(paid,{paid=it}); Spacer(Modifier.width(10.dp)); Text(if(paid) "اقامت پولی" else "اقامت رایگان",fontWeight=FontWeight.Medium) } }
                if (paid) item { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedTextField(amount,{amount=normalizeNumeric(it)},label={Text("مبلغ کل (تومان)")},modifier=Modifier.fillMaxWidth(),singleLine=true); Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { FilterChip(selected=paymentStatus=="پرداخت شده",onClick={paymentStatus="پرداخت شده"},label={Text("پرداخت شده")}); FilterChip(selected=paymentStatus=="بدهکار",onClick={paymentStatus="بدهکار"},label={Text("بدهکار")}) } } }
                item { OutlinedTextField(notes,{notes=it},label={Text("توضیحات")},modifier=Modifier.fillMaxWidth(),minLines=2) }
                val s=selected!!
                val plan=s.units.map { u -> PlanUnit(u.id, allocationTexts[u.id]?.toIntOrNull() ?: 0, familyNames[u.id].orEmpty().trim(), unitGuests[u.id] ?: emptyList()) }
                val namesOk=plan.all{it.familyLastName.isNotBlank()}; val countsOk=plan.all { p0 -> p0.guestCount>0 && (s.units.first{it.id==p0.unitId}.unitGroup=="apartment" || p0.guestCount<=s.units.first{it.id==p0.unitId}.capacity) }
                val totalOk=plan.sumOf{it.guestCount}==people; val amountOk=!paid || (amount.toLongOrNull()?:0)>0
                item {
                    if(!totalOk) Text("جمع تعداد نفرات واحدها باید دقیقاً $people نفر باشد.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                    Button(onClick={
                        val autoTitle = title.trim().ifBlank { if (plan.size==1) "خانواده ${plan.first().familyLastName}" else "رزرو کاروانی" }
                        vm.createBooking(autoTitle,startDate.toString(),endDate.toString(),if(caravan)"caravan" else "family",leader.trim(),phone,paid,amount.toLongOrNull()?:0L,if(paid)paymentStatus else "رایگان",notes.trim(),plan,emptyList(),onDone)
                    }, enabled=namesOk&&countsOk&&totalOk&&amountOk, modifier=Modifier.fillMaxWidth()) { Text("ثبت نهایی رزرو") }
                }
            }
        }
    }
    val editUnit = selected?.units?.firstOrNull { it.id == editingUnitId }
    if (editUnit != null) NewUnitGuestsDialog(
        unit = editUnit,
        familyLastName = familyNames[editUnit.id].orEmpty(),
        initial = unitGuests[editUnit.id] ?: emptyList(),
        onDismiss = { editingUnitId = null },
        onSave = { list -> unitGuests = unitGuests.toMutableMap().also { it[editUnit.id] = list }; editingUnitId = null }
    )
    if (showStartPicker) PersianDateDialog(startDate ?: LocalDate.now(), LocalDate.now(), "تاریخ ورود", { showStartPicker=false }) {
        startDate=it; if(endDate!=null && !endDate!!.isAfter(it)) endDate=null; searched=false;selected=null;stage=1;showStartPicker=false;showEndPicker=true
    }
    if (showEndPicker) PersianDateDialog(endDate ?: (startDate ?: LocalDate.now()).plusDays(1), (startDate ?: LocalDate.now()).plusDays(1), "تاریخ خروج", { showEndPicker=false }) {
        endDate=it;searched=false;selected=null;stage=1;showEndPicker=false
    }
}

@Composable
private fun NewUnitGuestsDialog(unit:UnitItem,familyLastName:String,initial:List<GuestInput>,onDismiss:()->Unit,onSave:(List<GuestInput>)->Unit){
    var guests by remember(unit.id){mutableStateOf(initial)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("${unit.name} • مشخصات افراد")},text={
        LazyColumn(Modifier.heightIn(max=520.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            item{InfoCard("ثبت اختیاری","برای ثبت رزرو فقط نام خانوادگی و تعداد نفرات واحد الزامی است. اطلاعات نفر اصلی و همراهان برای گزارش‌گیری دقیق‌تر اختیاری است.")}
            itemsIndexed(guests){i,g->Card(colors=CardDefaults.cardColors(containerColor=NavyCard2),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                OutlinedTextField(g.firstName,{v->guests=guests.toMutableList().also{it[i]=g.copy(firstName=v)}},label={Text(if(i==0)"نام نفر اصلی" else "نام همراه")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(g.lastName,{v->guests=guests.toMutableList().also{it[i]=g.copy(lastName=v)}},label={Text("نام خانوادگی")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(g.nationalId,{v->guests=guests.toMutableList().also{it[i]=g.copy(nationalId=normalizeNumeric(v,10))}},label={Text("کد ملی (اختیاری)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(g.phone,{v->guests=guests.toMutableList().also{it[i]=g.copy(phone=normalizeNumeric(v,11))}},label={Text("موبایل (اختیاری)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                TextButton(onClick={guests=guests.toMutableList().also{it.removeAt(i)}}){Text("حذف فرد",color=Pink)}
            }}}
            item{OutlinedButton(onClick={guests=guests+GuestInput("",familyLastName,"","")},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.PersonAdd,null);Spacer(Modifier.width(6.dp));Text(if(guests.isEmpty())"افزودن نفر اصلی" else "افزودن همراه")}}
        }
    },confirmButton={Button(onClick={onSave(guests)},enabled=guests.all{it.lastName.isNotBlank()&&(it.nationalId.isBlank()||it.nationalId.length==10)}){Text("ثبت اطلاعات افراد")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})
}

@Composable
private fun SuggestionCard(s: UnitSuggestion, selected: Boolean, onSelect: () -> Unit, best: Boolean) {
    val bg by animateColorAsState(if(selected) Color(0xFF2B2450) else NavyCard, label="suggestion")
    Card(onClick=onSelect, modifier=Modifier.fillMaxWidth().animateContentSize(), colors=CardDefaults.cardColors(containerColor=bg), shape=RoundedCornerShape(22.dp), border=BorderStroke(1.dp, if(selected) Purple else Color.White.copy(alpha=.06f))) {
        Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { Text(groupName(s.group),fontWeight=FontWeight.Bold); if(best) SuggestionBadge("پیشنهاد بهتر") }
            s.units.zip(s.allocations).forEach { (u,n) -> Text(if(u.unitGroup=="apartment") "${u.name} • $n نفر • بدون محدودیت ظرفیت" else "${u.name} • $n نفر از ظرفیت ${u.capacity}") }
            if(selected) Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle,null,tint=Mint,modifier=Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("انتخاب شد",color=Mint,fontWeight=FontWeight.Bold) }
        }
    }
}

@Composable
fun ReservationDetailsScreen(vm: AppViewModel, reservationId: String, onBack: () -> Unit) {
    val rs by vm.reservations.collectAsState(); val units by vm.units.collectAsState()
    val anchor = rs.firstOrNull { it.id==reservationId }
    if(anchor==null) { Scaffold(containerColor=Navy,topBar={DarkTopBar("جزئیات رزرو",onBack)}){p->Box(Modifier.padding(p).fillMaxSize(),contentAlignment=Alignment.Center){Text("رزرو پیدا نشد")}}; return }
    val gid=anchor.bookingGroupId.ifBlank{anchor.id}; val rows=rs.filter{(it.bookingGroupId.ifBlank{it.id})==gid}; val first=rows.first()
    var edit by remember(gid) { mutableStateOf(false) }; var showCancel by remember { mutableStateOf(false) }; var showExtend by remember { mutableStateOf(false) }; var showAdd by remember { mutableStateOf(false) }
    var title by remember(gid,first.title) { mutableStateOf(first.title) }; var leader by remember(gid,first.leaderName){mutableStateOf(first.leaderName)}; var phone by remember(gid,first.leaderPhone){mutableStateOf(first.leaderPhone)}
    var paid by remember(gid,first.isPaid){mutableStateOf(first.isPaid)}; var amount by remember(gid,rows){mutableStateOf(rows.sumOf{it.amount}.toString())}; var paymentStatus by remember(gid,first.paymentStatus){mutableStateOf(first.paymentStatus)}; var notes by remember(gid,first.notes){mutableStateOf(first.notes)}
    var start by remember(gid,first.startDate){mutableStateOf(LocalDate.parse(first.startDate))}; var end by remember(gid,first.endDate){mutableStateOf(LocalDate.parse(first.endDate))}; var showStart by remember{mutableStateOf(false)}; var showEnd by remember{mutableStateOf(false)}

    Scaffold(containerColor=Navy,topBar={DarkTopBar("جزئیات رزرو",onBack)}) { p ->
        LazyColumn(Modifier.padding(p).fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=32.dp)) {
            item {
                Card(colors=CardDefaults.cardColors(containerColor=NavyCard2),shape=RoundedCornerShape(24.dp)) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Text(first.title.ifBlank { if(first.reservationType=="caravan") "رزرو کاروانی" else "خانواده ${first.primaryLastName}" },style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    Text("ورود ${JalaliCalendar.fromGregorian(start).display()}  •  خروج ${JalaliCalendar.fromGregorian(end).display()}",color=TextMuted)
                    Text("${rows.sumOf{it.guestCount}} نفر • ${rows.size} واحد",color=PurpleSoft,fontWeight=FontWeight.Medium)
                } }
            }
            if(edit) {
                item { OutlinedTextField(title,{title=it},label={Text("عنوان رزرو")},modifier=Modifier.fillMaxWidth()) }
                item { Card(onClick={showStart=true},colors=CardDefaults.cardColors(containerColor=NavyCard2)){Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("تاریخ ورود و خروج");Text("${JalaliCalendar.fromGregorian(start).display()} ← ${JalaliCalendar.fromGregorian(end).display()}",color=PurpleSoft)}} }
                item { OutlinedTextField(leader,{leader=it},label={Text("نام سرپرست")},modifier=Modifier.fillMaxWidth()) }
                item { OutlinedTextField(phone,{phone=normalizeNumeric(it,11)},label={Text("شماره تماس")},modifier=Modifier.fillMaxWidth()) }
                item { Row(verticalAlignment=Alignment.CenterVertically){Switch(paid,{paid=it});Spacer(Modifier.width(8.dp));Text(if(paid)"اقامت پولی" else "اقامت رایگان")} }
                if(paid) item { OutlinedTextField(amount,{amount=normalizeNumeric(it)},label={Text("مبلغ کل")},modifier=Modifier.fillMaxWidth()) }
                item { OutlinedTextField(notes,{notes=it},label={Text("توضیحات")},modifier=Modifier.fillMaxWidth(),minLines=2) }
                item { Button(onClick={vm.updateBookingMeta(gid,title,start.toString(),end.toString(),leader,phone,paid,amount.toLongOrNull()?:0,if(paid)paymentStatus else "رایگان",notes){edit=false}},modifier=Modifier.fillMaxWidth()){Text("ذخیره اطلاعات رزرو")} }
            }
            item { Text("واحدهای رزرو",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold) }
            items(rows) { r -> ReservationUnitEditCard(vm,r,units,start,end,editable=edit,canRemove=rows.size>1) }
            if(edit && first.reservationType=="caravan") item { OutlinedButton(onClick={showAdd=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Add,null);Spacer(Modifier.width(6.dp));Text("افزودن واحد به کاروان")} }
            if(!edit) {
                item {
                    val checkedIn = first.checkInAt.isNotBlank() && first.checkInAt != "null"
                    val checkedOut = first.checkOutAt.isNotBlank() && first.checkOutAt != "null"
                    if(!checkedIn) Button(onClick={vm.markBookingStatus(gid,"check_in")},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Mint,contentColor=Navy)){Icon(Icons.Default.Login,null);Spacer(Modifier.width(7.dp));Text("ورود انجام شد",fontWeight=FontWeight.Bold)}
                    else if(!checkedOut) Button(onClick={vm.markBookingStatus(gid,"check_out")},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Gold,contentColor=Navy)){Icon(Icons.Default.Logout,null);Spacer(Modifier.width(7.dp));Text("خروج انجام شد",fontWeight=FontWeight.Bold)}
                    else InfoCard("وضعیت اقامت","ورود و خروج این رزرو ثبت شده است.")
                }
                item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Button(onClick={edit=true},modifier=Modifier.weight(1f)){Icon(Icons.Default.Edit,null);Spacer(Modifier.width(6.dp));Text("ویرایش")}; OutlinedButton(onClick={showExtend=true},modifier=Modifier.weight(1f)){Icon(Icons.Default.DateRange,null);Spacer(Modifier.width(6.dp));Text("تمدید")} } }
                item { OutlinedButton(onClick={showCancel=true},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error)){Icon(Icons.Default.Delete,null);Spacer(Modifier.width(6.dp));Text("لغو رزرو")} }
            }
        }
    }
    if(showCancel) AlertDialog(onDismissRequest={showCancel=false},title={Text("لغو رزرو")},text={Text("رزرو حذف نمی‌شود و فقط به حالت لغوشده می‌رود. مطمئن هستید؟")},confirmButton={Button(onClick={showCancel=false;vm.cancelBooking(gid,onBack)},colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)){Text("لغو رزرو")}},dismissButton={TextButton(onClick={showCancel=false}){Text("انصراف")}})
    if(showStart) PersianDateDialog(start,LocalDate.now().minusYears(2),"تاریخ ورود",{showStart=false}){start=it;if(!end.isAfter(start))end=start.plusDays(1);showStart=false;showEnd=true}
    if(showEnd) PersianDateDialog(end,start.plusDays(1),"تاریخ خروج",{showEnd=false}){end=it;showEnd=false}
    if(showExtend) PersianDateDialog(end,end.plusDays(1),"تمدید تا تاریخ",{showExtend=false}){newEnd->showExtend=false;vm.updateBookingMeta(gid,first.title,first.startDate,newEnd.toString(),first.leaderName,first.leaderPhone,first.isPaid,rows.sumOf{it.amount},first.paymentStatus,first.notes)}
    if(showAdd) AddUnitDialog(vm,gid,first.unitGroup,start,end,units,{showAdd=false})
}

@Composable
private fun ReservationUnitEditCard(vm: AppViewModel, r: Reservation, units: List<UnitItem>, start: LocalDate, end: LocalDate, editable: Boolean, canRemove: Boolean) {
    var surname by remember(r.id,r.primaryLastName){mutableStateOf(r.primaryLastName)}; var count by remember(r.id,r.guestCount){mutableStateOf(r.guestCount.toString())}; var selectedUnit by remember(r.id,r.unitId){mutableStateOf(r.unitId)}; var chooser by remember{mutableStateOf(false)}; var removeConfirm by remember{mutableStateOf(false)}; var showGuests by remember{mutableStateOf(false)}
    val u=units.firstOrNull{it.id==selectedUnit}; val valid=(count.toIntOrNull()?:0)>0 && surname.isNotBlank() && (u?.unitGroup=="apartment" || (count.toIntOrNull()?:0) <= (u?.capacity?:0))
    Card(colors=CardDefaults.cardColors(containerColor=NavyCard),shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,Color.White.copy(alpha=.06f))) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Text(u?.name ?: r.unitName,fontWeight=FontWeight.Bold,color=PurpleSoft)
            if(!editable) { Text("خانواده: ${r.primaryLastName.ifBlank{"—"}}");Text("${r.guestCount} نفر",color=TextMuted); OutlinedButton(onClick={showGuests=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Group,null);Spacer(Modifier.width(6.dp));Text("مشخصات نفر اصلی و همراهان")} }
            else {
                OutlinedTextField(surname,{surname=it},label={Text("نام خانوادگی اصلی")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(count,{count=normalizeNumeric(it,3)},label={Text("تعداد نفرات")},supportingText={Text(if(u?.unitGroup=="apartment")"بدون محدودیت ظرفیت" else "ظرفیت: ${u?.capacity ?: 0}")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedButton(onClick={chooser=true},modifier=Modifier.fillMaxWidth()){Text("تغییر واحد")}
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={vm.updateBookingUnit(r.id,selectedUnit,count.toIntOrNull()?:0,surname)},enabled=valid,modifier=Modifier.weight(1f)){Text("ذخیره واحد")}
                    if(canRemove) OutlinedButton(onClick={removeConfirm=true},modifier=Modifier.weight(1f),colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text("حذف واحد")}
                }
                OutlinedButton(onClick={showGuests=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Group,null);Spacer(Modifier.width(6.dp));Text("مشخصات نفر اصلی و همراهان")}
            }
        }
    }
    if(chooser) UnitChooserDialog(units.filter{it.unitGroup==r.unitGroup && (it.id==r.unitId || vm.isUnitFree(it.id,start,end))},selectedUnit,{chooser=false},{selectedUnit=it;chooser=false})
    if(removeConfirm) AlertDialog(onDismissRequest={removeConfirm=false},text={Text("این واحد از رزرو کاروانی حذف شود؟")},confirmButton={Button(onClick={removeConfirm=false;vm.removeBookingUnit(r.id)},colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)){Text("حذف")}},dismissButton={TextButton(onClick={removeConfirm=false}){Text("انصراف")}})
    if(showGuests) ReservationGuestsDialog(vm,r,{showGuests=false})
}

@Composable
private fun AddUnitDialog(vm: AppViewModel, gid: String, group: String, start: LocalDate, end: LocalDate, units: List<UnitItem>, onDismiss: () -> Unit) {
    val free=units.filter{it.unitGroup==group && vm.isUnitFree(it.id,start,end)}; var selected by remember{mutableStateOf(free.firstOrNull()?.id.orEmpty())}; var surname by remember{mutableStateOf("")}; var count by remember{mutableStateOf("")}; val u=free.firstOrNull{it.id==selected}
    AlertDialog(onDismissRequest=onDismiss,title={Text("افزودن واحد")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){if(free.isEmpty())Text("واحد خالی دیگری در این مجموعه وجود ندارد.") else {Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){free.forEach{FilterChip(selected=selected==it.id,onClick={selected=it.id},label={Text(it.name)})}};OutlinedTextField(surname,{surname=it},label={Text("نام خانوادگی")},modifier=Modifier.fillMaxWidth());OutlinedTextField(count,{count=normalizeNumeric(it,3)},label={Text("تعداد نفرات")},supportingText={Text(if(u?.unitGroup=="apartment")"بدون محدودیت" else "ظرفیت: ${u?.capacity ?: 0}")},modifier=Modifier.fillMaxWidth())}}},confirmButton={val n=count.toIntOrNull()?:0;Button(onClick={vm.addBookingUnit(gid,selected,n,surname,onDismiss)},enabled=selected.isNotBlank()&&surname.isNotBlank()&&n>0&&(u?.unitGroup=="apartment"||n<=(u?.capacity?:0))){Text("افزودن")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})
}

@Composable
private fun UnitChooserDialog(units: List<UnitItem>, selected: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text("انتخاب واحد")},text={LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.heightIn(max=360.dp)){items(units){u->Card(onClick={onSelect(u.id)},colors=CardDefaults.cardColors(containerColor=if(selected==u.id)Color(0xFF30265A)else NavyCard2)){Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(u.name);Text(if(u.unitGroup=="apartment")"نامحدود" else "${u.capacity} نفر",color=TextMuted)}}}}},confirmButton={TextButton(onClick=onDismiss){Text("بستن")}})
}

@Composable
fun ReportsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val rs by vm.reservations.collectAsState()
    val units by vm.units.collectAsState()
    var draftFrom by remember { mutableStateOf<LocalDate?>(null) }
    var draftTo by remember { mutableStateOf<LocalDate?>(null) }
    var draftGroup by remember { mutableStateOf("all") }
    var appliedFrom by remember { mutableStateOf<LocalDate?>(null) }
    var appliedTo by remember { mutableStateOf<LocalDate?>(null) }
    var appliedGroup by remember { mutableStateOf("all") }
    var generated by remember { mutableStateOf(false) }
    var showFrom by remember { mutableStateOf(false) }
    var showTo by remember { mutableStateOf(false) }

    val filtered = if (!generated) emptyList() else rs.filter { r ->
        val s = runCatching { LocalDate.parse(r.startDate) }.getOrNull() ?: return@filter false
        val e = runCatching { LocalDate.parse(r.endDate) }.getOrNull() ?: return@filter false
        (appliedFrom == null || e.isAfter(appliedFrom)) &&
            (appliedTo == null || !s.isAfter(appliedTo)) &&
            (appliedGroup == "all" || r.unitGroup == appliedGroup)
    }
    val bookings = filtered.map { it.bookingGroupId.ifBlank { it.id } }.distinct().size
    val guests = filtered.sumOf { it.guestCount }
    val revenue = filtered.filter { it.isPaid }.sumOf { it.amount }
    val received = filtered.filter { it.isPaid && it.paymentStatus == "پرداخت شده" }.sumOf { it.amount }
    val debt = filtered.filter { it.isPaid && it.paymentStatus != "پرداخت شده" }.sumOf { it.amount }

    Scaffold(containerColor = Navy, topBar = { DarkTopBar("گزارش‌ها", onBack) }) { p ->
        LazyColumn(
            Modifier.padding(p).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item { Text("فیلتر گزارش", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showFrom = true }, modifier = Modifier.weight(1f)) { Text(draftFrom?.let { JalaliCalendar.fromGregorian(it).display() } ?: "از تاریخ") }
                    OutlinedButton(onClick = { showTo = true }, modifier = Modifier.weight(1f)) { Text(draftTo?.let { JalaliCalendar.fromGregorian(it).display() } ?: "تا تاریخ") }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("all" to "همه مکان‌ها", "original" to "زائرسرا", "fatemiyeh" to "فاطمیه", "apartment" to "آپارتمان").forEach { (k, l) ->
                        FilterChip(selected = draftGroup == k, onClick = { draftGroup = k }, label = { Text(l) })
                    }
                }
            }
            item {
                Button(onClick = { appliedFrom = draftFrom; appliedTo = draftTo; appliedGroup = draftGroup; generated = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("نمایش گزارش")
                }
            }
            if (!generated) item { EmptyCard("بازه و مکان را انتخاب کنید و «نمایش گزارش» را بزنید.") }
            if (generated) {
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MetricCard("رزرو", bookings.toString(), Modifier.weight(1f)); MetricCard("نفر", guests.toString(), Modifier.weight(1f)) } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MetricCard("مبلغ کل", formatMoney(revenue), Modifier.weight(1f)); MetricCard("وصول", formatMoney(received), Modifier.weight(1f)) } }
                item { MetricCard("بدهی", formatMoney(debt), Modifier.fillMaxWidth()) }
                item { Text("گزارش مکانی", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                val locationRows = units.map { u -> u to filtered.filter { it.unitId == u.id } }.filter { it.second.isNotEmpty() }
                if (locationRows.isEmpty()) item { EmptyCard("در این بازه گزارشی وجود ندارد.") }
                items(locationRows) { (u, urs) ->
                    Card(colors = CardDefaults.cardColors(containerColor = NavyCard), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(u.name, fontWeight = FontWeight.Bold)
                                Text("${urs.sumOf { it.guestCount }} نفر", color = PurpleSoft)
                            }
                            urs.forEach { r ->
                                Text(
                                    "خانواده ${r.primaryLastName.ifBlank { "—" }} • ${r.guestCount} نفر • ${JalaliCalendar.isoToJalali(r.startDate)} تا ${JalaliCalendar.isoToJalali(r.endDate)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                            if (urs.any { it.isPaid }) Text("${formatMoney(urs.filter { it.isPaid }.sumOf { it.amount })} تومان", color = Gold)
                        }
                    }
                }
            }
        }
    }
    if (showFrom) PersianDateDialog(draftFrom ?: LocalDate.now(), LocalDate.of(2020, 1, 1), "از تاریخ", { showFrom = false }) { draftFrom = it; showFrom = false }
    if (showTo) PersianDateDialog(draftTo ?: LocalDate.now(), LocalDate.of(2020, 1, 1), "تا تاریخ", { showTo = false }) { draftTo = it; showTo = false }
}

@Composable
fun UnitsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val units by vm.units.collectAsState()
    Scaffold(containerColor = Navy, topBar = { DarkTopBar("واحدها", onBack) }) { p ->
        LazyColumn(Modifier.padding(p).fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { InfoCard("واحدها", "ظرفیت‌ها ثابت و فقط نمایشی هستند. آپارتمان‌ها محدودیت تعداد نفر ندارند.") }
            items(units) { u -> Card(colors=CardDefaults.cardColors(containerColor=NavyCard),shape=RoundedCornerShape(20.dp)){ Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.SpaceBetween){ Column{Text(u.name,fontWeight=FontWeight.Bold);Text(groupName(u.unitGroup),color=PurpleSoft)}; Text(if(u.unitGroup=="apartment") "بدون محدودیت" else "${u.capacity} نفر",color=Mint,fontWeight=FontWeight.Bold) } } }
        }
    }
}

@Composable
fun PeopleScreen(vm: AppViewModel, onBack: () -> Unit) {
    var mode by remember { mutableStateOf("national") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PersonLookup>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    val label = when (mode) { "phone" -> "شماره موبایل"; "last_name" -> "نام خانوادگی"; else -> "کد ملی" }

    Scaffold(containerColor = Navy, topBar = { DarkTopBar("سوابق زائر", onBack) }) { p ->
        LazyColumn(Modifier.padding(p).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { InfoCard("جستجوی سابقه", "با کد ملی، شماره موبایل یا بخشی از نام خانوادگی جست‌وجو کنید.") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = mode == "national", onClick = { mode = "national"; query = ""; results = emptyList() }, label = { Text("کد ملی") })
                    FilterChip(selected = mode == "phone", onClick = { mode = "phone"; query = ""; results = emptyList() }, label = { Text("موبایل") })
                    FilterChip(selected = mode == "last_name", onClick = { mode = "last_name"; query = ""; results = emptyList() }, label = { Text("نام خانوادگی") })
                }
            }
            item {
                OutlinedTextField(
                    query,
                    { query = if (mode == "last_name") it else normalizeNumeric(it, if (mode == "national") 10 else 11) },
                    label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }
            val valid = when (mode) { "national" -> query.length == 10; "phone" -> query.length >= 7; else -> query.trim().length >= 2 }
            item { Button(onClick = { searched = true; vm.searchPeople(mode, query) { results = it } }, enabled = valid, modifier = Modifier.fillMaxWidth()) { Text("جست‌وجو") } }
            if (searched && results.isEmpty()) item { EmptyCard("سابقه‌ای پیدا نشد.") }
            items(results) { person ->
                Card(colors = CardDefaults.cardColors(containerColor = NavyCard), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("${person.firstName} ${person.lastName}", fontWeight = FontWeight.Bold)
                        if (person.phone.isNotBlank()) Text("موبایل: ${person.phone}")
                        if (person.nationalId.isNotBlank() && person.nationalId != "null") Text("کد ملی: ${person.nationalId}")
                        Text("تعداد اقامت: ${person.visitCount}", color = PurpleSoft)
                        if (person.lastDeparture.isNotBlank() && person.lastDeparture != "null") {
                            Text("آخرین خروج: ${runCatching { JalaliCalendar.isoToJalali(person.lastDeparture) }.getOrDefault(person.lastDeparture)}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReservationGuestsDialog(vm:AppViewModel,r:Reservation,onDismiss:()->Unit){
    var guests by remember(r.id){mutableStateOf<List<GuestInput>>(emptyList())};var loaded by remember(r.id){mutableStateOf(false)}
    LaunchedEffect(r.id){vm.reservationGuests(r.id){guests=it;loaded=true}}
    AlertDialog(onDismissRequest=onDismiss,title={Text("${r.unitName} • خانواده ${r.primaryLastName}")},text={
        LazyColumn(Modifier.heightIn(max=520.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            item{Text("ثبت همراهان اختیاری است. نام خانوادگی واحد و تعداد نفرات برای خود رزرو کافی است.",style=MaterialTheme.typography.bodySmall,color=TextMuted)}
            if(!loaded)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
            itemsIndexed(guests){i,g->Card(colors=CardDefaults.cardColors(containerColor=NavyCard2),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                OutlinedTextField(g.firstName,{v->guests=guests.toMutableList().also{it[i]=g.copy(firstName=v)}},label={Text(if(i==0)"نام نفر اصلی" else "نام همراه")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(g.lastName,{v->guests=guests.toMutableList().also{it[i]=g.copy(lastName=v)}},label={Text("نام خانوادگی")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(g.nationalId,{v->guests=guests.toMutableList().also{it[i]=g.copy(nationalId=normalizeNumeric(v,10))}},label={Text("کد ملی (اختیاری)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                OutlinedTextField(g.phone,{v->guests=guests.toMutableList().also{it[i]=g.copy(phone=normalizeNumeric(v,11))}},label={Text("موبایل (اختیاری)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                TextButton(onClick={guests=guests.toMutableList().also{it.removeAt(i)}}){Text("حذف فرد",color=Pink)}
            }}}
            item{OutlinedButton(onClick={guests=guests+GuestInput("",r.primaryLastName,"","")},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.PersonAdd,null);Spacer(Modifier.width(6.dp));Text(if(guests.isEmpty())"ثبت مشخصات نفر اصلی" else "افزودن همراه")}}
        }
    },confirmButton={Button(onClick={vm.setReservationGuests(r.id,guests){onDismiss()}},enabled=guests.all{it.lastName.isNotBlank()&&(it.nationalId.isBlank()||it.nationalId.length==10)}){Text("ذخیره مشخصات")}},dismissButton={TextButton(onClick=onDismiss){Text("بستن")}})
}

@Composable
fun TodayScreen(vm:AppViewModel,initialMode:String,onBack:()->Unit,onDetail:(String)->Unit){
    val rs by vm.reservations.collectAsState();val today=LocalDate.now();var mode by remember{mutableStateOf(initialMode)}
    fun dateActive(r:Reservation)=runCatching{!today.isBefore(LocalDate.parse(r.startDate))&&today.isBefore(LocalDate.parse(r.endDate))}.getOrDefault(false)
    val filtered=when(mode){
        "arrivals"->rs.filter{it.startDate==today.toString()}
        "departures"->rs.filter{it.endDate==today.toString()}
        "present"->rs.filter{(it.checkInAt.isNotBlank()&&it.checkInAt!="null"&&(it.checkOutAt.isBlank()||it.checkOutAt=="null"))||dateActive(it)}
        "occupied"->rs.filter(::dateActive)
        "active"->rs
        else->rs.filter{it.startDate==today.toString()||it.endDate==today.toString()||dateActive(it)}
    }
    val groups=filtered.groupBy{it.bookingGroupId.ifBlank{it.id}}.values.sortedBy{it.minOfOrNull{r->r.startDate}}
    Scaffold(containerColor=Navy,topBar={DarkTopBar("عملیات امروز",onBack)}){p->LazyColumn(Modifier.padding(p).fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=30.dp)){
        item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("all" to "همه","arrivals" to "ورودی","departures" to "خروجی","present" to "حاضر","active" to "فعال").forEach{(k,l)->FilterChip(selected=mode==k,onClick={mode=k},label={Text(l)})}}}
        if(groups.isEmpty())item{EmptyCard("موردی برای این بخش وجود ندارد.")}
        items(groups){rows->BookingCard(rows){onDetail(rows.first().id)}}
    }}
}

@Composable
fun OccupancyScreen(vm:AppViewModel,onBack:()->Unit,onDetail:(String)->Unit){
    val units by vm.units.collectAsState();val rs by vm.reservations.collectAsState();var start by remember{mutableStateOf(LocalDate.now())};val days=(0..6).map{start.plusDays(it.toLong())}
    Scaffold(containerColor=Navy,topBar={DarkTopBar("تقویم اشغال واحدها",onBack)}){p->LazyColumn(Modifier.padding(p).fillMaxSize().padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(bottom=30.dp)){
        item{Card(colors=CardDefaults.cardColors(containerColor=NavyCard2),shape=RoundedCornerShape(20.dp)){Row(Modifier.fillMaxWidth().padding(10.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){TextButton(onClick={start=start.minusDays(7)}){Text("هفته قبل")};Text("${JalaliCalendar.fromGregorian(start).display()} تا ${JalaliCalendar.fromGregorian(start.plusDays(6)).display()}",fontWeight=FontWeight.Bold);TextButton(onClick={start=start.plusDays(7)}){Text("هفته بعد")}}}}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp)){Spacer(Modifier.width(88.dp));days.forEach{d->Text(JalaliCalendar.fromGregorian(d).day.toString(),modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=TextMuted)}}}
        items(units){u->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)){Text(u.name,modifier=Modifier.width(88.dp),style=MaterialTheme.typography.bodySmall,maxLines=2);days.forEach{d->val r=rs.firstOrNull{x->x.unitId==u.id&&runCatching{!d.isBefore(LocalDate.parse(x.startDate))&&d.isBefore(LocalDate.parse(x.endDate))}.getOrDefault(false)};Surface(onClick={if(r!=null){{onDetail(r.id)}}else{{}}},modifier=Modifier.weight(1f).height(38.dp),shape=RoundedCornerShape(8.dp),color=if(r==null)Mint.copy(alpha=.16f) else Pink.copy(alpha=.22f)){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Icon(if(r==null)Icons.Default.Check else Icons.Default.Hotel,null,tint=if(r==null)Mint else Pink,modifier=Modifier.size(17.dp))}}}}}
        item{InfoCard("راهنما","سبز = آزاد • صورتی = رزرو. روی خانه رزروشده بزنید تا جزئیات باز شود. روز خروج آزاد محسوب می‌شود.")}
    }}
}

@Composable
fun GlobalSearchScreen(vm:AppViewModel,onBack:()->Unit,onDetail:(String)->Unit){
    val rs by vm.reservations.collectAsState()
    var query by remember{mutableStateOf("")}
    var people by remember{mutableStateOf<List<PersonLookup>>(emptyList())}
    var searched by remember{mutableStateOf(false)}
    val local = if(query.trim().length < 2) emptyList() else rs
        .filter { r -> listOf(r.primaryLastName,r.title,r.leaderName,r.leaderPhone,r.unitName).any { it.contains(query.trim(),ignoreCase=true) } }
        .groupBy { it.bookingGroupId.ifBlank { it.id } }.values.toList()
    Scaffold(containerColor=Navy,topBar={DarkTopBar("جستجوی سراسری",onBack)}) { p ->
        LazyColumn(Modifier.padding(p).fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item { OutlinedTextField(query,{query=it},label={Text("نام، فامیل، موبایل یا کد ملی")},leadingIcon={Icon(Icons.Default.Search,null)},modifier=Modifier.fillMaxWidth(),singleLine=true) }
            item {
                Button(onClick={
                    searched=true
                    val q=normalizeNumeric(query)
                    val mode=when { q.length==10 -> "national"; q.length>=7 && q.all{it.isDigit()} -> "phone"; else -> "last_name" }
                    vm.searchPeople(mode,if(mode=="last_name")query.trim() else q){people=it}
                },enabled=query.trim().length>=2,modifier=Modifier.fillMaxWidth()){Text("جستجو")}
            }
            if(searched){
                item { Text("رزروها",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold) }
                if(local.isEmpty()) item { Text("رزروی پیدا نشد",color=TextMuted) }
                items(local){ rows -> BookingCard(rows){onDetail(rows.first().id)} }
                item { Text("زائران و سوابق",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold) }
                if(people.isEmpty()) item { Text("سابقه‌ای پیدا نشد",color=TextMuted) }
                items(people){ person ->
                    Card(colors=CardDefaults.cardColors(containerColor=NavyCard),shape=RoundedCornerShape(18.dp)){
                        Column(Modifier.padding(12.dp)){
                            Text("${person.firstName} ${person.lastName}",fontWeight=FontWeight.Bold)
                            if(person.phone.isNotBlank()) Text(person.phone,color=TextMuted)
                            Text("${person.visitCount} اقامت",color=PurpleSoft)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PersianDateDialog(initial:LocalDate,minDate:LocalDate,title:String,onDismiss:()->Unit,onSelect:(LocalDate)->Unit){val monthNames=listOf("فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند");val initialJ=JalaliCalendar.fromGregorian(if(initial.isBefore(minDate))minDate else initial);var year by remember{mutableIntStateOf(initialJ.year)};var month by remember{mutableIntStateOf(initialJ.month)};var day by remember{mutableIntStateOf(initialJ.day)};fun move(delta:Int){var m=month+delta;var y=year;if(m<1){m=12;y--};if(m>12){m=1;y++};year=y;month=m;day=day.coerceAtMost(JalaliCalendar.monthLength(y,m))};val selected=runCatching{JalaliCalendar.toGregorian(JalaliDate(year,month,day))}.getOrNull();val valid=selected!=null&&!selected.isBefore(minDate);AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){TextButton(onClick={move(-1)}){Text("ماه قبل")};Text("${monthNames[month-1]} $year",style=MaterialTheme.typography.titleMedium);TextButton(onClick={move(1)}){Text("ماه بعد")}};val max=JalaliCalendar.monthLength(year,month);Row(Modifier.fillMaxWidth()){listOf("ش","ی","د","س","چ","پ","ج").forEach{Text(it,modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)}};val firstGregorian=JalaliCalendar.toGregorian(JalaliDate(year,month,1));val offset=(firstGregorian.dayOfWeek.value+1)%7;val cells=List(offset){0}+(1..max).toList();cells.chunked(7).forEach{week->Row(Modifier.fillMaxWidth()){week.forEach{d->if(d==0)Spacer(Modifier.weight(1f))else{val g=runCatching{JalaliCalendar.toGregorian(JalaliDate(year,month,d))}.getOrNull();val enabled=g!=null&&!g.isBefore(minDate);TextButton(onClick={day=d},enabled=enabled,modifier=Modifier.weight(1f),contentPadding=PaddingValues(0.dp)){Text(if(d==day)"[$d]" else d.toString())}}};repeat(7-week.size){Spacer(Modifier.weight(1f))}}}}},confirmButton={Button(onClick={onSelect(selected!!)},enabled=valid){Text("انتخاب")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})}

@Composable private fun DarkTopBar(title:String,onBack:()->Unit){TopAppBar(title={Text(title,fontWeight=FontWeight.Bold)},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"بازگشت")}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Navy,titleContentColor=TextMain,navigationIconContentColor=TextMain))}
@Composable private fun Page(title:String,content:@Composable ColumnScope.()->Unit)=Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);content()}
@Composable private fun MetricCard(label:String,value:String,modifier:Modifier=Modifier)=Card(modifier.animateContentSize(),colors=CardDefaults.cardColors(containerColor=NavyCard2),shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(14.dp)){Text(value,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,color=PurpleSoft);Text(label,style=MaterialTheme.typography.bodySmall,color=TextMuted)}}
@Composable private fun InfoCard(title:String,text:String)=Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF252345)),shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(title,fontWeight=FontWeight.Bold,color=PurpleSoft);Text(text,style=MaterialTheme.typography.bodySmall,color=TextMuted)}}
@Composable private fun EmptyCard(text:String)=Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=NavyCard2),shape=RoundedCornerShape(20.dp)){Text(text,Modifier.padding(16.dp),style=MaterialTheme.typography.bodyMedium,color=TextMuted)}
@Composable private fun StepHeader(number:String,title:String,subtitle:String)=Row(verticalAlignment=Alignment.CenterVertically){Surface(shape=CircleShape,color=Purple){Text(number,Modifier.padding(horizontal=11.dp,vertical=6.dp),color=Color.White,fontWeight=FontWeight.Bold)};Spacer(Modifier.width(10.dp));Column{Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=TextMuted)}}
@Composable private fun SuggestionBadge(text:String)=Surface(shape=RoundedCornerShape(99.dp),color=Gold.copy(alpha=.18f)){Text(text,Modifier.padding(horizontal=9.dp,vertical=4.dp),style=MaterialTheme.typography.labelSmall,color=Gold)}
private fun groupName(group:String)=when(group){"fatemiyeh"->"فاطمیه";"apartment"->"آپارتمان‌ها";else->"زائرسرا"}
private fun formatMoney(v:Long):String=String.format("%,d",v)
