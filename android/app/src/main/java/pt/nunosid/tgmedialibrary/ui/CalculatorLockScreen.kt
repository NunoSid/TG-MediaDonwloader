package pt.nunosid.tgmedialibrary.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CalculatorLockScreen(
    onUnlock: (String) -> Boolean,
    onPanic: () -> Unit
) {
    var expression by remember { mutableStateOf("") }
    var display by remember { mutableStateOf("0") }

    fun press(value: String) {
        when (value) {
            "C" -> {
                expression = ""
                display = "0"
            }
            "⌫" -> {
                expression = expression.dropLast(1)
                display = expression.ifBlank { "0" }
            }
            "=" -> {
                when {
                    expression == LibraryViewModel.PANIC_CODE -> {
                        expression = ""
                        display = "0"
                        onPanic()
                    }
                    onUnlock(expression) -> {
                        expression = ""
                        display = "0"
                    }
                    else -> {
                        val result = evaluateExpression(expression)
                        expression = result
                        display = result.ifBlank { "0" }
                    }
                }
            }
            else -> {
                if (value in listOf("+", "−", "×", "÷")) {
                    if (expression.isBlank()) return
                    if (expression.lastOrNull()?.let { it in "+−×÷" } == true) {
                        expression = expression.dropLast(1) + value
                    } else expression += value
                } else {
                    expression = if (expression == "0" && value != ".") value else expression + value
                }
                display = expression.ifBlank { "0" }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = display,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 28.dp),
                textAlign = TextAlign.End,
                fontSize = 44.sp,
                maxLines = 2
            )

            val rows = listOf(
                listOf("C", "⌫", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "−"),
                listOf("1", "2", "3", "+"),
                listOf("0", ".", "=")
            )

            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { key ->
                        FilledTonalButton(
                            onClick = { press(key) },
                            modifier = Modifier.weight(if (key == "0" && row.size == 3) 2f else 1f).height(68.dp)
                        ) {
                            Text(key, fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun evaluateExpression(input: String): String {
    if (input.isBlank()) return "0"
    val regex = Regex("^(-?\\d+(?:\\.\\d+)?)([+−×÷])(-?\\d+(?:\\.\\d+)?)$")
    val match = regex.matchEntire(input) ?: return input
    val left = match.groupValues[1].toDoubleOrNull() ?: return input
    val op = match.groupValues[2]
    val right = match.groupValues[3].toDoubleOrNull() ?: return input
    val result = when (op) {
        "+" -> left + right
        "−" -> left - right
        "×" -> left * right
        "÷" -> if (right == 0.0) return "Erro" else left / right
        else -> return input
    }
    return if (result % 1.0 == 0.0) result.toLong().toString()
    else result.toString().trimEnd('0').trimEnd('.')
}
