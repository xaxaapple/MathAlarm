package ru.mathalarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Экран решения примеров. Показывается поверх экрана блокировки.
 * Звук продолжает играть (его держит AlarmService), пока не решены все примеры.
 */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        if (!AlarmService.isRinging(this)) {
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            AlarmScreen(
                onAllSolved = { AlarmService.stop(this) },
                onClose = { finish() }
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Намеренно НЕ снимаем блокировку экрана: примеры решаются поверх неё.
    }
}

@Composable
fun AlarmScreen(onAllSolved: () -> Unit, onClose: () -> Unit) {
    var solved by rememberSaveable { mutableStateOf(0) }
    var seed by rememberSaveable { mutableStateOf(System.nanoTime()) }
    var finished by rememberSaveable { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }

    val problem = remember(seed) { MathGen.problem(seed) }

    // Кнопка «назад» не выключает будильник
    BackHandler(enabled = true) {}

    LaunchedEffect(wrong) {
        if (wrong) {
            delay(900)
            wrong = false
        }
    }
    LaunchedEffect(finished) {
        if (finished) {
            onAllSolved()
            delay(1500)
            onClose()
        }
    }

    fun pick(value: Int) {
        if (finished) return
        if (value == problem.answer) {
            val n = solved + 1
            solved = n
            wrong = false
            seed = Random.nextLong()
            if (n >= PROBLEMS_TO_SOLVE) finished = true
        } else {
            // Неверно — сразу новый пример, чтобы нельзя было перебирать варианты.
            wrong = true
            seed = Random.nextLong()
        }
    }

    val bg = when {
        finished -> Green
        wrong -> Red
        else -> Paper
    }
    val optionColors = listOf(Blue, Pink, Green, Orange, Lilac, Yellow, White)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        BrutalCard(Modifier.fillMaxWidth(), bg = Pink) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BText(if (finished) "ГОТОВО!" else "ПОДЪЁМ!", size = 38.sp)
                BText(
                    if (finished) "Будильник выключен. Доброе утро!"
                    else "Звонок замолчит, только когда решишь все $PROBLEMS_TO_SOLVE примеров.",
                    size = 15.sp,
                    weight = FontWeight.Bold
                )
            }
        }

        // Прогресс
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (i in 0 until PROBLEMS_TO_SOLVE) {
                BrutalCard(
                    Modifier
                        .weight(1f)
                        .height(40.dp),
                    bg = if (i < solved) Green else White,
                    shadow = 4.dp
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        BText("${i + 1}", size = 18.sp, mono = true)
                    }
                }
            }
        }

        if (!finished) {
            BrutalCard(Modifier.fillMaxWidth(), bg = Yellow, shadow = 8.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BText(
                        "ПРИМЕР ${solved + 1} ИЗ $PROBLEMS_TO_SOLVE",
                        size = 14.sp,
                        align = TextAlign.Center
                    )
                    BText(
                        "${problem.a} × ${problem.b} = ?",
                        size = 54.sp,
                        align = TextAlign.Center,
                        mono = true
                    )
                }
            }

            if (wrong) {
                BrutalCard(Modifier.fillMaxWidth(), bg = Red, shadow = 4.dp) {
                    BText(
                        "НЕВЕРНО! ДЕРЖИ НОВЫЙ ПРИМЕР",
                        size = 16.sp,
                        color = White,
                        align = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    )
                }
            }

            // 7 вариантов: 1 верный + 6 неверных
            problem.options.chunked(2).forEachIndexed { rowIndex, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEachIndexed { colIndex, value ->
                        BrutalButton(
                            text = value.toString(),
                            modifier = Modifier.weight(1f),
                            bg = optionColors[(rowIndex * 2 + colIndex) % optionColors.size],
                            textSize = 30.sp,
                            vertical = 18.dp,
                            mono = true,
                            onClick = { pick(value) }
                        )
                    }
                }
            }
        }
    }
}
