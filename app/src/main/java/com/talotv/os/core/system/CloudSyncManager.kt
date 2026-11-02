package com.talotv.os.core.system

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.talotv.os.TaloApplication
import com.talotv.os.core.hardware.ProfileType
import com.talotv.os.core.profile.UserProfile
import com.talotv.os.core.security.DeviceFingerprint
import com.talotv.os.core.security.RemoteLockOverlayService
import com.talotv.os.core.security.TaloPrivacyShield
import com.talotv.os.core.security.TaloSecurityManager
import com.talotv.os.core.security.TaloSecurityVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val hasUpdate: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val apkUrl: String,
    val sha256: String,
    val changelog: String
)

object CloudSyncManager {

    private const val TAG = "CloudSyncManager"
    private const val API_BASE = "https://talo-tv.studio.ynh.fr/api"

    private val _isDeviceLocked = MutableStateFlow(false)
    val isDeviceLocked = _isDeviceLocked.asStateFlow()

    private val _lockMessage = MutableStateFlow("Ce téléviseur a été verrouillé à distance.")
    val lockMessage = _lockMessage.asStateFlow()

    private val _availableUpdate = MutableStateFlow<UpdateInfo?>(null)
    val availableUpdate = _availableUpdate.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private var rapidLostModeJob: Job? = null

    /**
     * Initializes security state directly from the hardware vault on process start
     */
    fun initSecurityState(context: Context) {
        val persistentLocked = TaloSecurityVault.isLocked(context)
        val persistentMessage = TaloSecurityVault.getLockMessage(context)

        _isDeviceLocked.value = persistentLocked
        _lockMessage.value = persistentMessage

        if (persistentLocked) {
            startRapidLostModePolling(context)
        }

        // Deploy Privacy Shield (Gilet pare-balle anti-télémétrie)
        TaloPrivacyShield.deployShield(context)
    }

    /**
     * Registers TV Pairing session on server with mutual cryptographic handshake
     */
    suspend fun registerPairingCode(context: Context, pairingCode: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val fingerprint = DeviceFingerprint.getFingerprint(context)
            Log.i(TAG, "Sync device fingerprint: $fingerprint")
            val model = Build.MODEL
            val url = URL("$API_BASE/pair.php?action=init")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 1500
                readTimeout = 1500
            }

            val nonce = TaloSecurityVault.generateNonce()
            val timestamp = System.currentTimeMillis()

