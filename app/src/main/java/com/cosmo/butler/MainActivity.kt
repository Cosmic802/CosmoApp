package com.cosmo.butler

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataOutputStream

enum class CosmoMode(val title: String, val desc: String, val color: Color, val icon: ImageVector) {
    STUDY("STUDY MODE", "DND + Focus Lock", StudyCyan, Icons.Default.MenuBook),
    GAME("GAME MODE", "120Hz + RAM Boost", GameAmber, Icons.Default.SportsEsports),
    SLEEP("SLEEP MODE", "Red Shield + DND", SleepCrimson, Icons.Default.Bedtime),
    NORMAL("NORMAL MODE", "Restore Defaults", NormalEmerald, Icons.Default.Shield)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CosmoTheme {
                CosmoDashboard()
            }
        }
    }
}

@Composable
fun CosmoDashboard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentMode by remember { mutableStateOf(CosmoMode.NORMAL) }
    var isRootOnline by remember { mutableStateOf<Boolean?>(null) }
    var hasOverlayPerm by remember { mutableStateOf(Settings.canDrawOverlays(context)) }


