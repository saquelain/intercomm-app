package com.ridecomm.app.ui

import android.app.DatePickerDialog
import android.content.DialogInterface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ridecomm.app.R
import com.ridecomm.app.garage.Bike
import com.ridecomm.app.garage.Garage
import com.ridecomm.app.garage.GarageLogic
import com.ridecomm.app.garage.GarageNote
import com.ridecomm.app.garage.MyGarage
import com.ridecomm.app.garage.ServiceItem
import com.ridecomm.app.garage.Urgency
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

private fun kmText(km: Double) = NumberFormat.getIntegerInstance(Locale.US).format(km.toLong()) + " km"

@Composable
private fun Urgency.color(): Color = when (this) {
    Urgency.DUE -> Palette.Stop
    Urgency.SOON -> Palette.Amber
    Urgency.OK -> Palette.Go
}

@Composable
private fun NoteLine(note: GarageNote) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(note.urgency.color()))
        Spacer(Modifier.width(10.dp))
        Text(note.text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** On the home screen: the bike I ride, its odometer and what needs doing soon. */
@Composable
fun GarageCard(g: Garage, nowMs: Long, onOpen: () -> Unit) {
    val bike = g.current
    if (bike == null) {
        if (LocalLook.current != UiLook.CLASSIC) {
            ActionRow(R.drawable.ms_build, "My garage", "Add your bike for oil change and insurance reminders", iconTint = Palette.Cyan, onClick = onOpen)
        } else {
            GlassButton("My garage: add your bike", R.drawable.ms_build, Modifier.fillMaxWidth(), height = 56.dp, onClick = onOpen)
        }
        return
    }
    val alerts = GarageLogic.alerts(g, nowMs)
    GlassCard(Modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onOpen), spacing = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("My garage", Modifier.weight(1f))
            Text("Open", style = MaterialTheme.typography.labelLarge, color = Palette.Accent)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(R.drawable.ms_two_wheeler, 28.dp, Palette.Cyan)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                FitText(bike.name, MaterialTheme.typography.titleLarge)
                FitText(listOf(kmText(bike.odometer), bike.reg).filter { it.isNotBlank() }.joinToString(" · "), MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
            }
        }
        if (alerts.isEmpty()) {
            NoteLine(GarageNote("All good" + (GarageLogic.next(bike, nowMs)?.let { ": next, ${it.text.replaceFirstChar { c -> c.lowercase() }}" } ?: ""), Urgency.OK))
        } else {
            alerts.take(3).forEach { NoteLine(it) }
        }
    }
}

/** The full garage page. */
@Composable
fun GarageScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { MyGarage.load(context) }
    val g by MyGarage.state.collectAsState()
    FullScreen(onClose) {
        GarageContent(
            g,
            System.currentTimeMillis(),
            onClose = onClose,
            onAddBike = { name, reg, odo -> MyGarage.addBike(context, name, reg, odo) },
            onUpdate = { MyGarage.updateBike(context, it) },
            onDelete = { MyGarage.deleteBike(context, it) },
            onCurrent = { MyGarage.setCurrent(context, it) },
            onLicence = { MyGarage.setLicence(context, it) },
        )
    }
}

/** Opens the phone's date picker; "Clear" removes the date. */
private fun pickDate(context: android.content.Context, currentMs: Long, onPick: (Long) -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = if (currentMs > 0) currentMs else System.currentTimeMillis() }
    DatePickerDialog(context, { _, y, m, d ->
        onPick(Calendar.getInstance().apply { clear(); set(y, m, d, 12, 0) }.timeInMillis)
    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).apply {
        if (currentMs > 0) setButton(DialogInterface.BUTTON_NEUTRAL, "Clear") { _, _ -> onPick(0) }
    }.show()
}

