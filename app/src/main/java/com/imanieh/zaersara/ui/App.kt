@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.imanieh.zaersara.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
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

private val AppColors = lightColorScheme(
    primary = Color(0xFF315F72),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4EAF4),
    secondary = Color(0xFF6A5A3C),
    secondaryContainer = Color(0xFFF1E5C8),
    surface = Color(0xFFF8FAFB),
    surfaceVariant = Color(0xFFE9EEF1),
    error = Color(0xFFB3261E)
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
                    composable("config") { ConfigScreen(vm) { if (vm.loggedIn) nav.popBackStack() else nav.navigate("login") { popUpTo("config") { inclusive = true } } } }
                    composable("login") { LoginScreen(vm) { nav.navigate("home") { popUpTo("login") { inclusive = true } } } }
                    composable("home") { HomeScreen(vm, { nav.navigate("new") }, { nav.navigate("reports") }, { nav.navigate("units") }, { nav.navigate("people") }) }
                    composable("new") { NewReservationScreen(vm) { nav.popBackStack() } }
                    composable("reports") { ReportsScreen(vm) { nav.popBackStack() } }
                    composable("units") { UnitsScreen(vm) { nav.popBackStack() } }
                    composable("people") { PeopleScreen(vm) { nav.popBackStack() } }
                }
                if (busy) Surface(Modifier.align(Alignment.Center), shape = RoundedCornerShape(18.dp), tonalElevation = 6.dp) { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(28.dp)); Spacer(Modifier.width(12.dp)); Text("در حال پردازش…") } }
                if (msg != null) AlertDialog(onDismissRequest = { vm.clearMessage() }, confirmButton = { TextButton(onClick = { vm.clearMessage() }) { Text("باشه") } }, text = { Text(msg!!) })
            }
        }
    }
}

