package pt.nunosid.tgmedialibrary.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.nunosid.tgmedialibrary.security.PrivacyPinStore
import pt.nunosid.tgmedialibrary.ui.theme.ReplayAcid
import pt.nunosid.tgmedialibrary.ui.theme.ReplayCyan
import pt.nunosid.tgmedialibrary.ui.theme.ReplayInk
import pt.nunosid.tgmedialibrary.ui.theme.ReplayMuted
import pt.nunosid.tgmedialibrary.ui.theme.ReplayPanel
import pt.nunosid.tgmedialibrary.ui.theme.ReplayPaper
import pt.nunosid.tgmedialibrary.ui.theme.ReplayPink

@Composable
fun PrivacyPinSetupScreen(onSetPin: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showPolicy by remember { mutableStateOf(false) }

    PrivacyShell("PRIVACY//SETUP", "CRIAR PIN DE PRIVACIDADE", ReplayCyan) {
        Text(
            "Escolhe um PIN de ${PrivacyPinStore.MIN_LENGTH} a ${PrivacyPinStore.MAX_LENGTH} dígitos. " +
                "Será pedido sempre que a app voltar a ficar bloqueada.",
            color = ReplayMuted,
            style = MaterialTheme.typography.bodySmall
        )
        PinField(pin, { pin = sanitizePin(it) }, "NOVO PIN")
        PinField(confirm, { confirm = sanitizePin(it) }, "CONFIRMAR PIN")
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = {
                error = when {
                    !PrivacyPinStore.isValidPin(pin) -> "O PIN deve ter entre ${PrivacyPinStore.MIN_LENGTH} e ${PrivacyPinStore.MAX_LENGTH} dígitos."
                    pin != confirm -> "Os PINs não coincidem."
                    !onSetPin(pin) -> "Não foi possível guardar o PIN."
                    else -> null
                }
            },
            enabled = pin.isNotBlank() && confirm.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RectangleShape,
            border = BorderStroke(2.dp, ReplayInk),
            colors = ButtonDefaults.buttonColors(containerColor = ReplayAcid, contentColor = ReplayInk)
        ) {
            Text("CRIAR PIN E CONTINUAR", fontWeight = FontWeight.Black)
        }
        Text(
            "O PIN não é guardado em texto simples.",
            color = ReplayMuted,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelSmall
        )
        TextButton(onClick = { showPolicy = true }) {
            Text("POLÍTICA DE PRIVACIDADE", fontWeight = FontWeight.Black, color = ReplayInk)
        }
    }

    if (showPolicy) PrivacyPolicyDialog { showPolicy = false }
}

@Composable
fun PrivacyLockScreen(onUnlock: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var showPolicy by remember { mutableStateOf(false) }

    PrivacyShell("PRIVACY//LOCK", "TG MEDIA LIBRARY BLOQUEADA", ReplayPink) {
        Text(
            "Introduz o PIN de privacidade definido na primeira utilização.",
            color = ReplayMuted,
            style = MaterialTheme.typography.bodySmall
        )
        PinField(pin, {
            pin = sanitizePin(it)
            error = false
        }, "PIN")
        if (error) {
            Text("PIN incorreto.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = {
                if (!onUnlock(pin)) {
                    error = true
                    pin = ""
                }
            },
            enabled = pin.length >= PrivacyPinStore.MIN_LENGTH,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RectangleShape,
            border = BorderStroke(2.dp, ReplayInk),
            colors = ButtonDefaults.buttonColors(containerColor = ReplayAcid, contentColor = ReplayInk)
        ) {
            Text("DESBLOQUEAR", fontWeight = FontWeight.Black)
        }
        TextButton(onClick = { showPolicy = true }) {
            Text("POLÍTICA DE PRIVACIDADE", fontWeight = FontWeight.Black, color = ReplayInk)
        }
    }

    if (showPolicy) PrivacyPolicyDialog { showPolicy = false }
}

@Composable
private fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = ReplayPanel,
        title = { Text("POLÍTICA DE PRIVACIDADE", fontWeight = FontWeight.Black) },
        text = {
            Column(
                Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("TG Media Library é uma aplicação independente e não é oficial nem afiliada à Telegram.", fontWeight = FontWeight.Bold)
                Text("A app liga diretamente aos serviços Telegram através da TDLib para autenticar a tua conta e apresentar media da tua própria conta.")
                Text("As credenciais API, a chave da base TDLib e os dados de bloqueio ficam armazenados localmente. Dados sensíveis são protegidos com mecanismos suportados pelo Android Keystore.")
                Text("O PIN não é guardado em texto simples. A app guarda apenas um verificador derivado com salt aleatório.")
                Text("A app não possui backend próprio para receber os teus vídeos, credenciais ou mensagens e não inclui SDK de publicidade ou analytics do programador.")
                Text("Vídeos podem ser descarregados temporariamente para reprodução, download ou partilha. As cópias temporárias são limpas pelos controlos de privacidade. Ficheiros que guardes explicitamente em Downloads permanecem no dispositivo até os apagares.")
                Text("Quando escolhes partilhar um ficheiro, esse ficheiro é fornecido à aplicação de destino que selecionares. Telegram processa os dados da tua conta segundo os seus próprios termos e política de privacidade.")
                Text("Podes apagar os dados locais da TG Media Library através das definições de armazenamento do Android ou desinstalando a app. A app não cria uma conta separada no servidor do programador.")
                Text("A versão integral e atualizada desta política está publicada no repositório público do projeto.", color = ReplayMuted)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = ReplayAcid, contentColor = ReplayInk)
            ) { Text("FECHAR", fontWeight = FontWeight.Black) }
        }
    )
}

@Composable
private fun PrivacyShell(
    eyebrow: String,
    title: String,
    accent: androidx.compose.ui.graphics.Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(ReplayPaper).padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.matchParentSize().offset(7.dp, 7.dp).background(ReplayInk))
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
private fun PinField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, fontWeight = FontWeight.Bold) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        shape = RectangleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = ReplayCyan,
            unfocusedContainerColor = ReplayPanel,
            focusedBorderColor = ReplayInk,
            unfocusedBorderColor = ReplayInk,
            cursorColor = ReplayInk
        )
    )
}

private fun sanitizePin(value: String): String =
    value.filter(Char::isDigit).take(PrivacyPinStore.MAX_LENGTH)
