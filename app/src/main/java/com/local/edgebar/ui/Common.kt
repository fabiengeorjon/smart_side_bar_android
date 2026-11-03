package com.local.edgebar.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.edgebar.data.AppCatalog
import com.local.edgebar.service.EdgeBarAccessibilityService
import com.local.edgebar.ui.theme.DotGlyph
import com.local.edgebar.ui.theme.DotoLabel
import com.local.edgebar.ui.theme.EdgeColors
import com.local.edgebar.ui.theme.Glyphs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ServiceStatus {
    /** Whether the user has enabled Edge Bar in Settings > Accessibility. */
    fun isEnabled(context: Context): Boolean {
        val am = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val me = ComponentName(context, EdgeBarAccessibilityService::class.java)
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            val si = it.resolveInfo.serviceInfo
            si.packageName == me.packageName && si.name == me.className
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = DotoLabel.copy(color = EdgeColors.Secondary), modifier = modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
fun EdgeCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).border(1.dp, EdgeColors.Border, shape).padding(horizontal = 14.dp, vertical = 6.dp),
        content = content,
    )
}

@Composable
fun BodyText(text: String, color: Color = EdgeColors.Secondary, modifier: Modifier = Modifier) {
    Text(text, color = color, fontSize = 14.sp, lineHeight = 20.sp, modifier = modifier)
}

@Composable
fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = EdgeColors.White, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = EdgeColors.Secondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = EdgeColors.Black,
                checkedTrackColor = EdgeColors.White,
                uncheckedThumbColor = EdgeColors.Secondary,
                uncheckedTrackColor = EdgeColors.Black,
                uncheckedBorderColor = EdgeColors.Border,
            ),
        )
    }
}

@Composable
fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = EdgeColors.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(valueText, style = DotoLabel)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = EdgeColors.White,
                activeTrackColor = EdgeColors.White,
                inactiveTrackColor = EdgeColors.Border,
            ),
        )
    }
}

@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val isSel = value == selected
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (isSel) EdgeColors.White else EdgeColors.Black)
                    .border(1.dp, if (isSel) EdgeColors.White else EdgeColors.Border, shape)
                    .selectable(selected = isSel, role = Role.RadioButton) { onSelect(value) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label.uppercase(), style = DotoLabel.copy(color = if (isSel) EdgeColors.Black else EdgeColors.White))
            }
        }
    }
}

@Composable
fun EdgeButton(text: String, modifier: Modifier = Modifier, primary: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (primary) EdgeColors.White else EdgeColors.Black)
            .border(1.dp, if (primary) EdgeColors.White else EdgeColors.Border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (primary) EdgeColors.Black else EdgeColors.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** Monochrome app icon, loaded off the main thread. */
@Composable
fun AppIconImage(packageName: String, size: Dp) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.toPx() }.roundToInt()
    val icon by produceState<ImageBitmap?>(null, packageName, px) {
        value = withContext(Dispatchers.IO) {
            AppCatalog.loadIcon(context.packageManager, packageName, px)?.bitmap?.asImageBitmap()
        }
    }
    Box(Modifier.size(size + 8.dp).clip(RoundedCornerShape(10.dp)).background(EdgeColors.Surface), contentAlignment = Alignment.Center) {
        val bmp = icon
        if (bmp != null) Image(bmp, contentDescription = null, modifier = Modifier.size(size))
        else DotGlyph(Glyphs.APP, size / 2, EdgeColors.Secondary)
    }
}

@Composable
fun SmallTextButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(text, style = DotoLabel.copy(color = if (enabled) EdgeColors.White else EdgeColors.Border, fontSize = 13.sp))
    }
}

@Composable
fun Gap(h: Dp = 12.dp) = Spacer(Modifier.height(h))
