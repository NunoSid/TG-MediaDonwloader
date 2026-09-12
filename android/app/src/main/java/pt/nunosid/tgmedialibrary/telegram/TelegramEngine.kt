package pt.nunosid.tgmedialibrary.telegram

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

sealed interface TelegramAuthState {
    data object Starting : TelegramAuthState
    data object NeedApiCredentials : TelegramAuthState
    data object NeedPhone : TelegramAuthState
    data object NeedCode : TelegramAuthState
    data object NeedPassword : TelegramAuthState
    data object Ready : TelegramAuthState
    data class Error(val message: String) : TelegramAuthState
}

class TelegramEngine(private val context: Context) {
    private val prefs = context.getSharedPreferences("telegram_api", Context.MODE_PRIVATE)
    private val _authState = MutableStateFlow<TelegramAuthState>(TelegramAuthState.Starting)
    val authState: StateFlow<TelegramAuthState> = _authState.asStateFlow()
    private val _connectionLabel = MutableStateFlow("A iniciar")
    val connectionLabel: StateFlow<String> = _connectionLabel.asStateFlow()

    private var client: Client? = null
    private var apiId = 0
    private var apiHash = ""

    init {
        val savedId = prefs.getInt("api_id", 0)
        val savedHash = prefs.getString("api_hash", "").orEmpty()
        if (savedId > 0 && savedHash.isNotBlank()) configureApi(savedId.toString(), savedHash, persist = false)
        else _authState.value = TelegramAuthState.NeedApiCredentials
    }

    fun configureApi(idText: String, hash: String, persist: Boolean = true) {
        val parsedId = idText.trim().toIntOrNull()
        if (parsedId == null || parsedId <= 0 || hash.trim().isBlank()) {
            _authState.value = TelegramAuthState.Error("API ID ou API Hash inválidos.")
            return
        }
        apiId = parsedId
        apiHash = hash.trim()
        if (persist) prefs.edit().putInt("api_id", apiId).putString("api_hash", apiHash).apply()
        startClient()
    }

    fun resetApiCredentials() {
        runCatching { client?.send(TdApi.Close()) { } }
        client = null
        prefs.edit().clear().apply()
        apiId = 0
        apiHash = ""
        _connectionLabel.value = "A iniciar"
        _authState.value = TelegramAuthState.NeedApiCredentials
    }

    private fun startClient() {
        if (client != null) return
        _authState.value = TelegramAuthState.Starting
        runCatching { System.loadLibrary("tdjni") }
            .onFailure {
                _authState.value = TelegramAuthState.Error("Não foi possível carregar TDLib: ${it.message}")
                return
            }
        Client.execute(TdApi.SetLogVerbosityLevel(1))
        client = Client.create(
            { obj -> handleUpdate(obj) },
            { throwable -> _authState.value = TelegramAuthState.Error(throwable.message ?: "Erro TDLib") },
            { throwable -> _authState.value = TelegramAuthState.Error(throwable.message ?: "Erro TDLib") }
        )
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
                val media = File(base, "files").apply { mkdirs() }
                val version = runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull() ?: "0.1.0"
                send(TdApi.SetTdlibParameters(
                    false, base.absolutePath, media.absolutePath, ByteArray(0),
                    true, true, true, false,
                    apiId, apiHash, "pt", android.os.Build.MODEL,
                    android.os.Build.VERSION.RELEASE, version
                ))
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
            if (result is TdApi.Error) _authState.value = TelegramAuthState.Error("${result.code}: ${result.message}")
            else {
                @Suppress("UNCHECKED_CAST")
                onResult?.invoke(result as T)
            }
        }
    }

    fun <T : TdApi.Object> sendBlocking(function: TdApi.Function<T>, timeoutSeconds: Long = 30): T {
        val active = client ?: error("TDLib indisponível")
        val latch = CountDownLatch(1)
        val resultRef = AtomicReference<TdApi.Object>()
        active.send(function) { result -> resultRef.set(result); latch.countDown() }
        check(latch.await(timeoutSeconds, TimeUnit.SECONDS)) { "Timeout TDLib" }
        val result = resultRef.get()
        if (result is TdApi.Error) error("TDLib ${result.code}: ${result.message}")
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    fun close() {
        runCatching { client?.send(TdApi.Close()) { } }
        client = null
    }
}
