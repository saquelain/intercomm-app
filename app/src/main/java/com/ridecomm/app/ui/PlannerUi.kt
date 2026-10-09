package com.ridecomm.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ridecomm.app.Prefs
import com.ridecomm.app.R
import com.ridecomm.app.group.GroupTracker
import com.ridecomm.app.group.Place
import com.ridecomm.app.plan.PlanCodec
import com.ridecomm.app.plan.PlanPlace
import com.ridecomm.app.plan.RidePlan
import com.ridecomm.app.plan.RidePlans
import com.ridecomm.app.ride.RideCode
import java.util.Calendar

private fun PlanPlace.navigate(context: android.content.Context) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GroupTracker.directionsLink(lat, lon)))) }
}

private fun Place.toPlan() = PlanPlace(name.take(PlanCodec.MAX_TEXT), lat, lon)

private fun sharePlan(context: android.content.Context, plan: RidePlan) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, RidePlans.shareText(context, plan))
    context.startActivity(Intent.createChooser(send, "Share ride plan"))
}

/** Home screen: the rides planned ahead (made here or from an invite), and a way to plan one. */
@Composable
fun PlannerCard(plans: List<RidePlan>, joinEnabled: Boolean, onPlan: () -> Unit, onJoin: (String) -> Unit) {
    val context = LocalContext.current
    if (plans.isEmpty()) {
        if (LocalLook.current != UiLook.CLASSIC) {
            ActionRow(R.drawable.ms_schedule, "Plan a ride", "Time, meeting point and stops, shared in the invite", iconTint = Palette.Violet, onClick = onPlan)
        } else {
            GlassButton("Plan a ride", R.drawable.ms_schedule, Modifier.fillMaxWidth(), height = 56.dp, onClick = onPlan)
        }
        return
    }
    GlassCard(spacing = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Upcoming rides", Modifier.weight(1f))
            Text("+ Plan", style = MaterialTheme.typography.labelLarge, color = Palette.Accent, modifier = Modifier.clickable(onClick = onPlan).padding(6.dp))
        }
        plans.forEach { plan ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(plan.label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${RidePlans.whenText(plan.atMs)} · meet at ${plan.meet.name}", style = MaterialTheme.typography.bodyMedium)
                if (plan.stops.isNotEmpty() || plan.dest != null) {
                    val route = plan.stops.map { it.name } + listOfNotNull(plan.dest?.name)
                    Text("Via ${route.joinToString(" → ")}", style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                val join = @Composable { m: Modifier ->
                    PrimaryButton("Join", R.drawable.ms_two_wheeler, m, height = 48.dp, enabled = joinEnabled) { onJoin(plan.code) }
                }
                val tools = @Composable {
                    GlassIconButton(R.drawable.ms_share, "Share plan", size = 48.dp, iconSize = 20.dp) { sharePlan(context, plan) }
                    GlassIconButton(R.drawable.ms_schedule, "Add to calendar", size = 48.dp, iconSize = 20.dp) {
                        runCatching { context.startActivity(RidePlans.calendarIntent(context, plan)) }
                    }
                    GlassIconButton(R.drawable.ms_close, "Delete plan", size = 48.dp, iconSize = 20.dp) { RidePlans.delete(context, plan.code) }
                }
                // Join beside the small buttons, or above them when there isn't room (small phone, large text).
                BoxWithConstraints {
                    if (maxWidth >= 290.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            join(Modifier.weight(1f))
                            tools()
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            join(Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { tools() }
                        }
                    }
                }
            }
        }
    }
}

/** One place in the plan editor: tap to choose or change it; ✕ removes it. */
@Composable
private fun PlaceLine(label: String, place: PlanPlace?, empty: String, onChoose: () -> Unit, onRemove: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(16.dp), fillAlpha = 0.06f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onChoose)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(R.drawable.ms_location_on, 22.dp, if (place != null) Palette.Go else Palette.TextTertiary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
            Text(place?.name ?: empty, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (place != null) Palette.TextPrimary else Palette.TextTertiary)
        }
        if (onRemove != null) GlassIconButton(R.drawable.ms_close, "Remove", size = 36.dp, iconSize = 16.dp, onClick = onRemove)
    }
}

