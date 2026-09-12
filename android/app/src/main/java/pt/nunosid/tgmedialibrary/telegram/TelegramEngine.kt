package pt.nunosid.tgmedialibrary.telegram

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import pt.nunosid.tgmedialibrary.security.SecureStore
import java.io.File
import java.util.concurrent.ConcurrentHashMap
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
    private val appContext = context.applicationContext
    private val legacyPrefs = appContext.getSharedPreferences("telegram_api", Context.MODE_PRIVATE)
    private val securityPrefs = appContext.getSharedPreferences("security_state", Context.MODE_PRIVATE)
    private val secureStore = SecureStore(appContext)

    private val _authState = MutableStateFlow<TelegramAuthState>(TelegramAuthState.Starting)
    val authState: StateFlow<TelegramAuthState> = _authState.asStateFlow()
    private val _connectionLabel = MutableStateFlow("A iniciar")
    val connectionLabel: StateFlow<String> = _connectionLabel.asStateFlow()
    private val transientFileIds = ConcurrentHashMap.newKeySet<Int>()

    private var client: Client? = null
    private var apiId = 0
    private var apiHash = ""

    init {
        migrateToEncryptedStorageOnce()
        val savedId = secureStore.getString(KEY_API_ID)?.toIntOrNull() ?: 0
        val savedHash = secureStore.getString(KEY_API_HASH).orEmpty()
        if (savedId > 0 && savedHash.isNotBlank()) configureApi(savedId.toString(), savedHash, persist = false)
        else _authState.value = TelegramAuthState.NeedApiCredentials
    }

    private fun migrateToEncryptedStorageOnce() {
        if (securityPrefs.getBoolean(MIGRATION_FLAG, false)) return

        // Preserve API credentials, but intentionally rebuild the old unencrypted TDLib database.
        val oldId = legacyPrefs.getInt("api_id", 0)
        val oldHash = legacyPrefs.getString("api_hash", "").orEmpty()
        if (oldId > 0 && oldHash.isNotBlank()) {
            secureStore.putString(KEY_API_ID, oldId.toString())
            secureStore.putString(KEY_API_HASH, oldHash)
        }
        legacyPrefs.edit().clear().commit()

        runCatching { File(appContext.filesDir, "tdlib").deleteRecursively() }
        purgeFilesystemCaches()
        securityPrefs.edit().putBoolean(MIGRATION_FLAG, true).commit()
    }

    fun configureApi(idText: String, hash: String, persist: Boolean = true) {
        val parsedId = idText.trim().toIntOrNull()
        if (parsedId == null || parsedId <= 0 || hash.trim().isBlank()) {
            _authState.value = TelegramAuthState.Error("API ID ou API Hash inválidos.")
            return
        }
        apiId = parsedId
        apiHash = hash.trim()
        if (persist) {
            secureStore.putString(KEY_API_ID, apiId.toString())
            secureStore.putString(KEY_API_HASH, apiHash)
            legacyPrefs.edit().clear().apply()
        }
        startClient()
    }

    fun resetApiCredentials() {
        purgePrivacyCaches()
        runCatching { client?.send(TdApi.Close()) { } }
        client = null
        secureStore.putString(KEY_API_ID, "")
        secureStore.putString(KEY_API_HASH, "")
        legacyPrefs.edit().clear().apply()
        apiId = 0
        apiHash = ""
        _connectionLabel.value = "A iniciar"
        _authState.value = TelegramAuthState.NeedApiCredentials
    }

    fun panicWipeLocal() {
        purgeTransientFilesAsync()
        val active = client
        if (active != null) {
            val latch = CountDownLatch(1)
            runCatching { active.send(TdApi.Close()) { latch.countDown() } }
            runCatching { latch.await(3, TimeUnit.SECONDS) }
        }
        client = null
        transientFileIds.clear()
        legacyPrefs.edit().clear().commit()
        secureStore.destroy()
        securityPrefs.edit().clear().commit()
        apiId = 0
        apiHash = ""

        runCatching { File(appContext.filesDir, "tdlib").deleteRecursively() }
        purgeFilesystemCaches()

        _connectionLabel.value = "Dados locais apagados"
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
                val base = File(appContext.filesDir, "tdlib").apply { mkdirs() }
                val media = File(base, "files").apply { mkdirs() }
                val version = runCatching {
                    appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
                }.getOrNull() ?: "0.7.0"
                val databaseKey = secureStore.getOrCreateRandomBytes(KEY_DATABASE_KEY, 32)

                send(TdApi.SetTdlibParameters(
                    false, base.absolutePath, media.absolutePath, databaseKey,
                    true, true, true, true,
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

    fun downloadFilePath(fileId: Int, priority: Int = 16, timeoutSeconds: Long = 1800): String {
        markTransientFile(fileId)
        val result = sendBlocking(
            TdApi.DownloadFile(fileId, priority, 0, 0, true),
            timeoutSeconds = timeoutSeconds
        )
        val path = result.local?.path.orEmpty()
        check(path.isNotBlank()) { "O Telegram não devolveu um ficheiro local." }
        return path
    }

    fun markTransientFile(fileId: Int) {
        transientFileIds.add(fileId)
    }

    fun purgeTransientFile(fileId: Int) {
        transientFileIds.remove(fileId)
        runCatching { sendBlocking(TdApi.CancelDownloadFile(fileId, false), timeoutSeconds = 5) }
        runCatching { sendBlocking(TdApi.DeleteFile(fileId), timeoutSeconds = 15) }
    }

    fun purgeTransientFileAsync(fileId: Int) {
        transientFileIds.remove(fileId)
        val active = client ?: return
        runCatching { active.send(TdApi.CancelDownloadFile(fileId, false)) { } }
        runCatching { active.send(TdApi.DeleteFile(fileId)) { } }
    }

    fun purgeTransientFilesAsync() {
        val ids = transientFileIds.toList()
        transientFileIds.clear()
        val active = client ?: return
        ids.forEach { fileId ->
            runCatching { active.send(TdApi.CancelDownloadFile(fileId, false)) { } }
            runCatching { active.send(TdApi.DeleteFile(fileId)) { } }
        }
    }

    fun purgePrivacyCaches() {
        purgeTransientFilesAsync()
        purgeFilesystemCaches()
    }

    private fun purgeFilesystemCaches() {
        runCatching { File(appContext.cacheDir, "telegram-share").deleteRecursively() }
        runCatching {
            appContext.cacheDir.listFiles()
                ?.filter { it.name.startsWith("tg-stream-") || it.name.startsWith("telegram-media-") }
                ?.forEach { it.deleteRecursively() }
        }
    }

    fun deleteLocalFile(fileId: Int) {
        transientFileIds.remove(fileId)
        runCatching { sendBlocking(TdApi.DeleteFile(fileId), timeoutSeconds = 30) }
    }

    fun deleteLocalFileAsync(fileId: Int) {
        transientFileIds.remove(fileId)
        val active = client ?: return
        runCatching { active.send(TdApi.DeleteFile(fileId)) { } }
    }

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
        purgePrivacyCaches()
        runCatching { client?.send(TdApi.Close()) { } }
        client = null
    }

    companion object {
        private const val MIGRATION_FLAG = "hardening_v1"
        private const val KEY_API_ID = "telegram_api_id"
        private const val KEY_API_HASH = "telegram_api_hash"
        private const val KEY_DATABASE_KEY = "tdlib_database_key"
    }
}
