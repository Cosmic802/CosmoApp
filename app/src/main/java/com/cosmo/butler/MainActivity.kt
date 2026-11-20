package com.cosmo.butler

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.viewModel
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

    fun isRootAvailable(): Boolean = exec("id").let { it.isSuccess && it.output.contains("uid=0") }

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

enum class CosmoMode(
    val titleRes: Int,
    val descRes: Int,
    val accent: Color,
    val icon: ImageVector,
    val testTag: String
) {
    STUDY(R.string.mode_study_title, R.string.mode_study_desc, StudyCyan, Icons.Filled.MenuBook, "mode_study_button"),
    GAME(R.string.mode_game_title, R.string.mode_game_desc, GameAmber, Icons.Filled.SportsEsports, "mode_game_button"),
    SLEEP(R.string.mode_sleep_title, R.string.mode_sleep_desc, SleepCrimson, Icons.Filled.Bedtime, "mode_sleep_button"),
    NORMAL(R.string.mode_normal_title, R.string.mode_normal_desc, NormalEmerald, Icons.Filled.PowerSettingsNew, "mode_normal_button")
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
            val headerOk = succeeded == total
            appendLog(
                summary = "[${mode.name}] $succeeded/$total su cmds ok" +
                    if (mode == CosmoMode.SLEEP && !Settings.canDrawOverlays(context)) " • grant overlay!" else "",
                isSuccess = headerOk
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            CosmoTheme {
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
    var showTargetsDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshOverlayPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }

    fun handleModeClick(mode: CosmoMode) {
        if (mode == CosmoMode.SLEEP) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (!Settings.canDrawOverlays(context)) {
                requestOverlayPermission()
            }
        }
        viewModel.activateMode(mode)
    }

    val activeAccent by animateColorAsState(
        targetValue = state.activeMode.accent,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "hudAccent"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(activeAccent.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.14f),
                    radius = size.width * 0.85f
                )
            )
            val strokeColor = activeAccent.copy(alpha = 0.16f)
            val gridStep = 48.dp.toPx()
            var y = gridStep
            while (y < size.height * 0.45f) {
                drawLine(strokeColor.copy(alpha = 0.05f), Offset(0f, y), Offset(size.width, y), 1f)
                y += gridStep
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .navigationBarsPadding()
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HudHeader(
                activeAccent = activeAccent,
                rootStatus = state.rootStatus,
                hasOverlayPermission = state.hasOverlayPermission,
                targetCount = state.distractingPackages.size,
                onProbeRoot = viewModel::probeRoot,
                onRequestOverlay = ::requestOverlayPermission,
                onOpenTargets = { showTargetsDialog = true }
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModeTile(
                        mode = CosmoMode.STUDY,
                        isSelected = state.activeMode == CosmoMode.STUDY,
                        isExecuting = state.isExecuting && state.activeMode == CosmoMode.STUDY,
                        onClick = { handleModeClick(CosmoMode.STUDY) },
                        modifier = Modifier.weight(1f)
                    )
                    ModeTile(
                        mode = CosmoMode.GAME,
                        isSelected = state.activeMode == CosmoMode.GAME,
                        isExecuting = state.isExecuting && state.activeMode == CosmoMode.GAME,
                        onClick = { handleModeClick(CosmoMode.GAME) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModeTile(
                        mode = CosmoMode.SLEEP,
                        isSelected = state.activeMode == CosmoMode.SLEEP,
                        isExecuting = state.isExecuting && state.activeMode == CosmoMode.SLEEP,
                        onClick = { handleModeClick(CosmoMode.SLEEP) },
                        modifier = Modifier.weight(1f)
                    )
                    ModeTile(
                        mode = CosmoMode.NORMAL,
                        isSelected = state.activeMode == CosmoMode.NORMAL,
                        isExecuting = state.isExecuting && state.activeMode == CosmoMode.NORMAL,
                        onClick = { handleModeClick(CosmoMode.NORMAL) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            AnimatedVisibility(
                visible = state.activeMode == CosmoMode.SLEEP,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                RedFilterSliderCard(
                    alpha = state.redOverlayAlpha,
                    onAlphaChange = viewModel::updateRedOverlayAlpha
                )
            }

            TelemetryLogCard(
                logs = state.logs,
                accentColor = activeAccent,
                onClear = viewModel::clearLogs
            )
        }
    }

    if (showTargetsDialog) {
        DistractingTargetsDialog(
            packages = state.distractingPackages,
            onAdd = viewModel::addDistractingPackage,
            onRemove = viewModel::removeDistractingPackage,
            onDismiss = { showTargetsDialog = false }
        )
    }
}

@Composable
private fun HudHeader(
    activeAccent: Color,
    rootStatus: RootStatus,
    hasOverlayPermission: Boolean,
    targetCount: Int,
    onProbeRoot: () -> Unit,
    onRequestOverlay: () -> Unit,
    onOpenTargets: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(activeAccent)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.app_name).uppercase(Locale.US),
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary
                    )
                }
                Text(
                    text = stringResource(R.string.cosmo_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = HudSurfaceElevated,
                border = BorderStroke(1.dp, HudOutline),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenTargets)
                    .testTag("targets_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = stringResource(R.string.cd_targets_button),
                        tint = activeAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${stringResource(R.string.action_targets)} ($targetCount)",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val (rootText, rootColor) = when (rootStatus) {
                RootStatus.ONLINE -> stringResource(R.string.status_root_online) to TerminalGreen
                RootStatus.DENIED -> stringResource(R.string.status_root_denied) to TerminalRed
                RootStatus.CHECKING -> stringResource(R.string.status_root_checking) to GameAmber
            }
            StatusPill(
                label = rootText,
                dotColor = rootColor,
                onClick = onProbeRoot,
                trailingIcon = Icons.Filled.Refresh,
                modifier = Modifier
                    .weight(1f)
                    .testTag("root_status_pill")
            )
            StatusPill(
                label = stringResource(
                    if (hasOverlayPermission) R.string.status_overlay_ready
                    else R.string.status_overlay_grant
                ),
                dotColor = if (hasOverlayPermission) TerminalGreen else GameAmber,
                onClick = onRequestOverlay,
                modifier = Modifier
                    .weight(1f)
                    .testTag("overlay_status_pill")
            )
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    dotColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector? = null
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = HudSurface,
        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.35f)),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = stringResource(R.string.cd_refresh_root),
                    tint = TextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun ModeTile(
    mode: CosmoMode,
    isSelected: Boolean,
    isExecuting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) mode.accent else HudOutline,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tileBorder"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 1.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tileBorderWidth"
    )
    val containerBg by animateColorAsState(
        targetValue = if (isSelected) mode.accent.copy(alpha = 0.14f) else HudSurface,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tileBg"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerBg,
        border = BorderStroke(borderWidth, borderColor),
        modifier = modifier
            .height(156.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag(mode.testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(mode.accent.copy(alpha = if (isSelected) 0.25f else 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = mode.icon,
                        contentDescription = stringResource(R.string.cd_mode_icon),
                        tint = mode.accent,
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = mode.accent
                    )
                } else if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(mode.accent)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(mode.titleRes),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isSelected) TextPrimary else TextPrimary.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(mode.descRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) mode.accent else TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RedFilterSliderCard(
    alpha: Float,
    onAlphaChange: (Float) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = HudSurface,
        border = BorderStroke(1.dp, SleepCrimson.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.red_filter_intensity_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = SleepCrimson
                )
                Text(
                    text = "${(alpha * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = alpha,
                onValueChange = onAlphaChange,
                valueRange = RedOverlayService.MIN_ALPHA..RedOverlayService.MAX_ALPHA,
                colors = SliderDefaults.colors(
                    thumbColor = SleepCrimson,
                    activeTrackColor = SleepCrimson,
                    inactiveTrackColor = HudOutline
                ),
                modifier = Modifier.testTag("red_filter_slider")
            )
        }
    }
}