            val payload = JSONObject().apply {
                put("pairingCode", pairingCode)
                put("deviceFingerprint", fingerprint)
                put("deviceModel", model)
                put("osVersion", "0.0.1")
                put("nonce", nonce)
                put("timestamp", timestamp)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val res = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(res)
                val secret = json.optString("device_secret", "")
                if (secret.isNotEmpty()) {
                    TaloSecurityVault.setDeviceSecret(context, secret)
                }
                conn.disconnect()
                true
            } else {
                conn.disconnect()
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pairing init network error: ${e.message}")
            false
        }
    }

    sealed class PairingPollState {
        object Pending : PairingPollState()
        data class AwaitingTvConfirmation(val userName: String, val userEmail: String, val isNewAccount: Boolean) : PairingPollState()
        data class Claimed(val userName: String, val isNewAccount: Boolean, val profiles: List<UserProfile>?) : PairingPollState()
        data class Error(val error: String) : PairingPollState()
    }

    /**
     * Polls server with detailed state detection (detects phone request and prompts TV confirmation)
     */
    suspend fun pollPairingStatusDetailed(
        context: Context,
        pairingCode: String
    ): PairingPollState = withContext(Dispatchers.IO) {
        try {
            val fingerprint = DeviceFingerprint.getFingerprint(context)
            val url = URL("$API_BASE/pair.php?action=poll&code=$pairingCode&fingerprint=$fingerprint")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }

            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return@withContext PairingPollState.Error("Erreur réseau (${conn.responseCode})")
            }

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(responseText)
            val status = json.optString("status", "")

            if (status == "awaiting_tv_confirmation") {
                val userName = json.optString("user_name", "Utilisateur Talo")
                val userEmail = json.optString("user_email", "")
                val isNewAccount = json.optBoolean("is_new_account", false)
                return@withContext PairingPollState.AwaitingTvConfirmation(userName, userEmail, isNewAccount)
            }

            val isClaimed = json.optBoolean("claimed", false)
            if (!isClaimed) {
                return@withContext PairingPollState.Pending
            }

            // Save negotiated device secret
            val deviceSecret = json.optString("device_secret", "")
            if (deviceSecret.isNotEmpty()) {
                TaloSecurityVault.setDeviceSecret(context, deviceSecret)
            }

            val userObj = json.optJSONObject("user")
            val userName = userObj?.optString("name") ?: "Utilisateur Talo"
            val userEmail = userObj?.optString("email") ?: ""
            val emailToSave = if (userEmail.isNotEmpty()) userEmail else "$userName@talotv.com"
            setCloudAccountInfo(context, userName, emailToSave)

            val isNewAccount = json.optBoolean("is_new_account", false)
            val profilesArray = json.optJSONArray("profiles")
            val profilesList = parseProfilesArray(profilesArray)

            val hwProfile = json.optJSONObject("device")?.optString("hardware_profile")
            if (!hwProfile.isNullOrEmpty()) {
                applyHwProfile(hwProfile)
            }

            PairingPollState.Claimed(userName, isNewAccount, profilesList)
        } catch (e: Exception) {
            Log.w(TAG, "Pairing poll detailed error: ${e.message}")
            PairingPollState.Error(e.message ?: "Erreur réseau")
        }
    }

    /**
     * Confirms the pairing request from the TV screen
     */
    suspend fun confirmPairingFromTv(
        context: Context,
        pairingCode: String
    ): PairingPollState = withContext(Dispatchers.IO) {
        try {
            val fingerprint = DeviceFingerprint.getFingerprint(context)
            val url = URL("$API_BASE/pair.php?action=confirm")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 1500
                readTimeout = 1500
            }

            val payload = JSONObject().apply {
                put("pairingCode", pairingCode)
                put("deviceFingerprint", fingerprint)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

            if (conn.responseCode in 200..299) {
                val res = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val json = JSONObject(res)

                val deviceSecret = json.optString("device_secret", "")
                if (deviceSecret.isNotEmpty()) {
                    TaloSecurityVault.setDeviceSecret(context, deviceSecret)
                }

                val userObj = json.optJSONObject("user")
                val userName = userObj?.optString("name") ?: "Utilisateur Talo"
                val userEmail = userObj?.optString("email") ?: ""
                setCloudAccountInfo(context, userName, if (userEmail.isNotEmpty()) userEmail else "$userName@talotv.com")

                val isNewAccount = json.optBoolean("is_new_account", false)
                val profilesArray = json.optJSONArray("profiles")
                val profilesList = parseProfilesArray(profilesArray)

                PairingPollState.Claimed(userName, isNewAccount, profilesList)
            } else {
                conn.disconnect()
                PairingPollState.Error("Erreur de confirmation (${conn.responseCode})")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Confirm pairing error: ${e.message}")
            PairingPollState.Error(e.message ?: "Erreur réseau")
        }
    }

    /**
     * Polls server to detect when user claims the pairing code on Web
     */
    suspend fun pollPairingStatus(
        context: Context,
        pairingCode: String
    ): Triple<Boolean, String?, List<UserProfile>?> = withContext(Dispatchers.IO) {
        try {
            val fingerprint = DeviceFingerprint.getFingerprint(context)
            val url = URL("$API_BASE/pair.php?action=poll&code=$pairingCode&fingerprint=$fingerprint")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }

            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return@withContext Triple(false, null, null)
            }

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(responseText)
            val isClaimed = json.optBoolean("claimed", false)
            if (!isClaimed) {
                return@withContext Triple(false, null, null)
            }

            // Save negotiated device secret
            val deviceSecret = json.optString("device_secret", "")
            if (deviceSecret.isNotEmpty()) {
                TaloSecurityVault.setDeviceSecret(context, deviceSecret)
            }

            val userObj = json.optJSONObject("user")
            val userName = userObj?.optString("name")
            val userEmail = userObj?.optString("email")
            if (!userName.isNullOrEmpty()) {
                val emailToSave = if (!userEmail.isNullOrEmpty()) userEmail else "$userName@talotv.com"
                setCloudAccountInfo(context, userName, emailToSave)
            }

            val profilesArray = json.optJSONArray("profiles")
            val profilesList = parseProfilesArray(profilesArray)

            val hwProfile = json.optJSONObject("device")?.optString("hardware_profile")
            if (!hwProfile.isNullOrEmpty()) {
                applyHwProfile(hwProfile)
            }

            Triple(true, userName, profilesList)
        } catch (e: Exception) {
            Log.w(TAG, "Pairing poll error: ${e.message}")
            Triple(false, null, null)
        }
    }

    fun getCloudAccountInfo(context: Context): Pair<String, String> {
        val prefs = context.getSharedPreferences("talo_cloud_account_prefs", Context.MODE_PRIVATE)
        var name = prefs.getString("cloud_account_name", null)
        var email = prefs.getString("cloud_account_email", null)

        if (name.isNullOrBlank() || email.isNullOrBlank()) {
            try {
                val am = android.accounts.AccountManager.get(context)
                val accounts = am.getAccountsByType("com.google")
                if (accounts.isNotEmpty()) {
                    val accEmail = accounts[0].name
                    email = accEmail
                    name = accEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                }
            } catch (_: Exception) {}
        }

        if (name.isNullOrBlank()) {
            name = "Alexandre"
        }
        if (email.isNullOrBlank()) {
            email = "alexandre@talotv.com"
        }

        return Pair(name, email)
    }

    fun setCloudAccountInfo(context: Context, name: String, email: String) {
        val prefs = context.getSharedPreferences("talo_cloud_account_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("cloud_account_name", name)
            .putString("cloud_account_email", email)
            .apply()
    }

    /**
     * Background Telemetry, Profile Sync, Lost Mode & OS Updates
     */
    fun startBackgroundSync(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            performSync(context)
        }
    }

    private suspend fun performSync(context: Context) = withContext(Dispatchers.IO) {
        try {
            _isSyncing.value = true
            val fingerprint = DeviceFingerprint.getFingerprint(context)
            Log.i(TAG, "Sync device fingerprint: $fingerprint")
            val model = Build.MODEL
            val osVer = "0.0.1"
            val deviceSecret = TaloSecurityVault.getDeviceSecret(context)

            val timestamp = System.currentTimeMillis()
            val nonce = TaloSecurityVault.generateNonce()

            val payload = JSONObject().apply {
                put("fingerprint", fingerprint)
                put("osVersion", osVer)
                put("model", model)
                put("isLocked", _isDeviceLocked.value)
            }
            val payloadString = payload.toString()

            val bodyHash = try {
                val md = java.security.MessageDigest.getInstance("SHA-256")
                md.digest(payloadString.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            } catch (e: Exception) { "" }

            val signTarget = if (bodyHash.isNotEmpty()) "$timestamp.$nonce.$fingerprint.$bodyHash" else "$timestamp.$nonce.$fingerprint"
            val signature = if (deviceSecret.isNotEmpty()) {
                TaloSecurityVault.signPayload(deviceSecret, signTarget)
            } else ""

            val url = URL("$API_BASE/sync.php?action=heartbeat")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                if (signature.isNotEmpty()) {
                    setRequestProperty("X-Talo-Signature", signature)
                    setRequestProperty("X-Talo-Timestamp", timestamp.toString())
                    setRequestProperty("X-Talo-Nonce", nonce)
                }
                doOutput = true
                connectTimeout = 7000
                readTimeout = 7000
            }

            OutputStreamWriter(conn.outputStream).use { it.write(payloadString) }

            if (conn.responseCode in 200..299) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)

                // 1. Lost Mode (Mode Perdu) Check
                val deviceObj = json.optJSONObject("device")
                if (deviceObj != null) {
                    val isLocked = deviceObj.optBoolean("is_locked", false)
                    val lockMsg = deviceObj.optString("lock_message", "Ce téléviseur a été verrouillé à distance.")
                    updateLockState(context, isLocked, lockMsg)
                }

                // 2. Hardware profile sync
                val hwProfile = deviceObj?.optString("hardware_profile")
                if (!hwProfile.isNullOrEmpty()) {
                    applyHwProfile(hwProfile)
                }

                // 3. User Profiles Cloud Sync
                val profilesArray = json.optJSONArray("profiles")
                val profilesList = parseProfilesArray(profilesArray)
                if (profilesList.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        TaloApplication.instance.userProfileManager.updateFromCloud(profilesList)
                    }
                }

                // 4. OS Update Check with SHA-256 verification
                val updateObj = json.optJSONObject("update")
                if (updateObj != null) {
                    val hasUpdate = updateObj.optBoolean("has_update", false)
                    val latestVer = updateObj.optString("latest_version", "0.0.1")
                    val apkUrl = updateObj.optString("apk_url", "")
                    val sha256 = updateObj.optString("sha256", "")
                    val changelog = updateObj.optString("changelog", "")

                    if (hasUpdate) {
                        _availableUpdate.value = UpdateInfo(
                            hasUpdate = true,
                            currentVersion = osVer,
                            latestVersion = latestVer,
                            apkUrl = apkUrl,
                            sha256 = sha256,
                            changelog = changelog
                        )
                    } else {
                        _availableUpdate.value = null
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "Background sync failed: ${e.message}")
        } finally {
            _isSyncing.value = false
        }
    }

    var isTestLockActive = false

    fun updateLockState(context: Context, isLocked: Boolean, lockMsg: String) {
        if (isTestLockActive && !isLocked) {
            return // Prevent test lock from being prematurely cancelled by older server response
        }
        val wasLocked = _isDeviceLocked.value
        _isDeviceLocked.value = isLocked
        _lockMessage.value = lockMsg

        // Persist to hardware-backed secure vault
        TaloSecurityVault.setLocked(context, isLocked, lockMsg)

        // Trigger or remove overlay service
        val lockIntent = Intent(context, RemoteLockOverlayService::class.java).apply {
            putExtra("IS_LOCKED", isLocked)
            putExtra("LOCK_MESSAGE", lockMsg)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isLocked) {
                context.startForegroundService(lockIntent)
            } else {
                context.startService(lockIntent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot start overlay service: ${e.message}")
        }

        if (isLocked) {
            try {
                val mainIntent = Intent(context, com.talotv.os.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
                context.startActivity(mainIntent)
            } catch (e: Exception) {
                // ignore
            }
        }

        // Manage rapid heartbeat for quick remote unlock
        if (isLocked && !wasLocked) {
            startRapidLostModePolling(context)
        } else if (!isLocked && wasLocked) {
            rapidLostModeJob?.cancel()
            rapidLostModeJob = null
        }
    }

    /**
     * When TV is in Lost Mode, poll server every 4 seconds to unlock immediately
     * when the owner clicks "Déverrouiller" on the web portal.
     */
    private fun startRapidLostModePolling(context: Context) {
        rapidLostModeJob?.cancel()
        rapidLostModeJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && _isDeviceLocked.value) {
                delay(4000)
                performSync(context)
            }
        }
    }

    fun unlockLocally(context: Context) {
        _isDeviceLocked.value = false
        TaloSecurityVault.setLocked(context, false)
        rapidLostModeJob?.cancel()
        rapidLostModeJob = null

        // Stop overlay service
        val lockIntent = Intent(context, RemoteLockOverlayService::class.java).apply {
            putExtra("IS_LOCKED", false)
        }
        try {
            context.startService(lockIntent)
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun parseProfilesArray(array: JSONArray?): List<UserProfile> {
        if (array == null) return emptyList()
        val list = mutableListOf<UserProfile>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val hexStr = obj.optString("avatar_color_hex", obj.optString("avatarColorHex", "0xFFFFFFFF"))
            val colorLong = try {
                if (hexStr.startsWith("#")) {
                    android.graphics.Color.parseColor(hexStr).toLong() or 0xFF000000
                } else if (hexStr.startsWith("0x") || hexStr.startsWith("0X")) {
                    hexStr.substring(2).toLong(16)
                } else {
                    hexStr.toLong()
                }
            } catch (e: Exception) {
                0xFFFFFFFF
            }

            list.add(
                UserProfile(
                    id = obj.optString("id", "user-$i"),
                    name = obj.optString("name", "Profil"),
                    ageTier = obj.optString("age_tier", obj.optString("ageTier", "25 - 34")),
                    isPrimary = obj.optBoolean("is_primary", obj.optBoolean("isPrimary", i == 0)),
                    avatarColorHex = colorLong,
                    hasPinLock = obj.optBoolean("has_pin_lock", obj.optBoolean("hasPinLock", false)),
                    pinCode = obj.optString("pin_code", obj.optString("pinCode", "")),
                    growthStage = obj.optInt("growth_stage", obj.optInt("growthStage", 5))
                )
            )
        }
        return list
    }

    private fun applyHwProfile(profileName: String) {
        try {
            val profType = ProfileType.valueOf(profileName.uppercase())
            TaloApplication.instance.profileManager.setProfileType(profType)
        } catch (e: Exception) {
            // keep current
        }
    }
}
