package pt.nunosid.tgmedialibrary.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.nunosid.tgmedialibrary.model.VideoItem
import pt.nunosid.tgmedialibrary.telegram.TelegramAuthState
import pt.nunosid.tgmedialibrary.ui.theme.ReplayAcid
import pt.nunosid.tgmedialibrary.ui.theme.ReplayCyan
import pt.nunosid.tgmedialibrary.ui.theme.ReplayInk
import pt.nunosid.tgmedialibrary.ui.theme.ReplayMuted
import pt.nunosid.tgmedialibrary.ui.theme.ReplayPanel
import pt.nunosid.tgmedialibrary.ui.theme.ReplayPaper
import pt.nunosid.tgmedialibrary.ui.theme.ReplayPink

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
        TelegramAuthState.Starting -> CenterMessage("A INICIAR TELEGRAM…")
        TelegramAuthState.NeedApiCredentials -> ApiCredentialsInput(viewModel::configureApi)
        TelegramAuthState.NeedPhone -> LoginInput("NÚMERO DE TELEFONE", "+351…", false, ReplayCyan, viewModel::submitPhone)
        TelegramAuthState.NeedCode -> LoginInput("CÓDIGO TELEGRAM", "12345", false, ReplayAcid, viewModel::submitCode)
        TelegramAuthState.NeedPassword -> LoginInput("PASSWORD 2FA", "Password", true, ReplayPink, viewModel::submitPassword)
        TelegramAuthState.Ready -> VideoLibrary(viewModel, onPlay)
        is TelegramAuthState.Error -> ErrorScreen(auth.message, viewModel::resetApiCredentials)
    }
}

@Composable
private fun ApiCredentialsInput(submit: (String, String) -> Unit) {
    var apiId by remember { mutableStateOf("") }
    var apiHash by remember { mutableStateOf("") }

    ReplayAuthShell("CONNECT//01", "LIGA A TUA CONTA TELEGRAM", ReplayCyan) {
        Text(
            "Introduz o API ID e API Hash criados em my.telegram.org. Ficam guardados apenas nesta app no dispositivo.",
            color = ReplayMuted,
            style = MaterialTheme.typography.bodySmall
        )
        ReplayTextField(apiId, { apiId = it.filter(Char::isDigit) }, "API ID")
        ReplayTextField(apiHash, { apiHash = it.trim() }, "API HASH", password = true)
        ReplayPrimaryButton(
            text = "GUARDAR E LIGAR",
            accent = ReplayAcid,
            enabled = apiId.isNotBlank() && apiHash.isNotBlank()
        ) { submit(apiId, apiHash) }
    }
}

@Composable
private fun LoginInput(
    title: String,
    hint: String,
    password: Boolean,
    accent: Color,
    submit: (String) -> Unit
) {
    var value by remember { mutableStateOf("") }
    ReplayAuthShell("AUTH//TG", title, accent) {
        ReplayTextField(value, { value = it }, hint, password)
        ReplayPrimaryButton("CONTINUAR", ReplayAcid, value.isNotBlank()) {
            if (value.isNotBlank()) submit(value)
        }
        Text(
            "READ ONLY · MEDIA REPLAY · NO MESSAGE SENDING",
            color = ReplayMuted,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun ErrorScreen(message: String, reset: () -> Unit) {
    ReplayAuthShell("ERROR//TG", "NÃO FOI POSSÍVEL LIGAR", MaterialTheme.colorScheme.error) {
        Text(message, color = ReplayMuted)
        ReplayPrimaryButton("ALTERAR CREDENCIAIS API", ReplayPink, true, reset)
    }
}

@Composable
private fun CenterMessage(message: String) {
    Box(
        Modifier.fillMaxSize().background(ReplayPaper).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.background(ReplayAcid).border(2.dp, ReplayInk).padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text(message, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun ReplayAuthShell(
    eyebrow: String,
    title: String,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(ReplayPaper).padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .matchParentSize()
                    .offset(7.dp, 7.dp)
                    .background(ReplayInk)
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(ReplayPanel)
                    .border(2.dp, ReplayInk)
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.background(accent).border(2.dp, ReplayInk).padding(horizontal = 7.dp, vertical = 3.dp)) {
                        Text(eyebrow, fontWeight = FontWeight.Black, letterSpacing = 1.sp, style = MaterialTheme.typography.labelSmall)
                    }
                    Text("TG//LIBRARY", color = ReplayMuted, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                }
                Text(title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
                content()
            }
        }
    }
}

@Composable
private fun ReplayTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    password: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, fontWeight = FontWeight.Bold) },
        singleLine = true,
        shape = RectangleShape,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = ReplayCyan,
            unfocusedContainerColor = Color.White,
            focusedBorderColor = ReplayInk,
            unfocusedBorderColor = ReplayInk,
            cursorColor = ReplayInk
        )
    )
}

@Composable
private fun ReplayPrimaryButton(
    text: String,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        enabled = enabled,
        shape = RectangleShape,
        border = BorderStroke(2.dp, ReplayInk),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = ReplayInk,
            disabledContainerColor = accent.copy(alpha = 0.4f),
            disabledContentColor = ReplayInk.copy(alpha = 0.45f)
        )
    ) {
        Text(text, fontWeight = FontWeight.Black, letterSpacing = 0.7.sp)
    }
}
