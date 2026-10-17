package cn.zhangyi.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import cn.zhangyi.domain.Preferences

@Composable
fun ZhangYiTheme(p: Preferences, content: @Composable () -> Unit) {
    val dark = when (p.theme) { "LIGHT" -> false; "DARK" -> true; else -> isSystemInDarkTheme() }
    val context = LocalContext.current
    val colors = if (p.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme(primary = Color(0xFFA1D2BC), onPrimary = Color(0xFF083829), primaryContainer = Color(0xFF24503E), onPrimaryContainer = Color(0xFFBCEFD6), background = Color(0xFF101512), surface = Color(0xFF101512))
    else lightColorScheme(primary = Color(0xFF326A53), onPrimary = Color.White, primaryContainer = Color(0xFFB4EBD0), onPrimaryContainer = Color(0xFF073824), background = Color(0xFFF7FAF6), surface = Color(0xFFF7FAF6))
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
        }
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
