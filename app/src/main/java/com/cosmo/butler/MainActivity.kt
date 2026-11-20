
package com.cosmo.butler

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================================
// HUD Color Palette
// ============================================================================

private val HudObsidian = Color(0xFF06090F)
private val HudCard = Color(0xFF0D131F)
private val HudCardElevated = Color(0xFF131C2E)
private val HudBorder = Color(0xFF1E2D45)
private val CyanAccent = Color(0xFF00E5FF)
private val AmberAccent = Color(0xFFFFB300)
private val CrimsonAccent = Color(0xFFFF2A4B)
private val EmeraldAccent = Color(0xFF00E676)
private val HudTextPrimary = Color(0xFFEAF2FF)
private val HudTextSecondary = Color(0xFF9EB0CE)
private val HudTextMuted = Color(0xFF637696)
private val LogOkGreen = Color(0xFF39FF88)
private val LogErrRed = Color(0xFFFF526B)

// ============================================================================
// 1. RootShell — Safe `su` Command Runner
// ============================================================================

data class ShellResult(
    val command: String,
    val exitCode: Int,
    val output: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}

object RootShell {

    fun exec(cmd: String): ShellResult {
        return try {
            val process = ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readText().trim()
            }
            val exitCode = process.waitFor()
            ShellResult(cmd, exitCode, output)
        } catch (e: Exception) {
            ShellResult(cmd, -1, e.message ?: "su binary unavailable")
        }
    }

    fun isRootAvailable(): Boolean =
        exec("id").let { it.isSuccess && it.output.contains("uid=0") }

    fun forceStopApps(packages: Collection<String>): List<ShellResult> =
        packages.filter { it.isNotBlank() }.map { pkg ->
            exec("am force-stop ${pkg.trim()}")
        }

    fun setRefreshRate120Hz(): ShellResult =
        exec("settings put system peak_refresh_rate 120.0; settings put system min_refresh_rate 120.0")

    fun setRefreshRate60Hz(): ShellResult =
        exec("settings put system peak_refresh_rate 60.0; settings put system min_refresh_rate 60.0")

    fun resetRefreshRateAuto(): ShellResult =
        exec("settings delete system min_refresh_rate; settings put system peak_refresh_rate 120.0")

    fun clearRamCaches(): ShellResult =
        exec("sync; echo 3 > /proc/sys/vm/drop_caches")

    fun setDnd(enabled: Boolean): ShellResult =
        exec("cmd notification set_dnd ${if (enabled) "on" else "off"}")
}

// ============================================================================
// 2. Modes, Preferences & Execution
// ============================================================================

enum class CosmoMode(
    val title: String,
    val summary: String,
    val accent: Color,
    val icon: ImageVector
) {
    STUDY("Study Mode", "DND ON • KILL DISTRACTIONS", CyanAccent, Icons.Filled.MenuBook),
    GAME("Game Mode", "120HZ LOCK • DROP CACHES • DND", AmberAccent, Icons.Filled.SportsEsports),
    SLEEP("Sleep Mode", "RED OVERLAY • DND • 60HZ", CrimsonAccent, Icons.Filled.Bedtime),
    NORMAL("Normal Mode", "CLEAR FILTER • DND OFF • AUTO HZ", EmeraldAccent, Icons.Filled.PowerSettingsNew)
}

object CosmoPrefs {
    private const val PREFS_NAME = "cosmo_life_os_prefs"
    private const val KEY_ACTIVE_MODE = "active_mode"
    private const val KEY_RED_ALPHA = "red_overlay_alpha"
    private const val KEY_DISTRACTING_PKGS = "distracting_packages"

    val DEFAULT_DISTRACTING_PACKAGES = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.google.android.youtube",
        "com.twitter.android",
        "com.reddit.frontpage",
        "com.facebook.katana"
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getActiveMode(context: Context): CosmoMode {
        val raw = prefs(context).getString(KEY_ACTIVE_MODE, CosmoMode.NORMAL.name)
        return runCatching { CosmoMode.valueOf(raw ?: CosmoMode.NORMAL.name) }
            .getOrDefault(CosmoMode.NORMAL)
    }

    fun setActiveMode(context: Context, mode: CosmoMode) {
        prefs(context).edit().putString(KEY_ACTIVE_MODE, mode.name).apply()
    }

