package pt.nunosid.tgmedialibrary.telegram

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import pt.nunosid.tgmedialibrary.BuildConfig
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

sealed interface TelegramAuthState {
    data object Starting : TelegramAuthState
    data object NeedPhone : TelegramAuthState
    data object NeedCode : TelegramAuthState
    data object NeedPassword : TelegramAuthState
    data object Ready : TelegramAuthState
    data class Error(val message: String) : TelegramAuthState
}

class TelegramEngine(private val context: Context) {
    private val _authState = MutableStateFlow<TelegramAuthState>(TelegramAuthState.Starting)
    val authState: StateFlow<TelegramAuthState> = _authState.asStateFlow()

    private val _connectionLabel = MutableStateFlow("A iniciar")
    val connectionLabel: StateFlow<String> = _connectionLabel.asStateFlow()

    private var client: Client? = null

    init {
        if (BuildConfig.TELEGRAM_API_ID <= 0 || BuildConfig.TELEGRAM_API_HASH.isBlank()) {
            _authState.value = TelegramAuthState.Error(
                "Configura telegram.api_id e telegram.api_hash no local.properties antes de compilar."
            )
        } else {
            System.loadLibrary("tdjni")
            Client.execute(TdApi.SetLogVerbosityLevel(1))
            client = Client.create(
                { obj -> handleUpdate(obj) },
                { throwable -> _authState.value = TelegramAuthState.Error(throwable.message ?: "Erro TDLib") },
                { throwable -> _authState.value = TelegramAuthState.Error(throwable.message ?: "Erro TDLib") }
            )
        }
    }

    private fun handleUpdate(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateAuthorizationState -> handleAuthorizationState(obj.authorizationState)
            is TdApi.UpdateConnectionState -> _connectionLabel.value = when (obj.state) {
                is TdApi.ConnectionStateReady -> "Ligado"
                is TdApi.ConnectionStateConnecting -> "A ligar"
                is TdApi.ConnectionStateUpdating -> "A sincronizar"
                is TdApi.ConnectionStateWaitingForNetwork -> "Sem rede"
                else -> "A ligar"
            }
        }
    }

    private fun handleAuthorizationState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val base = File(context.filesDir, "tdlib").apply { mkdirs() }
                File(base, "files").mkdirs()
                val parameters = TdApi.SetTdlibParameters(
                    false,
                    base.absolutePath,
                    File(base, "files").absolutePath,
                    ByteArray(0),
                    true,
                    true,
                    true,
                    false,
                    BuildConfig.TELEGRAM_API_ID,
                    BuildConfig.TELEGRAM_API_HASH,
                    "pt",
                    android.os.Build.MODEL,
                    android.os.Build.VERSION.RELEASE,
                    BuildConfig.VERSION_NAME
                )
                send(parameters)
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> _authState.value = TelegramAuthState.NeedPhone
            is TdApi.AuthorizationStateWaitCode -> _authState.value = TelegramAuthState.NeedCode
            is TdApi.AuthorizationStateWaitPassword -> _authState.value = TelegramAuthState.NeedPassword
            is TdApi.AuthorizationStateReady -> _authState.value = TelegramAuthState.Ready
            is TdApi.AuthorizationStateClosing -> _connectionLabel.value = "A terminar"
            is TdApi.AuthorizationStateClosed -> _connectionLabel.value = "Fechado"
        }
    }

    fun submitPhone(phone: String) = send(TdApi.SetAuthenticationPhoneNumber(phone.trim(), null))
    fun submitCode(code: String) = send(TdApi.CheckAuthenticationCode(code.trim()))
    fun submitPassword(password: String) = send(TdApi.CheckAuthenticationPassword(password))

    fun <T : TdApi.Object> send(function: TdApi.Function<T>, onResult: ((T) -> Unit)? = null) {
        val active = client ?: return
        active.send(function) { result ->
            if (result is TdApi.Error) {
                _authState.value = TelegramAuthState.Error("${result.code}: ${result.message}")
            } else {
                @Suppress("UNCHECKED_CAST")
                onResult?.invoke(result as T)
            }
        }
    }

    fun <T : TdApi.Object> sendBlocking(function: TdApi.Function<T>, timeoutSeconds: Long = 30): T {
        val active = client ?: error("TDLib indisponível")
        val latch = CountDownLatch(1)
        val resultRef = AtomicReference<TdApi.Object>()
        active.send(function) { result ->
            resultRef.set(result)
            latch.countDown()
        }
        check(latch.await(timeoutSeconds, TimeUnit.SECONDS)) { "Timeout TDLib" }
        val result = resultRef.get()
        if (result is TdApi.Error) error("TDLib ${result.code}: ${result.message}")
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    fun close() {
        client?.send(TdApi.Close()) { }
        client = null
    }
}
