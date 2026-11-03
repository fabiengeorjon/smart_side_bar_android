# Manifest components (service, receiver, activity) are kept automatically by AAPT rules.
# AndroidX/Compose/DataStore ship their own consumer rules.

# Keep enum names stable: they are persisted as strings in DataStore.
-keepclassmembers enum com.local.edgebar.core.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}
