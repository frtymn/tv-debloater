package com.frtymn.tvdebloater.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frtymn.tvdebloater.R
import com.frtymn.tvdebloater.adb.AdbErrorKind
import com.frtymn.tvdebloater.core.SafetyLevel
import java.util.Locale
import kotlin.math.abs

fun formatKb(kb: Long): String = when {
    abs(kb) >= 1024L * 1024 -> String.format(Locale.US, "%.2f GB", kb / 1024.0 / 1024.0)
    abs(kb) >= 1024 -> String.format(Locale.US, "%.0f MB", kb / 1024.0)
    else -> "$kb KB"
}

fun formatDiff(kb: Long): String = (if (kb >= 0) "+" else "-") + formatKb(abs(kb))

@Composable
fun Badge(text: String, color: Color) {
    Text(
        text, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
        modifier = Modifier.background(color, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun LevelBadge(level: SafetyLevel, unknown: Boolean) {
    when {
        unknown && level == SafetyLevel.CAUTION -> Badge(stringResource(R.string.level_unknown), UnknownColor)
        level == SafetyLevel.SAFE -> Badge(stringResource(R.string.level_safe), SafeColor)
        level == SafetyLevel.CAUTION -> Badge(stringResource(R.string.level_caution), CautionColor)
        else -> Badge(stringResource(R.string.level_protected), ProtectedColor)
    }
}

@Composable
fun errorText(kind: AdbErrorKind): String = stringResource(
    when (kind) {
        AdbErrorKind.TIMEOUT -> R.string.err_timeout
        AdbErrorKind.AUTH_TIMEOUT -> R.string.err_auth_timeout
        AdbErrorKind.REFUSED -> R.string.err_refused
        AdbErrorKind.UNREACHABLE -> R.string.err_unreachable
        AdbErrorKind.UNAUTHORIZED -> R.string.err_unauthorized
        AdbErrorKind.DISCONNECTED -> R.string.err_disconnected
        AdbErrorKind.NOT_CONNECTED -> R.string.err_not_connected
        AdbErrorKind.OTHER -> R.string.err_other
    },
)

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
}
