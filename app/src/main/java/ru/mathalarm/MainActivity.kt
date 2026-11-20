package ru.mathalarm

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    // Увеличивается в onResume, чтобы перечитать список будильников и разрешения
    private var resumeTick by mutableIntStateOf(0)

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent { App(tick = resumeTick) }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        // Если будильник звонит — сразу показываем примеры
        if (AlarmService.isRinging(this)) {
            startActivity(Intent(this, AlarmActivity::class.java))
        }
    }
}

// ---------- Вспомогательное ----------

fun soundTitle(c: Context, s: String?): String {
    if (s == null) return "Стандартный будильник"
    val uri = Uri.parse(s)
    try {
        c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur ->
            if (cur.moveToFirst()) {
                val n = cur.getString(0)
                if (!n.isNullOrBlank()) return n
            }
        }
    } catch (_: Exception) {
    }
    return try {
        RingtoneManager.getRingtone(c, uri)?.getTitle(c) ?: "Выбранный звук"
    } catch (_: Exception) {
        "Выбранный звук"
    }
}

private fun fullScreenAllowed(c: Context): Boolean {
    if (Build.VERSION.SDK_INT < 34) return true
    return c.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
}

private fun exactAllowed(c: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
}

private fun notificationsAllowed(c: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}

// ---------- Экраны ----------

@Composable
fun App(tick: Int) {
    val ctx = LocalContext.current
    var alarms by remember { mutableStateOf(AlarmStore.load(ctx)) }
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }

    LaunchedEffect(tick) { alarms = AlarmStore.load(ctx) }

    fun upsert(a: Alarm) {
        val list = AlarmStore.load(ctx)
        val updated = if (list.any { it.id == a.id }) list.map { if (it.id == a.id) a else it } else list + a
        AlarmStore.save(ctx, updated)
        alarms = updated
        val t = AlarmScheduler.schedule(ctx, a)
        if (t != null) Toast.makeText(ctx, "Сработает через ${untilText(t)}", Toast.LENGTH_LONG).show()
    }

    fun remove(a: Alarm) {
        AlarmScheduler.cancel(ctx, a.id)
        val updated = AlarmStore.load(ctx).filter { it.id != a.id }
        AlarmStore.save(ctx, updated)
        alarms = updated
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .systemBarsPadding()
    ) {
        val current = editing
        if (current == null) {
            AlarmList(
                alarms = alarms,
                tick = tick,
                onAdd = {
                    editingIsNew = true
                    editing = Alarm(
                        id = AlarmStore.nextId(alarms),
                        hour = 7, minute = 0, days = 31,
                        soundUri = null, enabled = true
                    )
                },
                onEdit = { editingIsNew = false; editing = it },
                onToggle = { a -> upsert(a.copy(enabled = !a.enabled)) }
            )
        } else {
            BackHandler { editing = null }
            AlarmEditor(
                initial = current,
                isNew = editingIsNew,
                onSave = { upsert(it); editing = null },
                onDelete = { remove(current); editing = null },
                onBack = { editing = null }
            )
        }
    }
}

