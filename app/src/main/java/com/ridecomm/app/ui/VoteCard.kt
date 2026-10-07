package com.ridecomm.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ridecomm.app.R
import com.ridecomm.app.vote.QuickMessage
import com.ridecomm.app.vote.Vote
import com.ridecomm.app.vote.VoteKind
import com.ridecomm.app.vote.VoteManager
import com.ridecomm.app.vote.VoteState

/** Active vote with big Yes/No buttons; otherwise the last result; otherwise quick actions. */
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
    GlassCard(tint = Palette.Amber, fillAlpha = 0.14f, spacing = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(vote.kind.icon, 30.dp, Palette.Amber)
            Spacer(Modifier.width(12.dp))
            Text("${vote.starterName} ${vote.kind.asking}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        val waiting = (vote.riders - vote.ballots.size).coerceAtLeast(0)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusPill("${vote.yes} yes", Palette.Go)
            StatusPill("${vote.no} no", Palette.Stop)
            if (waiting > 0) StatusPill("$waiting waiting", Palette.TextTertiary)
        }
        if (myVote == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("Yes", R.drawable.ms_thumb_up, Modifier.weight(1f), brush = Palette.GoGradient, height = 66.dp) {
                    VoteManager.cast(yes = true)
                }
                PrimaryButton("No", R.drawable.ms_thumb_down, Modifier.weight(1f), brush = Palette.StopGradient, height = 66.dp) {
                    VoteManager.cast(yes = false)
                }
            }
        } else {
            Text("You voted ${if (myVote) "yes" else "no"}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Result(vote: Vote) {
    val approved = vote.approved == true
    GlassCard(tint = if (approved) Palette.Go else Color.White, fillAlpha = if (approved) 0.14f else 0.09f) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(if (approved) R.drawable.ms_check_circle else R.drawable.ms_close, 28.dp, if (approved) Palette.Go else Palette.TextSecondary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "${vote.kind.stopName} ${if (approved) "approved" else "not approved"}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("${vote.yes} yes · ${vote.no} no", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun QuickActions() {
    GlassCard(spacing = 12.dp) {
        SectionLabel("Ask the group")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VoteKind.entries.forEach { kind ->
                GlassTile(kind.icon, kind.label, Modifier.weight(1f), accent = Palette.Amber) { VoteManager.startVote(kind) }
            }
        }
        SectionLabel("Quick message")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickMessage.entries.forEach { message ->
                GlassTile(message.icon, message.label, Modifier.weight(1f), accent = Palette.Cyan) { VoteManager.sendQuick(message) }
            }
        }
    }
}
