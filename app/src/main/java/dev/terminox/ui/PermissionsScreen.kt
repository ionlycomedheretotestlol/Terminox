package dev.terminox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PermState(val storage: Boolean, val background: Boolean, val notifications: Boolean)

/** Permission list, used as a setup step and inside Settings. */
@Composable
fun PermissionList(state: PermState, onStorage: () -> Unit, onBackground: () -> Unit, onNotifications: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PermCard(Icons.Rounded.Folder, "Phone storage", "Your files at /sdcard inside the terminal.", state.storage, onStorage)
        PermCard(Icons.Rounded.BatteryChargingFull, "Run in background", "Lets terminox-lock keep sessions alive with the screen off.", state.background, onBackground)
        PermCard(Icons.Rounded.Notifications, "Notifications", "Shows what's running (and the lock) in your shade.", state.notifications, onNotifications)
    }
}

@Composable
fun PermissionsScreen(state: PermState, onStorage: () -> Unit, onBackground: () -> Unit, onNotifications: () -> Unit, onDone: () -> Unit) {
    val theme = Themes.current
    Box(Modifier.fillMaxSize()) {
        Aurora(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Unlock the good stuff.", color = Color.White, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text("Tap each one. You can change them later in Settings.", color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp)
            Spacer(Modifier.height(24.dp))
            PermissionList(state, onStorage, onBackground, onNotifications)
            Spacer(Modifier.height(28.dp))
            val all = state.storage && state.background && state.notifications
            Text(if (all) "Let's cook" else "Continue", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                modifier = Modifier.align(Alignment.End).clip(RoundedCornerShape(50)).background(theme.a)
                    .clickable(onClick = onDone).padding(horizontal = 28.dp, vertical = 14.dp))
        }
    }
}

@Composable
private fun PermCard(icon: ImageVector, title: String, sub: String, granted: Boolean, onClick: () -> Unit) {
    val theme = Themes.current
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), alpha = 0.5f).clickable(enabled = !granted, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = theme.a, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
        }
        Spacer(Modifier.width(10.dp))
        if (granted) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(theme.b), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, "Granted", tint = Color.Black, modifier = Modifier.size(18.dp))
            }
        } else {
            Text("Allow", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 14.dp, vertical = 7.dp))
        }
    }
}