    fun getRedOverlayAlpha(context: Context): Float =
        prefs(context).getFloat(KEY_RED_ALPHA, RedOverlayService.DEFAULT_ALPHA)
            .coerceIn(RedOverlayService.MIN_ALPHA, RedOverlayService.MAX_ALPHA)

    fun setRedOverlayAlpha(context: Context, alpha: Float) {
        prefs(context).edit().putFloat(KEY_RED_ALPHA, alpha).apply()
    }

    fun getDistractingPackages(context: Context): List<String> {
        val saved = prefs(context).getStringSet(KEY_DISTRACTING_PKGS, null)
        return (saved ?: DEFAULT_DISTRACTING_PACKAGES).sorted()
    }

    fun setDistractingPackages(context: Context, packages: Set<String>) {
        prefs(context).edit().putStringSet(KEY_DISTRACTING_PKGS, packages).apply()
    }
}

object CosmoModeExecutor {

    fun applyMode(
        context: Context,
        mode: CosmoMode,
        distractingPackages: List<String>,
        redOverlayAlpha: Float
    ): List<ShellResult> {
        val results = mutableListOf<ShellResult>()
        when (mode) {
            CosmoMode.STUDY -> {
                RedOverlayService.stop(context)
                results += RootShell.setDnd(true)
                results += RootShell.forceStopApps(distractingPackages)
            }
            CosmoMode.GAME -> {
                RedOverlayService.stop(context)
                results += RootShell.setRefreshRate120Hz()
                results += RootShell.clearRamCaches()
                results += RootShell.setDnd(true)
            }
            CosmoMode.SLEEP -> {
                if (Settings.canDrawOverlays(context)) {
                    RedOverlayService.start(context, redOverlayAlpha)
                }
                results += RootShell.setDnd(true)
                results += RootShell.setRefreshRate60Hz()
                results += RootShell.forceStopApps(distractingPackages)
            }
            CosmoMode.NORMAL -> {
                RedOverlayService.stop(context)
                results += RootShell.setDnd(false)
                results += RootShell.resetRefreshRateAuto()
            }
        }
        CosmoPrefs.setActiveMode(context, mode)
        return results
    }
}

// ============================================================================
// 3. ViewModel & UI State
// ============================================================================

enum class RootStatus { CHECKING, ONLINE, DENIED }

data class TelemetryLogEntry(
    val id: Long,
    val timestamp: String,
    val summary: String,
    val isSuccess: Boolean
)

data class CosmoUiState(
    val activeMode: CosmoMode = CosmoMode.NORMAL,
    val rootStatus: RootStatus = RootStatus.CHECKING,
    val hasOverlayPermission: Boolean = false,
    val isExecuting: Boolean = false,
    val redOverlayAlpha: Float = RedOverlayService.DEFAULT_ALPHA,
    val distractingPackages: List<String> = emptyList(),
    val logs: List<TelemetryLogEntry> = emptyList()
)

class CosmoViewModel(application: Application) : AndroidViewModel(application) {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private var logSeq = 0L

    private val _uiState = MutableStateFlow(
        CosmoUiState(
            activeMode = CosmoPrefs.getActiveMode(application),
            hasOverlayPermission = Settings.canDrawOverlays(application),
            redOverlayAlpha = CosmoPrefs.getRedOverlayAlpha(application),
            distractingPackages = CosmoPrefs.getDistractingPackages(application)
        )
    )
    val uiState: StateFlow<CosmoUiState> = _uiState.asStateFlow()

    init {
        probeRoot()
        viewModelScope.launch {
            RedOverlayService.isRunning.collect { running ->
                if (!running && _uiState.value.activeMode == CosmoMode.SLEEP) {
                    _uiState.update { it.copy(activeMode = CosmoMode.NORMAL) }
                    CosmoPrefs.setActiveMode(getApplication(), CosmoMode.NORMAL)
                }
            }
        }
    }

    fun refreshOverlayPermission() {
        val granted = Settings.canDrawOverlays(getApplication())
        _uiState.update { it.copy(hasOverlayPermission = granted) }
    }

