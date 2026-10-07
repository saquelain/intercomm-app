# Libraries (LiveKit, WebRTC, JNA, Media3, Compose, coroutines) ship their own keep rules.
# RideComm itself uses no reflection, so nothing of ours needs keeping.

# Readable stack traces in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
