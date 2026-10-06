// Calculator host for OriginSU's Compose FloatingBottomBar (Apache 2.0):
// calculator tabs + Material3 theme + Java-friendly selection state +
// bridge that installs the bar into a ComposeView over the View hierarchy.

package com.android.calculator2.ui.navbar

import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.calculator2.R
import com.android.calculator2.ui.component.FloatingBottomBar
import com.android.calculator2.ui.component.FloatingBottomBarItem
import top.yukonga.miuix.kmp.blur.Backdrop

data class NavTab(
    @DrawableRes val iconRes: Int,
    @StringRes val labelRes: Int,
)

fun interface NavSelectionListener {
    fun onTabSelected(index: Int)
}

/** Selection state shared between Java activities and the Compose bar. */
class GlassNavState(initial: Int) {
    var selectedIndex by mutableIntStateOf(initial)
        private set
    var listener: NavSelectionListener? = null

    /** External sync (e.g. MotionLayout transitions); never notifies the listener. */
    fun select(index: Int) {
        selectedIndex = index
    }

    internal fun emit(index: Int) {
        selectedIndex = index
        listener?.onTabSelected(index)
    }
}

private val CalculatorLightScheme = lightColorScheme(
    primary = Color(0xFF1E6258),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB6E1D8),
    onPrimaryContainer = Color(0xFF00201B),
    surface = Color(0xFFFBFDFB),
    onSurface = Color(0xFF191C1C),
    surfaceContainer = Color(0xFFF3EDE7),
    surfaceContainerHigh = Color(0xFFECE6E1),
    surfaceVariant = Color(0xFFDBE5E1),
    onSurfaceVariant = Color(0xFF3F4947),
)

private val CalculatorDarkScheme = darkColorScheme(
    primary = Color(0xFF7ED4C5),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFF9CF1E3),
    surface = Color(0xFF131313),
    onSurface = Color(0xFFE4E2E1),
    surfaceContainer = Color(0xFF1F1B16),
    surfaceContainerHigh = Color(0xFF2A2520),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C6),
)

@Composable
private fun CalculatorGlassTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) CalculatorDarkScheme else CalculatorLightScheme,
        content = content,
    )
}

@Composable
private fun CalculatorNavBar(
    state: GlassNavState,
    tabs: List<NavTab>,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    CalculatorGlassTheme {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            FloatingBottomBar(
                selectedIndex = state.selectedIndex,
                onSelected = { state.emit(it) },
                tabsCount = tabs.size,
                backdrop = backdrop,
                isBlurEnabled = true,
            ) { activateTab ->
                tabs.forEachIndexed { index, tab ->
                    FloatingBottomBarItem(
                        selected = index == state.selectedIndex,
                        onClick = { activateTab(index) },
                        modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                    ) {
                        val contentColor = LocalContentColor.current
                        Icon(
                            painterResource(tab.iconRes),
                            stringResource(tab.labelRes),
                            tint = contentColor,
                        )
                        Text(
                            text = stringResource(tab.labelRes),
                            color = contentColor,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible,
                        )
                    }
                }
            }
        }
    }
}

object GlassNavBridge {
    @JvmStatic
    fun calculatorTabs(): List<NavTab> = listOf(
        NavTab(R.drawable.ic_nav_calculator, R.string.nav_calculator),
        NavTab(R.drawable.ic_nav_history, R.string.nav_history),
        NavTab(R.drawable.ic_nav_converter, R.string.nav_converter),
        NavTab(R.drawable.ic_nav_settings, R.string.nav_settings),
    )

    /**
     * Installs the exact OriginSU floating bar into [host]. Returns the
     * shared selection state: call [GlassNavState.select] to sync external
     * navigation; taps arrive through [listener].
     */
    @JvmStatic
    fun install(
        host: ComposeView,
        tabs: List<NavTab>,
        initial: Int,
        listener: NavSelectionListener,
    ): GlassNavState {
        val state = GlassNavState(initial)
        state.listener = listener
        val capture = SnapshotCapture(host)
        val backdrop = SnapshotBackdrop()
        capture.onFrame = { frame ->
            backdrop.frame = frame
            host.invalidate()
        }
        host.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = capture.attach()
            override fun onViewDetachedFromWindow(v: View) {
                capture.detach()
                host.removeOnAttachStateChangeListener(this)
            }
        })
        if (host.isAttachedToWindow) {
            capture.attach()
        }
        host.setContent {
            CalculatorNavBar(state, tabs, backdrop)
        }
        return state
    }
}
