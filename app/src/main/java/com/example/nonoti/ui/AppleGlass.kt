package com.example.nonoti.ui

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.nonoti.theme.AppleGlassPalette
import com.example.nonoti.theme.LocalApplePalette
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

val LocalHazeState = compositionLocalOf<HazeState?> { null }

fun <T> appleSpring() = spring<T>(dampingRatio = 0.82f, stiffness = 380f)

@Composable
fun Modifier.appleGlass(
    shape: Shape,
    tint: Color = LocalApplePalette.current.glassSurfaceTint,
    active: Boolean = false,
    blurRadius: Dp = 26.dp,
    noiseFactor: Float = 0.04f,
    elevation: Dp = 10.dp,
    hazeState: HazeState? = LocalHazeState.current,
): Modifier {
    val palette = LocalApplePalette.current
    val resolvedTint = if (active) palette.glassActiveTint else tint
    val borderBrush = Brush.linearGradient(
        0f to (if (active) palette.activeBorderStart else palette.borderStart),
        0.48f to palette.borderMiddle,
        1f to palette.borderEnd,
    )
    val base = this
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = palette.shadowSpot,
            spotColor = palette.shadowSpot,
        )
        .clip(shape)

    val blurred = if (hazeState != null) {
        base.hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = palette.canvas,
                tint = HazeTint(color = resolvedTint),
                blurRadius = blurRadius,
                noiseFactor = noiseFactor,
            ),
        )
    } else {
        base.background(resolvedTint, shape)
    }

    return blurred.border(width = 0.8.dp, brush = borderBrush, shape = shape)
}

@Composable
fun AppleGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    active: Boolean = false,
    elevation: Dp = 8.dp,
    hazeState: HazeState? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.appleGlass(
            shape = shape,
            active = active,
            elevation = elevation,
            hazeState = hazeState,
        ),
        content = content,
    )
}

@Composable
fun AppleGlassPillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val palette = LocalApplePalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = appleSpring(),
        label = "pillScale",
    )

    val bgColor = when {
        !enabled -> palette.glassInnerPillTint
        accent -> palette.controlAccent
        else -> palette.glassSurfaceTint
    }
    val contentColor = when {
        !enabled -> palette.mutedText
        accent -> palette.onControlAccent
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderBrush = Brush.linearGradient(
        0f to (if (accent && enabled) palette.activeBorderStart else palette.borderStart),
        0.5f to palette.borderMiddle,
        1f to palette.borderEnd,
    )

    Row(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = if (accent && enabled) 8.dp else 4.dp,
                shape = CircleShape,
                ambientColor = palette.shadowSpot,
                spotColor = palette.shadowSpot,
            )
            .clip(CircleShape)
            .background(bgColor, CircleShape)
            .border(0.8.dp, borderBrush, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

@Composable
fun AppleGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = appleSpring(),
        label = "iconBtnScale",
    )
    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .appleGlass(shape = CircleShape, elevation = 6.dp, hazeState = null)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun AppleGlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalApplePalette.current
    val trackColor by animateColorAsState(
        targetValue = if (checked) palette.controlAccent else palette.switchOffTrack,
        animationSpec = appleSpring(),
        label = "switchTrack",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 23.dp else 3.dp,
        animationSpec = appleSpring(),
        label = "switchThumb",
    )
    val borderColor = if (checked) {
        palette.activeBorderStart
    } else {
        palette.borderMiddle
    }

    Box(
        modifier = modifier
            .width(52.dp)
            .height(32.dp)
            .clip(CircleShape)
            .background(trackColor, CircleShape)
            .border(0.8.dp, borderColor, CircleShape)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(26.dp)
                .shadow(4.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
                .background(Color.White, CircleShape),
        )
    }
}

