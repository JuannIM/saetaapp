# Proguard rules for SAETA Saldo Android

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Room entities, DAOs, and database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.saetasaldo.app.data.local.** { *; }

# Keep Gson models and DTOs
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.saetasaldo.app.data.remote.dto.** { *; }

# Keep Glance AppWidget and Actions
-keep class com.saetasaldo.app.widget.** { *; }
-keep class * extends androidx.glance.appwidget.action.ActionCallback { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }

# Keep ML Kit text recognition
-keep class com.google.mlkit.vision.text.** { *; }

# Keep Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }
