# JNI uses these exact class/method names from ac_encoder.cpp.
-keep class org.qinyan.rimoat.data.NativeAcEncoder { *; }

# Room constructs WorkManager's generated database implementation by reflection.
-keep class androidx.work.impl.WorkDatabase_Impl {
    public <init>();
}
# WorkManager creates input mergers by their stored class names.
-keepclassmembers class * extends androidx.work.InputMerger {
    public <init>();
}

# Glance restores callback classes by their name from a PendingIntent.
-keep class org.qinyan.rimoat.widget.SendWidgetCommand { public <init>(); }
-keep class org.qinyan.rimoat.widget.ReloadWidgets { public <init>(); }

# Glance maps provider instances by their concrete widget class canonical name.
# Preserve distinct class identities: horizontal class merging would update all
# three providers with whichever widget happens to run updateAll first.
-keep class org.qinyan.rimoat.widget.SingleRemoteWidget { public <init>(); }
-keep class org.qinyan.rimoat.widget.RemotePanelWidget { public <init>(); }
-keep class org.qinyan.rimoat.widget.DeviceStatusWidget { public <init>(); }

# Localized picker providers also need independent Glance class identities.
-keep class org.qinyan.rimoat.widget.* extends org.qinyan.rimoat.widget.RemoteAppWidget { public <init>(); }

# Glance renders vectors without a Compose UI owner or its shared graphics cache.
-assumevalues class androidx.compose.ui.ComposeUiFlags {
    public static boolean isVectorDrawCacheSharingEnabled return false;
}
