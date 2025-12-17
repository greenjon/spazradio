package llm.slop.spazradio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import llm.slop.spazradio.ui.theme.NeonGreen

@Composable
fun FooterToolbar(
    onRadioClick: () -> Unit,
    onArchivesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0x80000000), RoundedCornerShape(16.dp))
            .border(3.dp, NeonGreen, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onRadioClick) {
                Text(
                    text = "Radio",
                    color = NeonGreen,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            TextButton(onClick = onArchivesClick) {
                Text(
                    text = "Archives",
                    color = NeonGreen,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