@Composable
fun AlarmList(
    alarms: List<Alarm>,
    tick: Int,
    onAdd: () -> Unit,
    onEdit: (Alarm) -> Unit,
    onToggle: (Alarm) -> Unit
) {
    val ctx = LocalContext.current
    val ringing = remember(tick) { AlarmService.isRinging(ctx) }
    val notifOk = remember(tick) { notificationsAllowed(ctx) }
    val fsiOk = remember(tick) { fullScreenAllowed(ctx) }
    val exactOk = remember(tick) { exactAllowed(ctx) }

    val nextTrigger = alarms.filter { it.enabled }.minOfOrNull { it.nextTriggerMillis() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        BrutalCard(Modifier.fillMaxWidth(), bg = Yellow, shadow = 8.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BText("БУДИЛЬНИК×", size = 34.sp)
                BText(
                    if (nextTrigger != null) "Следующий звонок через ${untilText(nextTrigger)}"
                    else "Нет включённых будильников",
                    size = 14.sp,
                    weight = FontWeight.Bold
                )
            }
        }

        if (ringing) {
            BrutalButton(
                text = "ЗВОНИТ! РЕШИТЬ ПРИМЕРЫ",
                modifier = Modifier.fillMaxWidth(),
                bg = Red,
                textSize = 20.sp,
                onClick = { ctx.startActivity(Intent(ctx, AlarmActivity::class.java)) }
            )
        }

        if (!notifOk) {
            WarningCard("Включи уведомления, иначе будильник не покажется на экране блокировки.", "РАЗРЕШИТЬ") {
                ctx.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                )
            }
        }
        if (!fsiOk && Build.VERSION.SDK_INT >= 34) {
            WarningCard("Разреши «полноэкранные уведомления», чтобы будильник открывался поверх блокировки.", "ОТКРЫТЬ") {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                        .setData(Uri.parse("package:${ctx.packageName}"))
                )
            }
        }
        if (!exactOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            WarningCard("Разреши «будильники и напоминания», чтобы звонок был точным.", "ОТКРЫТЬ") {
                ctx.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData(Uri.parse("package:${ctx.packageName}"))
                )
            }
        }

        BrutalButton(
            text = "+ НОВЫЙ БУДИЛЬНИК",
            modifier = Modifier.fillMaxWidth(),
            bg = Green,
            textSize = 20.sp,
            onClick = onAdd
        )

        alarms.sortedWith(compareBy({ it.hour }, { it.minute })).forEach { a ->
            BrutalCard(
                Modifier
                    .fillMaxWidth()
                    .clickable { onEdit(a) },
                bg = if (a.enabled) White else Paper
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BText("${two(a.hour)}:${two(a.minute)}", size = 44.sp, mono = true)
                        BText(daysText(a.days), size = 14.sp)
                    }
                    BrutalButton(
                        text = if (a.enabled) "ВКЛ" else "ВЫКЛ",
                        bg = if (a.enabled) Green else Pink,
                        textSize = 18.sp,
                        shadow = 4.dp,
                        onClick = { onToggle(a) }
                    )
                }
            }
        }

        if (alarms.isEmpty()) {
            BText(
                "Пока пусто. Добавь будильник — выключить его можно будет только решив $PROBLEMS_TO_SOLVE примеров на умножение.",
                size = 15.sp,
                weight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun WarningCard(text: String, button: String, onClick: () -> Unit) {
    BrutalCard(Modifier.fillMaxWidth(), bg = Orange, shadow = 4.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BText(text, size = 14.sp, weight = FontWeight.Bold)
            BrutalButton(button, bg = White, textSize = 16.sp, shadow = 4.dp, vertical = 10.dp, onClick = onClick)
        }
    }
}

@Composable
fun AlarmEditor(
    initial: Alarm,
    isNew: Boolean,
    onSave: (Alarm) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    var hour by remember { mutableStateOf(initial.hour) }
    var minute by remember { mutableStateOf(initial.minute) }
    var days by remember { mutableStateOf(initial.days) }
    var sound by remember { mutableStateOf(initial.soundUri) }

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            sound = uri?.toString()
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            sound = uri.toString()
        }
    }

    fun addMinutes(d: Int) {
        minute = ((minute + d) % 60 + 60) % 60
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        BrutalCard(Modifier.fillMaxWidth(), bg = Pink, shadow = 8.dp) {
            BText(
                if (isNew) "НОВЫЙ БУДИЛЬНИК" else "РЕДАКТИРОВАНИЕ",
                size = 26.sp,
                modifier = Modifier.padding(16.dp)
            )
        }

        // Время
        BrutalCard(Modifier.fillMaxWidth(), bg = Yellow) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TimeColumn(
                    value = two(hour),
                    steps = listOf("+1" to { hour = (hour + 1) % 24 }, "−1" to { hour = (hour + 23) % 24 }),
                    modifier = Modifier.weight(1f)
                )
                BText(":", size = 44.sp, mono = true)
                TimeColumn(
                    value = two(minute),
                    steps = listOf(
                        "+5" to { addMinutes(5) }, "+1" to { addMinutes(1) },
                        "−1" to { addMinutes(-1) }, "−5" to { addMinutes(-5) }
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Дни недели
        BText("ДНИ РАБОТЫ", size = 16.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DAY_NAMES.forEachIndexed { i, name ->
                val on = (days and (1 shl i)) != 0
                BrutalButton(
                    text = name,
                    modifier = Modifier.weight(1f),
                    bg = if (on) Green else White,
                    textSize = 13.sp,
                    shadow = if (on) 2.dp else 4.dp,
                    horizontal = 0.dp,
                    vertical = 12.dp,
                    onClick = { days = days xor (1 shl i) }
                )
            }
        }
        BText(
            if (days == 0) "Дни не выбраны — сработает один раз." else daysText(days),
            size = 13.sp,
            weight = FontWeight.Bold
        )

        // Звук
        BText("ЗВУК", size = 16.sp)
        BrutalCard(Modifier.fillMaxWidth(), bg = White) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BText(soundTitle(ctx, sound), size = 17.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BrutalButton(
                        "МЕЛОДИИ", Modifier.weight(1f), bg = Blue, textSize = 14.sp, shadow = 4.dp, vertical = 10.dp,
                        horizontal = 4.dp
                    ) {
                        ringtonePicker.launch(
                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                    sound?.let { Uri.parse(it) }
                                )
                            }
                        )
                    }
                    BrutalButton(
                        "СВОЙ ФАЙЛ", Modifier.weight(1f), bg = Lilac, textSize = 14.sp, shadow = 4.dp, vertical = 10.dp,
                        horizontal = 4.dp
                    ) { filePicker.launch(arrayOf("audio/*")) }
                    BrutalButton(
                        "СБРОС", Modifier.weight(1f), bg = White, textSize = 14.sp, shadow = 4.dp, vertical = 10.dp,
                        horizontal = 4.dp
                    ) { sound = null }
                }
            }
        }

        val edited = Alarm(initial.id, hour, minute, days, sound, true)

        BrutalButton(
            "СОХРАНИТЬ", Modifier.fillMaxWidth(), bg = Green, textSize = 22.sp,
            onClick = { onSave(edited) }
        )
        BrutalButton(
            "ПРОВЕРИТЬ ЗВОНОК", Modifier.fillMaxWidth(), bg = Blue, textSize = 18.sp,
            onClick = {
                AlarmService.start(ctx, initial.id, sound)
                ctx.startActivity(Intent(ctx, AlarmActivity::class.java))
            }
        )
        if (!isNew) {
            BrutalButton("УДАЛИТЬ", Modifier.fillMaxWidth(), bg = Red, textSize = 18.sp, onClick = onDelete)
        }
        BrutalButton("НАЗАД", Modifier.fillMaxWidth(), bg = White, textSize = 18.sp, onClick = onBack)
    }
}

@Composable
private fun TimeColumn(
    value: String,
    steps: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier
) {
    val half = steps.size / 2
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.take(half).forEach { (label, action) ->
            BrutalButton(label, Modifier.fillMaxWidth(), bg = White, textSize = 16.sp, shadow = 4.dp, vertical = 8.dp, onClick = action)
        }
        BText(value, size = 56.sp, mono = true, align = TextAlign.Center)
        steps.drop(half).forEach { (label, action) ->
            BrutalButton(label, Modifier.fillMaxWidth(), bg = White, textSize = 16.sp, shadow = 4.dp, vertical = 8.dp, onClick = action)
        }
    }
}
