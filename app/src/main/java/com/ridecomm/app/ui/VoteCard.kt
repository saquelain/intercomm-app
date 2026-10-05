package com.ridecomm.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridecomm.app.vote.QuickMessage
import com.ridecomm.app.vote.Vote
import com.ridecomm.app.vote.VoteKind
import com.ridecomm.app.vote.VoteManager
import com.ridecomm.app.vote.VoteState

private val Yellow = Color(0xFFFFC82E)

/** Active vote with big Yes/No buttons; otherwise the last result; otherwise quick-action buttons. */
@Composable
fun VoteCard(vote: VoteState) {
    val active = vote.active
    val result = vote.lastResult
    when {
        active != null -> ActiveVote(active, vote.myVote)
        result != null -> Result(result)
        else -> QuickActions()
    }
}

@Composable
private fun ActiveVote(vote: Vote, myVote: Boolean?) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Yellow.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("${vote.kind.emoji} ${vote.starterName} ${vote.kind.asking}", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        val waiting = (vote.riders - vote.ballots.size).coerceAtLeast(0)
        Text(
            "👍 ${vote.yes}   👎 ${vote.no}" + if (waiting > 0) "   ⏳ $waiting waiting" else "",
            fontSize = 16.sp,
        )
        if (myVote == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VoteButton("👍 YES", Talking) { VoteManager.cast(yes = true) }
                VoteButton("👎 NO", Danger) { VoteManager.cast(yes = false) }
            }
        } else {
            Text("You voted ${if (myVote) "yes" else "no"}", fontSize = 15.sp, color = Muted)
        }
    }
}

@Composable
private fun RowScope.VoteButton(text: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.Black),
    ) { Text(text, fontSize = 22.sp, fontWeight = FontWeight.Black) }
}

@Composable
private fun Result(vote: Vote) {
    val approved = vote.approved == true
    Text(
        "${vote.kind.emoji} ${vote.kind.stopName} ${if (approved) "approved" else "not approved"} · 👍 ${vote.yes} 👎 ${vote.no}",
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        color = if (approved) Talking else Muted,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(14.dp),
    )
}

@Composable
private fun QuickActions() {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        VoteKind.entries.forEach { kind ->
            QuickButton(kind.emoji, "${kind.label}?") { VoteManager.startVote(kind) }
        }
        QuickMessage.entries.forEach { message ->
            QuickButton(message.emoji, message.label) { VoteManager.sendQuick(message) }
        }
    }
}

@Composable
private fun RowScope.QuickButton(emoji: String, label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(68.dp),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(2.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 22.sp)
            Text(label, fontSize = 11.sp, maxLines = 1)
        }
    }
}
