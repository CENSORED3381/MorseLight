package com.example.morselight

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.Normalizer

private val MORSE = mapOf(
    'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
    'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
    'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
    'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
    'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
    'Z' to "--..",
    '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
    '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----."
)

private fun normalize(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .uppercase()

private fun words(text: String): List<String> =
    normalize(text).split(" ").filter { it.isNotEmpty() }

/** Texto em morse para mostrar na tela. Palavras separadas por " / ". */
fun toMorseText(text: String): String =
    words(text).joinToString(" / ") { w ->
        w.mapNotNull { MORSE[it] }.joinToString(" ")
    }

/** Sequência (luz ligada?, duração em unidades) seguindo as regras do morse. */
fun buildPattern(text: String): List<Pair<Boolean, Int>> {
    val out = mutableListOf<Pair<Boolean, Int>>()
    words(text).forEachIndexed { wi, word ->
        if (wi > 0) out.add(false to 7)
        word.mapNotNull { MORSE[it] }.forEachIndexed { li, code ->
            if (li > 0) out.add(false to 3)
            code.forEachIndexed { si, symbol ->
                if (si > 0) out.add(false to 1)
                out.add(true to if (symbol == '.') 1 else 3)
            }
        }
    }
    return out
}

private fun findTorchId(cm: CameraManager): String? =
    cm.cameraIdList.firstOrNull { id ->
        val c = cm.getCameraCharacteristics(id)
        c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
            c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MorseScreen()
            }
        }
    }
}

@Composable
fun MorseScreen() {
    val context = LocalContext.current
    val cm = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val torchId = remember { runCatching { findTorchId(cm) }.getOrNull() }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var unitMs by remember { mutableFloatStateOf(200f) }
    var lightOn by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    val morse = remember(name) { toMorseText(name) }

    fun setTorch(on: Boolean) {
        if (torchId != null) runCatching { cm.setTorchMode(torchId, on) }
    }

    fun transmit(text: String) {
        if (running) return
        val pattern = buildPattern(text)
        if (pattern.isEmpty()) return
        running = true
        job = scope.launch {
            try {
                for ((on, units) in pattern) {
                    lightOn = on
                    setTorch(on)
                    delay((units * unitMs).toLong())
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                lightOn = false
                setTorch(false)
                running = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Morse Light", fontSize = 28.sp, style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Digite um nome (ex.: Huzeifa)") },
            singleLine = true,
            enabled = !running,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = if (morse.isEmpty()) "…" else morse,
            fontFamily = FontFamily.Monospace,
            fontSize = 22.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (lightOn) Color(0xFFFFEB3B) else Color(0xFF222222)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (lightOn) "●" else "○",
                color = if (lightOn) Color.Black else Color.Gray,
                fontSize = 48.sp
            )
        }

        Text("Velocidade: ${unitMs.toInt()} ms por unidade")
        Slider(
            value = unitMs,
            onValueChange = { unitMs = it },
            valueRange = 80f..500f,
            enabled = !running
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { if (running) job?.cancel() else transmit(name) },
                enabled = running || morse.isNotEmpty()
            ) {
                Text(if (running) "Parar" else "Transmitir")
            }
            OutlinedButton(
                onClick = { transmit("SOS") },
                enabled = !running
            ) {
                Text("SOS")
            }
        }

        if (torchId == null) {
            Text("Este aparelho não tem flash disponível; só a tela vai piscar.")
        }
    }
}