/** The page for a given garage (screenshots render it straight). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GarageContent(
    g: Garage,
    nowMs: Long,
    onClose: () -> Unit,
    onAddBike: (String, String, Double) -> Unit,
    onUpdate: (Bike) -> Unit,
    onDelete: (String) -> Unit,
    onCurrent: (String) -> Unit,
    onLicence: (Long) -> Unit,
) {
    val context = LocalContext.current
    var addingBike by remember { mutableStateOf(false) }
    var editingBike by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ServiceItem?>(null) }
    var addingItem by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val bike = g.current
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageTopBar("My garage", "Kept on this phone", onClose)
        if (g.bikes.size > 1 || bike != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                g.bikes.forEach { b ->
                    val sel = b.id == bike?.id
                    Row(
                        Modifier.clip(RoundedCornerShape(16.dp))
                            .glass(RoundedCornerShape(16.dp), tint = if (sel) Palette.CyanBright else Color.White, fillAlpha = if (sel) 0.22f else 0.07f)
                            .clickable { onCurrent(b.id) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Ico(R.drawable.ms_two_wheeler, 18.dp, if (sel) Palette.OnSurface else Palette.TextSecondary)
                        Spacer(Modifier.width(6.dp))
                        Text(b.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp)).glass(RoundedCornerShape(16.dp), fillAlpha = 0.05f).clickable { addingBike = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ico(R.drawable.ms_add, 18.dp, Palette.Accent)
                    Spacer(Modifier.width(6.dp))
                    Text("Add bike", style = MaterialTheme.typography.labelLarge, color = Palette.Accent)
                }
            }
        }
        if (bike == null) {
            GlassCard(spacing = 12.dp) {
                Ico(R.drawable.ms_two_wheeler, 40.dp, Palette.Cyan)
                Text("Add your bike", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Type its odometer once: your rides keep it up to date. You get reminders for oil change, chain, " +
                        "air filter and service by km or months, and before your insurance, PUC or licence run out.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                PrimaryButton("Add your bike", R.drawable.ms_add, Modifier.fillMaxWidth(), height = 56.dp) { addingBike = true }
            }
        } else {
            GlassCard(spacing = 10.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        FitText(bike.name, MaterialTheme.typography.headlineMedium)
                        if (bike.reg.isNotBlank()) Text(bike.reg, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
                    }
                    GlassIconButton(R.drawable.ms_edit, "Edit bike", size = 46.dp, iconSize = 20.dp) { editingBike = true }
                }
                SectionLabel("Odometer")
                FitText(kmText(bike.odometer), MaterialTheme.typography.displayMedium)
                Text(
                    if (bike.addedKm > 0) "${kmText(bike.addedKm)} added by your rides since you typed it." else "Your rides add their km by themselves.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextSecondary,
                )
            }

            GlassCard(spacing = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Service", Modifier.weight(1f))
                    Text("+ Add", style = MaterialTheme.typography.labelLarge, color = Palette.Accent, modifier = Modifier.clickable { addingItem = true }.padding(6.dp))
                }
                if (bike.items.isEmpty()) Text("Nothing to track. Add an oil change or anything else.", style = MaterialTheme.typography.bodyMedium)
                bike.items.forEach { item ->
                    val note = GarageLogic.itemNote(item, bike.odometer, nowMs)
                    Column(
                        Modifier.fillMaxWidth().clickable { editingItem = item },
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (note.urgency == Urgency.DUE) "Due now" else note.text.removePrefix(item.name).trim().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (note.urgency == Urgency.OK) Palette.TextSecondary else note.urgency.color(),
                                )
                            }
                            GlassButton("Done", R.drawable.ms_check_circle, height = 44.dp) { onUpdate(GarageLogic.done(bike, item.id, nowMs)) }
                        }
                        val brush = when (note.urgency) {
                            Urgency.DUE -> Palette.StopGradient
                            Urgency.SOON -> Brush.linearGradient(listOf(Palette.AmberBright, Palette.OrangeBright))
                            Urgency.OK -> Palette.GoGradient
                        }
                        ProgressBar(GarageLogic.used(item, bike.odometer, nowMs), brush = brush)
                        Text(
                            "Every " + listOfNotNull(
                                item.everyKm.takeIf { it > 0 }?.let { kmText(it.toDouble()) },
                                item.everyMonths.takeIf { it > 0 }?.let { "$it ${if (it == 1) "month" else "months"}" },
                            ).joinToString(" or ") + " · last at ${kmText(item.lastKm)}, ${GarageLogic.dateText(item.lastAtMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.TextTertiary,
                        )
                    }
                }
            }

            GlassCard(spacing = 12.dp) {
                SectionLabel("Papers")
                DocRow("Insurance", bike.insuranceUntilMs, nowMs) { pickDate(context, bike.insuranceUntilMs) { onUpdate(bike.copy(insuranceUntilMs = it)) } }
                DocRow("PUC (pollution)", bike.pucUntilMs, nowMs) { pickDate(context, bike.pucUntilMs) { onUpdate(bike.copy(pucUntilMs = it)) } }
                DocRow("Driving licence", g.licenceUntilMs, nowMs) { pickDate(context, g.licenceUntilMs) { onLicence(it) } }
                Text("You get a notification 30 days and 7 days before, and on the day.", style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
            }

            GlassButton(if (confirmDelete) "Tap again to remove ${bike.name}" else "Remove this bike", R.drawable.ms_close, Modifier.fillMaxWidth(), height = 48.dp) {
                if (confirmDelete) {
                    onDelete(bike.id)
                    confirmDelete = false
                } else {
                    confirmDelete = true
                }
            }
        }
    }

    if (addingBike) {
        BikeDialog("Add a bike", null, onCancel = { addingBike = false }) { name, reg, odo ->
            onAddBike(name, reg, odo ?: 0.0)
            addingBike = false
        }
    }
    if (editingBike && bike != null) {
        BikeDialog("Edit bike", bike, onCancel = { editingBike = false }) { name, reg, odo ->
            val renamed = bike.copy(name = name.ifBlank { bike.name }.take(40), reg = reg.trim().uppercase().take(20))
            onUpdate(if (odo != null) GarageLogic.setOdometer(renamed, odo) else renamed)
            editingBike = false
        }
    }
    if (bike != null && (editingItem != null || addingItem)) {
        val item = editingItem
        ItemDialog(
            item,
            odometer = bike.odometer,
            onCancel = { editingItem = null; addingItem = false },
            onDelete = item?.let { { onUpdate(bike.copy(items = bike.items.filter { it.id != item.id })); editingItem = null } },
        ) { saved ->
            onUpdate(bike.copy(items = if (item == null) bike.items + saved else bike.items.map { if (it.id == saved.id) saved else it }))
            editingItem = null
            addingItem = false
        }
    }
}

@Composable
private fun DocRow(label: String, untilMs: Long, nowMs: Long, onPick: () -> Unit) {
    val note = GarageLogic.docNote(label, untilMs, nowMs)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onPick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    note == null -> "Tap to add the end date"
                    note.urgency == Urgency.OK -> "Until ${GarageLogic.dateText(untilMs)}"
                    else -> note.text.removePrefix(label).trim().replaceFirstChar { it.uppercase() }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = note?.urgency?.takeIf { it != Urgency.OK }?.color() ?: Palette.TextSecondary,
            )
        }
        Ico(R.drawable.ms_schedule, 22.dp, Palette.TextSecondary, "Change date")
    }
}

private fun parseKm(text: String): Double? = text.filter { it.isDigit() || it == '.' }.toDoubleOrNull()?.takeIf { it in 0.0..9_999_999.0 }

/** Name, number plate and odometer. The odometer is only passed on when it was typed or changed. */
@Composable
private fun BikeDialog(title: String, bike: Bike?, onCancel: () -> Unit, onSave: (String, String, Double?) -> Unit) {
    var name by remember { mutableStateOf(bike?.name.orEmpty()) }
    var reg by remember { mutableStateOf(bike?.reg.orEmpty()) }
    val shownOdo = bike?.odometer?.toLong()?.toString().orEmpty()
    var odo by remember { mutableStateOf(shownOdo) }
    GlassDialog(onDismiss = onCancel) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        GlassTextField(name, { name = it.take(40) }, "Bike", placeholder = "e.g. Classic 350", textStyle = MaterialTheme.typography.bodyLarge)
        GlassTextField(reg, { reg = it.take(20) }, "Number plate (optional)", placeholder = "e.g. MH12AB1234", textStyle = MaterialTheme.typography.bodyLarge)
        GlassTextField(
            odo,
            { odo = it.filter { c -> c.isDigit() }.take(7) },
            "Odometer (km)",
            placeholder = "e.g. 12000",
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        ButtonRow(2) {
            GlassButton("Cancel", modifier = Modifier.share(), onClick = onCancel)
            PrimaryButton("Save", modifier = Modifier.share(), height = 56.dp, enabled = name.isNotBlank()) {
                onSave(name.trim(), reg, parseKm(odo)?.takeIf { odo != shownOdo })
            }
        }
    }
}

