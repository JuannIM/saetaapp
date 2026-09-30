# Proguard rules for SAETA Saldo Android
# Keep Room entities, DAOs and GSON DTOs
-keepclassmembers class * {
    @androidx.room.Entity *;
    @androidx.room.Dao *;
}
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
