package pt.nunosid.tgmedialibrary.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import pt.nunosid.tgmedialibrary.model.VideoItem
import pt.nunosid.tgmedialibrary.telegram.TelegramAuthState

@Composable
fun RootScreen(viewModel: LibraryViewModel, onPlay: (VideoItem) -> Unit) {
    when (val auth = viewModel.authState.collectAsState().value) {
        TelegramAuthState.Starting -> CenterMessage("A iniciar Telegram…")
        TelegramAuthState.NeedPhone -> LoginInput("Número de telefone", "+351…", false, viewModel::submitPhone)
        TelegramAuthState.NeedCode -> LoginInput("Código Telegram", "12345", false, viewModel::submitCode)
        TelegramAuthState.NeedPassword -> LoginInput("Password 2FA", "Password", true, viewModel::submitPassword)
        TelegramAuthState.Ready -> VideoLibrary(viewModel, onPlay)
        is TelegramAuthState.Error -> CenterMessage(auth.message)
    }
}

@Composable
private fun LoginInput(title: String, hint: String, password: Boolean, submit: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Telegram Media Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(title)
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(hint) },
                visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                singleLine = true
            )
            Button(onClick = { if (value.isNotBlank()) submit(value) }, modifier = Modifier.fillMaxWidth()) { Text("Continuar") }
            Text("A app apenas lê e reproduz media da tua conta. Não envia mensagens.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CenterMessage(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(message) }
}