@Composable
fun <T> AppleGlassSegmentedControl(
    options: List<T>,
    selectedOption: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalApplePalette.current
    val selectedIndex = options.indexOf(selectedOption).coerceAtLeast(0)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .background(palette.glassInnerPillTint, CircleShape)
            .border(0.6.dp, palette.borderMiddle, CircleShape)
            .padding(4.dp),
    ) {
        val segmentWidth = if (options.isNotEmpty()) maxWidth / options.size else 0.dp
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = appleSpring(),
            label = "segmentIndicator",
        )

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .shadow(4.dp, CircleShape, ambientColor = palette.shadowSpot, spotColor = palette.shadowSpot)
                .clip(CircleShape)
                .background(palette.controlAccent, CircleShape)
                .border(0.6.dp, palette.activeBorderStart, CircleShape),
        )

        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEach { option ->
                val isSelected = option == selectedOption
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) palette.onControlAccent else MaterialTheme.colorScheme.onSurface,
                    animationSpec = appleSpring(),
                    label = "segmentText",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(
                            enabled = !isSelected,
                            role = Role.Tab,
                            onClick = { onSelect(option) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(option),
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

data class AppleNavItem<T>(
    val key: T,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun <T> AppleGlassBottomBar(
    items: List<AppleNavItem<T>>,
    selectedKey: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalApplePalette.current
    val hazeState = LocalHazeState.current
    val selectedIndex = items.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .height(64.dp)
                .appleGlass(
                    shape = CircleShape,
                    tint = palette.glassBarTint,
                    blurRadius = 28.dp,
                    noiseFactor = 0.04f,
                    elevation = 18.dp,
                    hazeState = hazeState,
                )
                .padding(5.dp),
        ) {
            val itemWidth = if (items.isNotEmpty()) maxWidth / items.size else 0.dp
            val indicatorOffset by animateDpAsState(
                targetValue = itemWidth * selectedIndex,
                animationSpec = appleSpring(),
                label = "navPillOffset",
            )

            if (items.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .shadow(
                            elevation = 6.dp,
                            shape = CircleShape,
                            ambientColor = palette.shadowSpot,
                            spotColor = palette.shadowSpot,
                        )
                        .clip(CircleShape)
                        .background(palette.controlAccent, CircleShape)
                        .border(0.75.dp, palette.activeBorderStart, CircleShape),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    val isSelected = item.key == selectedKey
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (pressed) 0.94f else 1f,
                        animationSpec = appleSpring(),
                        label = "navItemScale",
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) palette.onControlAccent else palette.mutedText,
                        animationSpec = appleSpring(),
                        label = "navItemColor",
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .scale(scale)
                            .clip(CircleShape)
                            .semantics { selected = isSelected }
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.Tab,
                                onClick = { onSelect(item.key) },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            color = contentColor,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppleGlassDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable ColumnScope.() -> Unit,
    confirmButton: @Composable RowScope.() -> Unit,
    dismissButton: (@Composable RowScope.() -> Unit)? = null,
) {
    val palette: AppleGlassPalette = LocalApplePalette.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.let { w ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    w.attributes = w.attributes.apply {
                        blurBehindRadius = 48
                    }
                }
                w.setDimAmount(if (palette.isDark) 0.48f else 0.24f)
            }
        }

        val shape = RoundedCornerShape(28.dp)
        val borderBrush = Brush.linearGradient(
            0f to palette.borderStart,
            0.5f to palette.borderMiddle,
            1f to palette.borderEnd,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp)
                .shadow(24.dp, shape, ambientColor = palette.shadowSpot, spotColor = palette.shadowSpot)
                .clip(shape)
                .background(palette.glassDialogTint, shape)
                .border(0.9.dp, borderBrush, shape)
                .padding(24.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Box(Modifier.padding(bottom = 12.dp)) {
                    androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                        title()
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = text,
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dismissButton?.invoke(this)
                    if (dismissButton != null) Spacer(Modifier.width(8.dp))
                    confirmButton()
                }
            }
        }
    }
}
