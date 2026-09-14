package com.vachak.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vachak.ui.theme.VachakColors

/**
 * Shared grade filter bottom sheet (M3). Replaces the old AlertDialog +
 * RadioButton pattern — a sheet is thumb-reachable on tablets and matches
 * the classroom app's bottom-nav ergonomics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeFilterSheet(
    selected: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VachakColors.Surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Filter by grade",
                style = MaterialTheme.typography.titleLarge,
                color = VachakColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Show lessons for one grade, or everything.",
                style = MaterialTheme.typography.bodyMedium,
                color = VachakColors.TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            val options = listOf(null to "All grades") + (1..5).map { it to "Grade $it" }
            options.forEach { (g, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .clickable { onSelect(g) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(
                        selected = selected == g,
                        onClick = { onSelect(g) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = VachakColors.DeepLavender,
                            unselectedColor = VachakColors.TextSecondary
                        )
                    )
                    Text(label, style = MaterialTheme.typography.bodyLarge, color = VachakColors.TextPrimary)
                }
            }
        }
    }
}
