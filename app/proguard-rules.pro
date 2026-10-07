# Libraries (LiveKit, WebRTC, JNA, Media3, Compose, coroutines) ship their own keep rules.

# Data saver changes the voice bitrate through LiveKit's internal WebRTC sender, found by name.
-keepclassmembers class io.livekit.android.room.track.LocalAudioTrack {
    livekit.org.webrtc.RtpSender getSender$livekit_android_sdk_release();
}

# Readable stack traces in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
