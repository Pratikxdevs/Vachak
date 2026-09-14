package com.vachak.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vachak.ui.theme.VachakColors
import com.vachak.ui.theme.VachakDepth
import com.vachak.ui.theme.VachakLayer
import com.vachak.ui.theme.cardShadow
import com.vachak.ui.theme.color
import com.vachak.ui.theme.raisedShadow

/**
 * Expressive primitives for the UI rework. Every component follows the same
 * contract: hierarchy (one thing pops, the rest mutes), context (subtitle or
 * detail text — never a bare title), and depth ([VachakDepth]).
 */

/** Layered section card: title + muted subtitle context + content. */
@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = VachakLayer.Card.color(),
        border = BorderStroke(1.dp, VachakColors.Border),
        modifier = modifier.fillMaxWidth().cardShadow()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = VachakColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = VachakColors.TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (actionLabel != null && onAction != null) {
                    androidx.compose.material3.TextButton(onClick = onAction) { Text(actionLabel) }
                }
            }
            content()
        }
    }
}

/** Selectable option card: selected state is ELEVATED (shadow + tint + border). */
@Composable
fun SelectableCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) VachakColors.Lavender100 else VachakLayer.Card.color(),
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) VachakColors.DeepLavender else VachakColors.Border
        ),
        modifier = modifier.fillMaxWidth().then(
            if (selected) Modifier.raisedShadow() else Modifier.cardShadow()
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

/** Recessed well: darker surface + hairline border, zero shadow. For tables,
 *  progress beds, logs — content that sits INTO the card, not above it. */
@Composable
fun InsetWell(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = VachakLayer.Well.color(),
        border = BorderStroke(VachakDepth.WellBorder, VachakColors.OutlineVariant),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) { content() }
    }
}

/** Color-coded status WITH a text cause — color is never the only signal. */
@Composable
fun StatusRow(
    dot: Color,
    title: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = dot,
            modifier = Modifier.size(10.dp)
        ) {}
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = VachakColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = VachakColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Directional empty state: what + why + one next action. Never bare "no data". */
@Composable
fun GuidedEmpty(
    icon: ImageVector,
    title: String,
    why: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = VachakLayer.Section.color(),
        border = BorderStroke(1.dp, VachakColors.Border),
        modifier = modifier.fillMaxWidth().cardShadow(RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = VachakColors.Lavender100,
                modifier = Modifier.size(52.dp)
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(24.dp))
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = VachakColors.TextPrimary
            )
            Text(
                why,
                style = MaterialTheme.typography.bodyMedium,
                color = VachakColors.TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VachakColors.PrimaryDark,
                    contentColor = Color.White
                ),
                modifier = Modifier.height(48.dp).raisedShadow(RoundedCornerShape(50))
            ) { Text(actionLabel) }
        }
    }
}

/** Named-step loading: the exact step name buys patience; real fraction shows
 *  momentum. Pass null fraction only when genuinely indeterminate. */
@Composable
fun SteppedLoading(
    steps: List<String>,
    currentStep: Int,
    fraction: Float? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = VachakLayer.Section.color(),
        border = BorderStroke(1.dp, VachakColors.Border),
        modifier = modifier.fillMaxWidth().cardShadow(RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val label = steps.getOrNull(currentStep.coerceIn(steps.indices)) ?: "Loading…"
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = VachakColors.TextPrimary
            )
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                    color = VachakColors.Lavender600,
                    trackColor = VachakColors.Lavender200
                )
                Text(
                    "Step ${currentStep + 1} of ${steps.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = VachakColors.TextSecondary
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                    color = VachakColors.Lavender600,
                    trackColor = VachakColors.Lavender200
                )
            }
        }
    }
}

/** Breadcrumb trail: where am I, how did I get here. */
@Composable
fun BreadcrumbTrail(
    trail: List<String>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        trail.forEachIndexed { i, crumb ->
            if (i > 0) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    null,
                    tint = VachakColors.TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                crumb,
                style = MaterialTheme.typography.labelMedium,
                color = if (i == trail.lastIndex) VachakColors.DeepLavender else VachakColors.TextSecondary,
                fontWeight = if (i == trail.lastIndex) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