@Composable
fun ConfigScreen(vm: AppViewModel, onDone: () -> Unit) {
    var url by remember { mutableStateOf(vm.prefs.baseUrl) }
    var key by remember { mutableStateOf(vm.prefs.anonKey) }
    Page("تنظیم اتصال آنلاین") {
        InfoCard("اتصال به پایگاه داده", "آدرس اصلی پروژه Supabase و Publishable Key را وارد کنید.")
        OutlinedTextField(url, { url = it }, label = { Text("Supabase URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(key, { key = it }, label = { Text("Publishable / Anon key") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.saveConfig(url, key); onDone() }, enabled = url.isNotBlank() && key.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("ذخیره و ادامه") }
        Text("نمونه آدرس: https://xxxx.supabase.co", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
fun HomeScreen(vm: AppViewModel, onNew: () -> Unit, onReports: () -> Unit, onUnits: () -> Unit, onPeople: () -> Unit) {
    val units by vm.units.collectAsState()
    val rs by vm.reservations.collectAsState()
    val serverState by vm.serverState.collectAsState()
    val lastSync by vm.lastSync.collectAsState()
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(1000) } }
    val today = now.toLocalDate()
    val occupied = units.count { u -> rs.any { r -> r.unitId == u.id && runCatching { !today.isBefore(LocalDate.parse(r.startDate)) && today.isBefore(LocalDate.parse(r.endDate)) }.getOrDefault(false) } }
    val todayGuests = rs.filter { r -> runCatching { !today.isBefore(LocalDate.parse(r.startDate)) && today.isBefore(LocalDate.parse(r.endDate)) }.getOrDefault(false) }.sumOf { it.guestCount }
    val todayArrivals = rs.count { it.startDate == today.toString() }
    val j = JalaliCalendar.fromGregorian(today)
    val dayName = listOf("دوشنبه","سه‌شنبه","چهارشنبه","پنج‌شنبه","جمعه","شنبه","یکشنبه")[today.dayOfWeek.value - 1]
    val timeText = now.format(DateTimeFormatter.ofPattern("HH:mm"))

    Scaffold(
        topBar = { TopAppBar(
            title = { Column { Text("مدیریت زائرسرا مشهد", fontWeight = FontWeight.Bold); Text("داشبورد اقامت و رزرو", style = MaterialTheme.typography.labelMedium) } },
            actions = { ServerStatusBadge(serverState, lastSync) { vm.refresh() } }
        ) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onNew, modifier = Modifier.animateContentSize(), text = { Text("رزرو جدید") }, icon = { Text("+") }) }
    ) { p ->
        LazyColumn(Modifier.padding(p).fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Card(Modifier.fillMaxWidth().animateContentSize(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha=.55f))) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Text("$dayName، ${j.display()}", fontWeight = FontWeight.Bold); Text("امروز", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(timeText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item { Text("وضعیت امروز", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MetricCard("زائر حاضر", todayGuests.toString(), Modifier.weight(1f)); MetricCard("واحد اشغال", "$occupied / ${units.size}", Modifier.weight(1f)) } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MetricCard("رزرو فعال", rs.size.toString(), Modifier.weight(1f)); MetricCard("ورودی امروز", todayArrivals.toString(), Modifier.weight(1f)) } }
            item { Text("دسترسی سریع", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { QuickButton("گزارش‌ها", onReports, Modifier.weight(1f)); QuickButton("واحدها", onUnits, Modifier.weight(1f)); QuickButton("سوابق زائر", onPeople, Modifier.weight(1f)) } }
            item { Text("رزروهای نزدیک", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (rs.isEmpty()) item { EmptyCard("هنوز رزروی ثبت نشده است.") }
            items(rs.sortedBy { it.startDate }.take(20)) { r -> ReservationCard(r) }
        }
    }
}

@Composable
private fun ServerStatusBadge(state: String, lastSync: Long?, onRefresh: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "serverPulse")
    val pulse by transition.animateFloat(initialValue = .45f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "pulse")
    val color = when(state) { "connected" -> Color(0xFF2E7D32); "offline" -> MaterialTheme.colorScheme.error; "auth" -> Color(0xFFF57C00); else -> MaterialTheme.colorScheme.primary }
    val label = when(state) { "connected" -> "متصل"; "offline" -> "قطع"; "auth" -> "ورود مجدد"; else -> "اتصال…" }
    TextButton(onClick = onRefresh) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(Modifier.size(10.dp).alpha(if (state == "checking") pulse else 1f), shape = RoundedCornerShape(99.dp), color = color) {}
            Text(label, color = color, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ReservationCard(r: Reservation) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(r.title.ifBlank { if (r.reservationType == "caravan") "رزرو کاروان" else "رزرو" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                AssistChip(onClick = {}, label = { Text("${r.guestCount} نفر") })
            }
            Text(r.unitName, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            Text("${JalaliCalendar.isoToJalali(r.startDate)} تا ${JalaliCalendar.isoToJalali(r.endDate)}")
            if (r.reservationType == "caravan") Text("کاروانی${if (r.leaderName.isNotBlank()) " • سرپرست: ${r.leaderName}" else ""}", style = MaterialTheme.typography.bodySmall)
            Text(if (r.isPaid) "${formatMoney(r.amount)} تومان • ${r.paymentStatus}" else "رایگان", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun NewReservationScreen(vm: AppViewModel, onDone: () -> Unit) {
    val units by vm.units.collectAsState()
    val rs by vm.reservations.collectAsState()
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var peopleText by remember { mutableStateOf("") }
    var caravan by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<UnitSuggestion?>(null) }
    var title by remember { mutableStateOf("") }
    var leader by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var paid by remember { mutableStateOf(false) }
    var paymentStatus by remember { mutableStateOf("پرداخت شده") }
    var amount by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var guests by remember { mutableStateOf(listOf<GuestInput>()) }
    var fn by remember { mutableStateOf("") }
    var ln by remember { mutableStateOf("") }
    var nid by remember { mutableStateOf("") }
    var guestPhone by remember { mutableStateOf("") }
    var personNote by remember { mutableStateOf("") }
    val people = peopleText.toIntOrNull() ?: 0
    val dateOk = startDate != null && endDate != null && endDate!!.isAfter(startDate!!)
    val suggestions = remember(startDate, endDate, people, caravan, searched, units, rs) {
        if (searched && dateOk && people > 0) vm.suggestions(startDate!!, endDate!!, people, caravan) else emptyList()
    }

    LaunchedEffect(nid) {
        if (nid.length == 10) {
            vm.lookupPerson(nid) { p ->
                if (p != null) {
                    fn = p.firstName; ln = p.lastName; guestPhone = p.phone
                    personNote = if (p.visitCount > 0) "این زائر قبلاً ${p.visitCount} بار اقامت داشته است." else "مشخصات از سابقه بازیابی شد."
                } else personNote = "کد ملی جدید است."
            }
        } else personNote = ""
    }

    Scaffold(topBar = { TopAppBar(title = { Text("رزرو جدید", fontWeight = FontWeight.Bold) }, navigationIcon = { TextButton(onClick = onDone) { Text("بازگشت") } }) }) { p ->
        LazyColumn(Modifier.padding(p).fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { StepHeader("۱", "تاریخ و تعداد نفرات", "برنامه واحدهای خالی و مناسب را پیشنهاد می‌دهد.") }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f)) { Text(startDate?.let { JalaliCalendar.fromGregorian(it).display() } ?: "تاریخ ورود") }
                OutlinedButton(onClick = { showEndPicker = true }, enabled = startDate != null, modifier = Modifier.weight(1f)) { Text(endDate?.let { JalaliCalendar.fromGregorian(it).display() } ?: "تاریخ خروج") }
            } }
            item { OutlinedTextField(peopleText, { peopleText = normalizeNumeric(it, 3); searched = false; selected = null }, label = { Text("تعداد نفرات") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { Row(verticalAlignment = Alignment.CenterVertically) { Switch(caravan, { caravan = it; searched = false; selected = null }); Spacer(Modifier.width(10.dp)); Column { Text(if (caravan) "رزرو کاروانی" else "رزرو خانوادگی / فردی", fontWeight = FontWeight.Medium); Text(if (caravan) "امکان پیشنهاد چند واحد در یک مجموعه" else "پیشنهاد یک واحد مناسب", style = MaterialTheme.typography.bodySmall) } } }
            item { Button(onClick = { searched = true; selected = null }, enabled = dateOk && people > 0, modifier = Modifier.fillMaxWidth()) { Text("پیشنهاد واحدهای خالی") } }
            if (searched) {
                item { StepHeader("۲", "انتخاب پیشنهاد", if (suggestions.isEmpty()) "برای این بازه ظرفیت مناسبی پیدا نشد." else "ترکیب بین مجموعه‌های مختلف انجام نمی‌شود.") }
                if (suggestions.isEmpty()) item { EmptyCard("هیچ واحد یا ترکیب خالی با ظرفیت کافی پیدا نشد. تاریخ یا تعداد نفرات را تغییر دهید.") }
                itemsIndexed(suggestions) { index: Int, s: UnitSuggestion -> SuggestionCard(s, selected == s, { selected = s }, index == 0) }
            }
            if (selected != null) {
                item { StepHeader("۳", "مشخصات رزرو و زائران", "کد ملی اختیاری است؛ شماره موبایل برای جست‌وجوی بعدی ذخیره می‌شود.") }
                item { OutlinedTextField(title, { title = it }, label = { Text("عنوان / نام خانواده یا کاروان") }, modifier = Modifier.fillMaxWidth()) }
                if (caravan) item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(leader, { leader = it }, label = { Text("نام سرپرست") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(phone, { phone = normalizeNumeric(it, 11) }, label = { Text("شماره تماس سرپرست") }, modifier = Modifier.fillMaxWidth()) } }
                item { Text("زائران ${guests.size} از $people", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                itemsIndexed(guests) { index: Int, g: GuestInput -> GuestCard(g) { guests = guests.toMutableList().also { list -> list.removeAt(index) } } }
                if (guests.size < people) item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f))) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(nid, { nid = normalizeNumeric(it, 10) }, label = { Text("کد ملی (اختیاری)") }, supportingText = { if (nid.isNotBlank() && nid.length < 10) Text("در صورت وارد کردن، باید ۱۰ رقم باشد.") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            if (personNote.isNotBlank()) Text(personNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            OutlinedTextField(fn, { fn = it }, label = { Text("نام") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(ln, { ln = it }, label = { Text("نام خانوادگی") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(guestPhone, { guestPhone = normalizeNumeric(it, 11) }, label = { Text("شماره موبایل") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            val nationalValid = nid.isBlank() || nid.length == 10
                            val duplicate = nid.isNotBlank() && guests.any { it.nationalId == nid }
                            Button(onClick = { guests = guests + GuestInput(fn.trim(), ln.trim(), nid, guestPhone); fn = ""; ln = ""; nid = ""; guestPhone = ""; personNote = "" }, enabled = fn.isNotBlank() && ln.isNotBlank() && nationalValid && !duplicate, modifier = Modifier.fillMaxWidth()) { Text("+ افزودن زائر") }
                            if (duplicate) Text("این کد ملی قبلاً در همین رزرو اضافه شده است.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item { Row(verticalAlignment = Alignment.CenterVertically) { Switch(paid, { paid = it }); Spacer(Modifier.width(10.dp)); Text(if (paid) "اقامت پولی" else "اقامت رایگان", fontWeight = FontWeight.Medium) } }
                if (paid) item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(amount, { amount = normalizeNumeric(it) }, label = { Text("مبلغ کل (تومان)") }, modifier = Modifier.fillMaxWidth(), singleLine = true); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = paymentStatus == "پرداخت شده", onClick = { paymentStatus = "پرداخت شده" }, label = { Text("پرداخت شده") }); FilterChip(selected = paymentStatus == "بدهکار", onClick = { paymentStatus = "بدهکار" }, label = { Text("بدهکار") }) } } }
                item { OutlinedTextField(notes, { notes = it }, label = { Text("توضیحات") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
                val amountOk = !paid || (amount.toLongOrNull() ?: 0L) > 0
                val guestsOk = guests.size == people
                val canSave = guestsOk && amountOk && selected != null
                item {
                    if (!guestsOk) Text("برای ثبت نهایی باید مشخصات هر $people نفر اضافه شود.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = {
                        val s = selected!!
                        val plan = s.units.zip(s.allocations).map { PlanUnit(it.first.id, it.second) }
                        vm.createBooking(title.trim(), startDate.toString(), endDate.toString(), if (caravan) "caravan" else "family", leader.trim(), phone, paid, amount.toLongOrNull() ?: 0L, if (paid) paymentStatus else "رایگان", notes.trim(), plan, guests, onDone)
                    }, enabled = canSave, modifier = Modifier.fillMaxWidth()) { Text("ثبت نهایی رزرو") }
                }
            }
        }
    }

    if (showStartPicker) PersianDateDialog(startDate ?: LocalDate.now(), LocalDate.now(), "تاریخ ورود", { showStartPicker = false }) { startDate = it; if (endDate != null && !endDate!!.isAfter(it)) endDate = null; searched = false; selected = null; showStartPicker = false }
    if (showEndPicker) PersianDateDialog(endDate ?: (startDate ?: LocalDate.now()).plusDays(1), (startDate ?: LocalDate.now()).plusDays(1), "تاریخ خروج", { showEndPicker = false }) { endDate = it; searched = false; selected = null; showEndPicker = false }
}

@Composable
private fun SuggestionCard(s: UnitSuggestion, selected: Boolean, onSelect: () -> Unit, best: Boolean) {
    Card(onClick = onSelect, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(groupName(s.group), fontWeight = FontWeight.Bold)
                if (best) SuggestionBadge("پیشنهاد بهتر")
            }
            s.units.zip(s.allocations).forEach { (u, n) -> Text(if (u.unitGroup == "apartment") "${u.name} • $n نفر • بدون محدودیت ظرفیت" else "${u.name} • $n نفر از ظرفیت ${u.capacity}") }
            if (s.group != "apartment") Text("ظرفیت خالی پس از تخصیص: ${s.spareCapacity} نفر", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (selected) Text("✓ انتخاب شد", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ReportsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val rs by vm.reservations.collectAsState(); val units by vm.units.collectAsState()
    var draftFrom by remember { mutableStateOf<LocalDate?>(null) }; var draftTo by remember { mutableStateOf<LocalDate?>(null) }; var draftGroup by remember { mutableStateOf("all") }
    var appliedFrom by remember { mutableStateOf<LocalDate?>(null) }; var appliedTo by remember { mutableStateOf<LocalDate?>(null) }; var appliedGroup by remember { mutableStateOf("all") }; var generated by remember { mutableStateOf(false) }
    var showFrom by remember { mutableStateOf(false) }; var showTo by remember { mutableStateOf(false) }
    val filtered = if (!generated) emptyList() else rs.filter { r ->
        val s = runCatching { LocalDate.parse(r.startDate) }.getOrNull() ?: return@filter false
        val e = runCatching { LocalDate.parse(r.endDate) }.getOrNull() ?: return@filter false
        (appliedFrom == null || e.isAfter(appliedFrom)) && (appliedTo == null || !s.isAfter(appliedTo)) && (appliedGroup == "all" || r.unitGroup == appliedGroup)
    }
    val guests = filtered.sumOf { it.guestCount }; val revenue = filtered.filter { it.isPaid }.sumOf { it.amount }; val received = filtered.filter { it.isPaid && it.paymentStatus == "پرداخت شده" }.sumOf { it.amount }; val debt = filtered.filter { it.isPaid && it.paymentStatus != "پرداخت شده" }.sumOf { it.amount }
    Scaffold(topBar = { TopAppBar(title = { Text("گزارش‌ها", fontWeight = FontWeight.Bold) }, navigationIcon = { TextButton(onClick = onBack) { Text("بازگشت") } }) }) { p ->
        LazyColumn(Modifier.padding(p).fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Text("فیلتر گزارش", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { showFrom = true }, modifier = Modifier.weight(1f)) { Text(draftFrom?.let { JalaliCalendar.fromGregorian(it).display() } ?: "از تاریخ") }; OutlinedButton(onClick = { showTo = true }, modifier = Modifier.weight(1f)) { Text(draftTo?.let { JalaliCalendar.fromGregorian(it).display() } ?: "تا تاریخ") } } }
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("all" to "همه مکان‌ها", "original" to "زائرسرا", "fatemiyeh" to "فاطمیه", "apartment" to "آپارتمان").forEach { (k,label) -> FilterChip(selected = draftGroup == k, onClick = { draftGroup = k }, label = { Text(label) }) } } }
            item { Button(onClick = { appliedFrom=draftFrom; appliedTo=draftTo; appliedGroup=draftGroup; generated=true }, modifier = Modifier.fillMaxWidth()) { Text("نمایش گزارش") } }
            if (!generated) item { EmptyCard("بازه و مکان را انتخاب کنید و «نمایش گزارش» را بزنید.") }
            if (generated) {
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MetricCard("رزرو", filtered.size.toString(), Modifier.weight(1f)); MetricCard("نفر", guests.toString(), Modifier.weight(1f)) } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MetricCard("مبلغ کل", formatMoney(revenue), Modifier.weight(1f)); MetricCard("وصول", formatMoney(received), Modifier.weight(1f)) } }
                item { MetricCard("بدهی", formatMoney(debt), Modifier.fillMaxWidth()) }
                item { Text("گزارش مکانی", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                val rows = units.map { u -> u to filtered.filter { it.unitId == u.id } }.filter { it.second.isNotEmpty() }
                if (rows.isEmpty()) item { EmptyCard("در این بازه گزارشی وجود ندارد.") }
                items(rows) { (u, unitReservations) -> Card(Modifier.fillMaxWidth().animateContentSize()) { Row(Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(u.name, fontWeight = FontWeight.Bold); Text("${unitReservations.size} رزرو • ${unitReservations.sumOf { it.guestCount }} نفر", style = MaterialTheme.typography.bodySmall) }; Text("${formatMoney(unitReservations.filter { it.isPaid }.sumOf { it.amount })} تومان", color = MaterialTheme.colorScheme.primary) } } }
            }
        }
    }
    if (showFrom) PersianDateDialog(draftFrom ?: LocalDate.now(), LocalDate.of(2020,1,1), "از تاریخ", { showFrom=false }) { draftFrom=it; showFrom=false }
    if (showTo) PersianDateDialog(draftTo ?: LocalDate.now(), LocalDate.of(2020,1,1), "تا تاریخ", { showTo=false }) { draftTo=it; showTo=false }
}

@Composable
fun UnitsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val units by vm.units.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("واحدها", fontWeight = FontWeight.Bold) }, navigationIcon = { TextButton(onClick = onBack) { Text("بازگشت") } }) }) { p ->
        LazyColumn(Modifier.padding(p).fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { InfoCard("ظرفیت واحدها", "فاطمیه‌ها بر اساس تعداد تخت ظرفیت دارند. آپارتمان طبقه دوم و آپارتمان زیرزمین محدودیت تعداد نفر ندارند.") }
            items(units) { u: UnitItem -> UnitEditCard(u) { capacity: Int -> vm.updateUnitCapacity(u.id, capacity) } }
        }
    }
}

@Composable
private fun UnitEditCard(u: UnitItem, onSave: (Int) -> Unit) {
    var edit by remember(u.id, u.capacityConfigured, u.capacity) { mutableStateOf(if (u.capacityConfigured) u.capacity.toString() else "") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(u.name, fontWeight = FontWeight.Bold); Text(groupName(u.unitGroup), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            if (u.unitGroup == "apartment") {
                Text("بدون محدودیت تعداد نفر", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("ظرفیت فعلی: ${u.capacity} نفر")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(edit, { edit = normalizeNumeric(it, 2) }, label = { Text("ظرفیت") }, modifier = Modifier.weight(1f), singleLine = true); Button(onClick = { onSave(edit.toInt()) }, enabled = (edit.toIntOrNull() ?: 0) > 0) { Text("ذخیره") } }
            }
        }
    }
}

@Composable
fun PeopleScreen(vm: AppViewModel, onBack: () -> Unit) {
    var mode by remember { mutableStateOf("national") }; var query by remember { mutableStateOf("") }; var results by remember { mutableStateOf<List<PersonLookup>>(emptyList()) }; var searched by remember { mutableStateOf(false) }
    val label = when(mode) { "phone" -> "شماره موبایل"; "last_name" -> "نام خانوادگی"; else -> "کد ملی" }
    val normalizedQuery = if (mode == "last_name") query else normalizeNumeric(query, if (mode == "national") 10 else 11)
    Scaffold(topBar = { TopAppBar(title = { Text("سوابق زائر", fontWeight = FontWeight.Bold) }, navigationIcon = { TextButton(onClick = onBack) { Text("بازگشت") } }) }) { p ->
        LazyColumn(Modifier.padding(p).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { InfoCard("جستجوی سابقه", "با کد ملی، شماره موبایل یا بخشی از نام خانوادگی جست‌وجو کنید.") }
            item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(selected=mode=="national", onClick={mode="national";query="";results=emptyList()}, label={Text("کد ملی")}); FilterChip(selected=mode=="phone", onClick={mode="phone";query="";results=emptyList()}, label={Text("موبایل")}); FilterChip(selected=mode=="last_name", onClick={mode="last_name";query="";results=emptyList()}, label={Text("نام خانوادگی")}) } }
            item { OutlinedTextField(query, { query = if (mode=="last_name") it else normalizeNumeric(it, if(mode=="national")10 else 11) }, label={Text(label)}, modifier=Modifier.fillMaxWidth(), singleLine=true) }
            val valid = when(mode) { "national" -> query.length==10; "phone" -> query.length>=7; else -> query.trim().length>=2 }
            item { Button(onClick={ searched=true; vm.searchPeople(mode, query) { results=it } }, enabled=valid, modifier=Modifier.fillMaxWidth()) { Text("جست‌وجو") } }
            if (searched && results.isEmpty()) item { EmptyCard("سابقه‌ای پیدا نشد.") }
            items(results) { person -> Card(Modifier.fillMaxWidth().animateContentSize()) { Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(5.dp)) { Text("${person.firstName} ${person.lastName}", fontWeight=FontWeight.Bold); if(person.phone.isNotBlank()) Text("موبایل: ${person.phone}"); if(person.nationalId.isNotBlank() && person.nationalId != "null") Text("کد ملی: ${person.nationalId}"); Text("تعداد اقامت: ${person.visitCount}", color=MaterialTheme.colorScheme.primary); if(person.lastDeparture.isNotBlank() && person.lastDeparture != "null") Text("آخرین خروج: ${runCatching { JalaliCalendar.isoToJalali(person.lastDeparture) }.getOrDefault(person.lastDeparture)}", style=MaterialTheme.typography.bodySmall) } } }
        }
    }
}

@Composable
fun PersianDateDialog(
    initial: LocalDate,
    minDate: LocalDate,
    title: String,
    onDismiss: () -> Unit,
    onSelect: (LocalDate) -> Unit
) {
    val monthNames = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    val initialJ = JalaliCalendar.fromGregorian(if (initial.isBefore(minDate)) minDate else initial)
    var year by remember { mutableIntStateOf(initialJ.year) }
    var month by remember { mutableIntStateOf(initialJ.month) }
    var day by remember { mutableIntStateOf(initialJ.day) }

    fun move(delta: Int) {
        var m = month + delta
        var y = year
        if (m < 1) { m = 12; y-- }
        if (m > 12) { m = 1; y++ }
        year = y
        month = m
        day = day.coerceAtMost(JalaliCalendar.monthLength(y, m))
    }

    val selected = runCatching { JalaliCalendar.toGregorian(JalaliDate(year, month, day)) }.getOrNull()
    val valid = selected != null && !selected.isBefore(minDate)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { move(-1) }) { Text("ماه قبل") }
                    Text("${monthNames[month - 1]} $year", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { move(1) }) { Text("ماه بعد") }
                }
                val max = JalaliCalendar.monthLength(year, month)
                Row(Modifier.fillMaxWidth()) {
                    listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach {
                        Text(it, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    }
                }
                val firstGregorian = JalaliCalendar.toGregorian(JalaliDate(year, month, 1))
                val offset = (firstGregorian.dayOfWeek.value + 1) % 7
                val cells = List(offset) { 0 } + (1..max).toList()
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { d ->
                            if (d == 0) {
                                Spacer(Modifier.weight(1f))
                            } else {
                                val g = runCatching { JalaliCalendar.toGregorian(JalaliDate(year, month, d)) }.getOrNull()
                                val enabled = g != null && !g.isBefore(minDate)
                                TextButton(
                                    onClick = { day = d },
                                    enabled = enabled,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(0.dp)
                                ) { Text(if (d == day) "[$d]" else d.toString()) }
                            }
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSelect(selected!!) }, enabled = valid) { Text("انتخاب") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun Page(title: String, content: @Composable ColumnScope.() -> Unit) = Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); content() }

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) = Card(modifier.animateContentSize(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f))) { Column(Modifier.padding(14.dp)) { Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); Text(label, style = MaterialTheme.typography.bodySmall) } }