    fun probeRoot() {
        _uiState.update { it.copy(rootStatus = RootStatus.CHECKING) }
        viewModelScope.launch {
            val available = withContext(Dispatchers.IO) { RootShell.isRootAvailable() }
            _uiState.update {
                it.copy(rootStatus = if (available) RootStatus.ONLINE else RootStatus.DENIED)
            }
            appendLog(
                summary = if (available) "su uid=0 verified" else "su access unavailable (non-root)",
                isSuccess = available
            )
        }
    }

    fun activateMode(mode: CosmoMode) {
        val context = getApplication<Application>()
        val current = _uiState.value
        _uiState.update { it.copy(activeMode = mode, isExecuting = true) }

        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) {
                CosmoModeExecutor.applyMode(
                    context = context,
                    mode = mode,
                    distractingPackages = current.distractingPackages,
                    redOverlayAlpha = current.redOverlayAlpha
                )
            }
            val succeeded = results.count { it.isSuccess }
            val total = results.size
            appendLog(
                summary = "[${mode.name}] $succeeded/$total su cmds ok" +
                    if (mode == CosmoMode.SLEEP && !Settings.canDrawOverlays(context)) " • grant overlay!" else "",
                isSuccess = succeeded == total
            )
            results.takeLast(3).forEach { res ->
                val shortCmd = res.command.take(44)
                val detail = if (res.isSuccess) "OK" else res.output.ifBlank { "ERR ${res.exitCode}" }.take(32)
                appendLog("$shortCmd -> $detail", res.isSuccess)
            }
            _uiState.update {
                it.copy(
                    isExecuting = false,
                    hasOverlayPermission = Settings.canDrawOverlays(context)
                )
            }
        }
    }

    fun updateRedOverlayAlpha(alpha: Float) {
        val clamped = alpha.coerceIn(RedOverlayService.MIN_ALPHA, RedOverlayService.MAX_ALPHA)
        _uiState.update { it.copy(redOverlayAlpha = clamped) }
        val context = getApplication<Application>()
        CosmoPrefs.setRedOverlayAlpha(context, clamped)
        RedOverlayService.updateAlpha(context, clamped)
    }

    fun addDistractingPackage(pkg: String) {
        val clean = pkg.trim()
        if (clean.isEmpty()) return
        val updated = (_uiState.value.distractingPackages + clean).distinct().sorted()
        CosmoPrefs.setDistractingPackages(getApplication(), updated.toSet())
        _uiState.update { it.copy(distractingPackages = updated) }
    }

    fun removeDistractingPackage(pkg: String) {
        val updated = _uiState.value.distractingPackages.filterNot { it == pkg }
        CosmoPrefs.setDistractingPackages(getApplication(), updated.toSet())
        _uiState.update { it.copy(distractingPackages = updated) }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList()) }
    }

    private fun appendLog(summary: String, isSuccess: Boolean) {
        val entry = TelemetryLogEntry(
            id = ++logSeq,
            timestamp = timeFormat.format(Date()),
            summary = summary,
            isSuccess = isSuccess
        )
        _uiState.update { state ->
            state.copy(logs = (listOf(entry) + state.logs).take(16))
        }
    }
}

// ============================================================================
// 4. MainActivity & HUD UI
// ============================================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = CyanAccent,
                    onPrimary = HudObsidian,
                    secondary = AmberAccent,
                    tertiary = CrimsonAccent,
                    background = HudObsidian,
                    onBackground = HudTextPrimary,
                    surface = HudCard,
                    onSurface = HudTextPrimary,
                    surfaceVariant = HudCardElevated,
                    onSurfaceVariant = HudTextSecondary,
                    outline = HudBorder
                )
            ) {
                CosmoHudScreen()
            }
        }
    }
}

@Composable
fun CosmoHudScreen(viewModel: CosmoViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var showTargetsDialog by remember { mutableStateOf(false) }

    // Voice & AI Bar state
    var isListening by remember { mutableStateOf(false) }
    var isThinking by remember { mutableStateOf(false) }
    var voiceStatus by remember { mutableStateOf("Voice standby") }
    var aiReply by remember { mutableStateOf("") }
    var chatInput by remember { mutableStateOf("") }

    val cosmoBrain = remember { CosmoBrain() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    fun requestOve