@Composable
private fun TelemetryLogCard(
    logs: List<TelemetryLogEntry>,
    accentColor: Color,
    onClear: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = HudSurface,
        border = BorderStroke(1.dp, HudOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Terminal,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.shell_telemetry_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
                if (logs.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.action_clear_log),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onClear)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("clear_log_button")
                    )
                }
            }

            if (logs.isEmpty()) {
                Text(
                    text = stringResource(R.string.shell_telemetry_empty),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    logs.take(6).forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = entry.timestamp,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            Text(
                                text = entry.summary,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (entry.isSuccess) TerminalGreen else TerminalRed,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DistractingTargetsDialog(
    packages: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HudSurfaceElevated,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(
                text = stringResource(R.string.targets_dialog_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.targets_dialog_hint),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("target_package_input")
                    )
                    Button(
                        onClick = {
                            onAdd(input)
                            input = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StudyCyan),
                        modifier = Modifier.testTag("add_target_button")
                    ) {
                        Text(
                            text = stringResource(R.string.targets_dialog_add),
                            color = ObsidianBg
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(packages, key = { it }) { pkg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(HudSurface)
                                .border(1.dp, HudOutline, RoundedCornerShape(10.dp))
                                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = pkg,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onRemove(pkg) }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.cd_remove_package),
                                    tint = TerminalRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.targets_dialog_done),
                    color = StudyCyan
                )
            }
        }
    )
}