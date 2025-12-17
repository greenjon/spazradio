package llm.slop.spazradio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import llm.slop.spazradio.ui.theme.NeonGreen

@Composable
fun FooterToolbar(
    onRadioClick: () -> Unit,
    onArchivesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FooterButton(
            text = "Radio",
            onClick = onRadioClick,
            modifier = Modifier.weight(1f)
        )
        FooterButton(
            text = "Archives",
            onClick = onArchivesClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun FooterButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .background(Color(0x80000000), RoundedCornerShape(16.dp))
            .border(2.dp, NeonGreen, RoundedCornerShape(16.dp))
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = NeonGreen,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