/** A service item: what, every how many km or months, and when it was last done. */
@Composable
private fun ItemDialog(item: ServiceItem?, odometer: Double, onCancel: () -> Unit, onDelete: (() -> Unit)?, onSave: (ServiceItem) -> Unit) {
    var name by remember { mutableStateOf(item?.name.orEmpty()) }
    var km by remember { mutableStateOf(item?.everyKm?.takeIf { it > 0 }?.toString().orEmpty()) }
    var months by remember { mutableStateOf(item?.everyMonths?.takeIf { it > 0 }?.toString().orEmpty()) }
    var lastKm by remember { mutableStateOf((item?.lastKm ?: odometer).toLong().toString()) }
    val number = KeyboardOptions(keyboardType = KeyboardType.Number)
    GlassDialog(onDismiss = onCancel) {
        Text(if (item == null) "Add to track" else item.name, style = MaterialTheme.typography.headlineMedium)
        GlassTextField(name, { name = it.take(40) }, "What", placeholder = "e.g. Brake pads", textStyle = MaterialTheme.typography.bodyLarge)
        GlassTextField(km, { km = it.filter { c -> c.isDigit() }.take(6) }, "Every … km (optional)", placeholder = "e.g. 3000", textStyle = MaterialTheme.typography.bodyLarge, keyboardOptions = number)
        GlassTextField(months, { months = it.filter { c -> c.isDigit() }.take(2) }, "Or every … months (optional)", placeholder = "e.g. 6", textStyle = MaterialTheme.typography.bodyLarge, keyboardOptions = number)
        GlassTextField(lastKm, { lastKm = it.filter { c -> c.isDigit() }.take(7) }, "Last done at (km)", textStyle = MaterialTheme.typography.bodyLarge, keyboardOptions = number)
        val everyKm = km.toIntOrNull() ?: 0
        val everyMonths = months.toIntOrNull() ?: 0
        ButtonRow(2) {
            GlassButton("Cancel", modifier = Modifier.share(), onClick = onCancel)
            PrimaryButton("Save", modifier = Modifier.share(), height = 56.dp, enabled = name.isNotBlank() && (everyKm > 0 || everyMonths > 0)) {
                val last = parseKm(lastKm) ?: odometer
                onSave(
                    ServiceItem(
                        id = item?.id ?: UUID.randomUUID().toString(),
                        name = name.trim(),
                        everyKm = everyKm,
                        everyMonths = everyMonths,
                        lastKm = last,
                        // The date stays; "Done" sets both to now.
                        lastAtMs = item?.lastAtMs ?: System.currentTimeMillis(),
                    ),
                )
            }
        }
        if (onDelete != null) GlassButton("Stop tracking this", R.drawable.ms_close, Modifier.fillMaxWidth(), height = 48.dp, onClick = onDelete)
    }
}
