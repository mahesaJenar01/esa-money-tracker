# Keep the export DTOs intact: they are serialized by name for data export.
-keepclassmembers class com.esa.moneytracker.data.export.** { *; }
