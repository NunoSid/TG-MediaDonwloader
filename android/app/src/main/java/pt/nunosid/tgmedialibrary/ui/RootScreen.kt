package pt.nunosid.tgmedialibrary.ui

import androidx.activity.compose.BackHandler
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
    val unlocked by viewModel.privacyUnlocked.collectAsState()

    if (!unlocked) {
        CalculatorLockScreen(
            onUnlock = viewModel::tryUnlock,
            onPanic = viewModel::panicWipe
        )
        return
    }

    BackHandler(enabled = true) { viewModel.lockPrivacy() }

    when (val auth = viewModel.authState.collectAsState().value) {
        TelegramAuthState.Starting -> CenterMessage("A iniciar Telegram…")
        TelegramAuthState.NeedApiCredentials -> ApiCredentialsInput(viewModel::configureApi)
        TelegramAuthState.NeedPhone -> LoginInput("Número de telefone", "+351…", false, viewModel::submitPhone)
        TelegramAuthState.NeedCode -> LoginInput("Código Telegram", "12345", false, viewModel::submitCode)
        TelegramAuthState.NeedPassword -> LoginInput("Password 2FA", "Password", true, viewModel::submitPassword)
        TelegramAuthState.Ready -> VideoLibrary(viewModel, onPlay)
        is TelegramAuthState.Error -> ErrorScreen(auth.message, viewModel::resetApiCredentials)
    }
}

@Composable
private fun ApiCredentialsInput(submit: (String, String) -> Unit) {
    var apiId by remember { mutableStateOf("") }
    var apiHash by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Telegram Media Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Liga a tua conta Telegram", style = MaterialTheme.typography.titleMedium)
            Text("Introduz o API ID e API Hash criados em my.telegram.org. Ficam guardados apenas nesta app no dispositivo.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(apiId, { apiId = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("API ID") }, singleLine = true)
            OutlinedTextField(apiHash, { apiHash = it.trim() }, Modifier.fillMaxWidth(), label = { Text("API Hash") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            Button({ submit(apiId, apiHash) }, Modifier.fillMaxWidth(), enabled = apiId.isNotBlank() && apiHash.isNotBlank()) { Text("Guardar e ligar") }
        }
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
private fun ErrorScreen(message: String, reset: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Não foi possível ligar ao Telegram", style = MaterialTheme.typography.titleLarge)
            Text(message)
            OutlinedButton(reset) { Text("Alterar credenciais API") }
        }
    }
}

@Composable
private fun CenterMessage(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(message) }
}
