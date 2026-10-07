package com.ridecomm.app.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min

/** My profile photo: a small square JPEG kept in app storage and shared with the ride. */
object Profile {
    private const val FILE = "avatar.jpg"
    /** Small enough to send to every rider in a few kilobytes. */
    const val SIZE_PX = 192

    private val _photo = MutableStateFlow<Bitmap?>(null)
    /** My current photo, or null for the initial-letter avatar. */
    val photo: StateFlow<Bitmap?> = _photo.asStateFlow()
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val file = File(context.filesDir, FILE)
        if (file.exists()) _photo.value = BitmapFactory.decodeFile(file.path)
    }

    /** The JPEG bytes to send to other riders, or null if I have no photo. */
    fun photoBytes(context: Context): ByteArray? = File(context.filesDir, FILE).takeIf { it.exists() }?.readBytes()

    /** Crops the picked image to a centred square, scales it down and saves it. */
    suspend fun setPhoto(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val source = runCatching { decode(context, uri) }.getOrNull() ?: return@withContext false
        val side = min(source.width, source.height)
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, SIZE_PX, SIZE_PX, true)
        val bytes = ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
        File(context.filesDir, FILE).writeBytes(bytes)
        _photo.value = scaled
        true
    }

    fun removePhoto(context: Context) {
        File(context.filesDir, FILE).delete()
        _photo.value = null
    }

    private fun decode(context: Context, uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                // Decode at a reduced size: we only keep 192 px.
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > 1024) decoder.setTargetSampleSize(longest / 1024)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri).use { input ->
                BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = 4 })!!
            }
        }
}