@Composable
private fun QuickButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) = FilledTonalButton(onClick = onClick, modifier = modifier.heightIn(min = 52.dp).animateContentSize()) { Text(label) }

@Composable
private fun InfoCard(title: String, text: String) = Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .65f))) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(text, style = MaterialTheme.typography.bodySmall) } }

@Composable
private fun EmptyCard(text: String) = Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))) { Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }

@Composable
private fun StepHeader(number: String, title: String, subtitle: String) = Row(verticalAlignment = Alignment.CenterVertically) { Surface(shape = RoundedCornerShape(99.dp), color = MaterialTheme.colorScheme.primary) { Text(number, Modifier.padding(horizontal = 11.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(10.dp)); Column { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable
private fun GuestCard(g: GuestInput, onDelete: () -> Unit) = Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("${g.firstName} ${g.lastName}", fontWeight = FontWeight.Medium); Text(listOfNotNull(if (g.nationalId.isBlank()) "بدون کد ملی" else g.nationalId, g.phone.takeIf { it.isNotBlank() }).joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; TextButton(onClick = onDelete) { Text("حذف") } } }

@Composable
private fun SuggestionBadge(text: String) = Surface(shape = RoundedCornerShape(99.dp), color = MaterialTheme.colorScheme.secondaryContainer) { Text(text, Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall) }

private fun groupName(group: String) = when (group) { "fatemiyeh" -> "فاطمیه"; "apartment" -> "آپارتمان‌ها"; else -> "زائرسرا" }

private fun formatMoney(v: Long): String = String.format("%,d", v)