/** Plan a ride: title, day and time, meeting point, stops and where it ends. Saved, then shared. */
@Composable
fun PlanDialog(onCancel: () -> Unit, onSaved: (RidePlan) -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    // Tomorrow at 6 am: a typical start for a group ride.
    var at by remember {
        mutableLongStateOf(Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis)
    }
    var meet by remember { mutableStateOf<PlanPlace?>(null) }
    var stops by remember { mutableStateOf(listOf<PlanPlace>()) }
    var dest by remember { mutableStateOf<PlanPlace?>(null) }
    // Which place the search is for: "meet", "stop" or "dest".
    var picking by remember { mutableStateOf<String?>(null) }

    GlassDialog(onDismiss = onCancel) {
        Text("Plan a ride", style = MaterialTheme.typography.headlineMedium)
        Text("Everyone you share it with gets the time, meeting point and stops, and a reminder before it starts.", style = MaterialTheme.typography.bodyMedium)
        GlassTextField(
            value = title,
            onValueChange = { title = it.take(PlanCodec.MAX_TEXT) },
            label = "Name (optional)",
            placeholder = "e.g. Sunday Lonavala ride",
            textStyle = MaterialTheme.typography.bodyLarge,
        )
        SectionLabel("When")
        Text(RidePlans.whenText(at), style = MaterialTheme.typography.titleMedium)
        ButtonRow(2) {
            GlassButton("Change day", R.drawable.ms_schedule, Modifier.share(), height = 48.dp) {
                val c = Calendar.getInstance().apply { timeInMillis = at }
                DatePickerDialog(context, { _, y, m, d ->
                    at = Calendar.getInstance().apply { timeInMillis = at; set(y, m, d) }.timeInMillis
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).apply {
                    datePicker.minDate = System.currentTimeMillis() - 60_000
                }.show()
            }
            GlassButton("Change time", R.drawable.ms_schedule, Modifier.share(), height = 48.dp) {
                val c = Calendar.getInstance().apply { timeInMillis = at }
                TimePickerDialog(context, { _, h, min ->
                    at = Calendar.getInstance().apply { timeInMillis = at; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min) }.timeInMillis
                }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), DateFormat.is24HourFormat(context)).show()
            }
        }
        SectionLabel("Where")
        PlaceLine("Meeting point", meet, "Choose where to meet", onChoose = { picking = "meet" })
        stops.forEachIndexed { i, s ->
            PlaceLine("Stop ${i + 1}", s, "", onChoose = {}, onRemove = { stops = stops.filterIndexed { j, _ -> j != i } })
        }
        if (stops.size < PlanCodec.MAX_STOPS) {
            GlassButton("Add a stop", R.drawable.ms_add, Modifier.fillMaxWidth(), height = 48.dp) { picking = "stop" }
        }
        PlaceLine("Riding to (optional)", dest, "Choose the destination", onChoose = { picking = "dest" }, onRemove = dest?.let { { dest = null } })
        val ready = meet != null && at > System.currentTimeMillis()
        if (at <= System.currentTimeMillis()) Text("Pick a time in the future.", style = MaterialTheme.typography.bodyMedium, color = Palette.Amber)
        ButtonRow(2) {
            GlassButton("Cancel", modifier = Modifier.share(0.8f), onClick = onCancel)
            PrimaryButton("Save & share", R.drawable.ms_share, Modifier.share(1.2f), height = 56.dp, enabled = ready) {
                val plan = RidePlan(
                    code = RideCode.generate(),
                    title = title.trim(),
                    atMs = at,
                    meet = meet ?: return@PrimaryButton,
                    stops = stops,
                    dest = dest,
                    by = Prefs.riderName(context),
                )
                RidePlans.save(context, plan)
                sharePlan(context, plan)
                onSaved(plan)
            }
        }
    }

    picking?.let { which ->
        DestinationDialog(
            onCancel = { picking = null },
            onPick = { place ->
                when (which) {
                    "meet" -> meet = place.toPlan()
                    "stop" -> stops = (stops + place.toPlan()).take(PlanCodec.MAX_STOPS)
                    else -> dest = place.toPlan()
                }
                picking = null
            },
            title = when (which) {
                "meet" -> "Where do we meet?"
                "stop" -> "Add a stop"
                else -> "Where does the ride go?"
            },
            description = "Type a place, or paste coordinates or a Google Maps link.",
            myLocation = which == "meet",
        )
    }
}

/** In a planned ride: the plan, each place one tap from directions; the end can become the group's destination. */
@Composable
fun PlanRideCard(plan: RidePlan, onSetDestination: ((PlanPlace) -> Unit)?) {
    val context = LocalContext.current
    GlassCard(tint = Palette.Violet, fillAlpha = 0.10f, spacing = 10.dp) {
        SectionLabel("Today's plan")
        Text(plan.label, style = MaterialTheme.typography.titleMedium)
        val places = listOf("Meet" to plan.meet) + plan.stops.mapIndexed { i, s -> "Stop ${i + 1}" to s } + listOfNotNull(plan.dest?.let { "Riding to" to it })
        places.forEach { (label, place) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { place.navigate(context) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
                    Text(place.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Ico(R.drawable.ms_navigation, 22.dp, Palette.Accent, "Navigate")
            }
        }
        val dest = plan.dest
        if (dest != null && onSetDestination != null) {
            GlassButton("Set as the group's destination", R.drawable.ms_sports_score, Modifier.fillMaxWidth(), height = 48.dp, tint = Color.White) { onSetDestination(dest) }
        }
    }
}
