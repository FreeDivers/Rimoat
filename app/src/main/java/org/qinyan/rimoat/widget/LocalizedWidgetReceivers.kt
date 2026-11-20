package org.qinyan.rimoat.widget

// Keep each Glance widget class distinct so launcher previews match their target locale.
class EnSingleRemoteWidget : RemoteAppWidget(WidgetKind.SINGLE)
class EnSingleRemoteWidgetReceiver : RemoteAppWidgetReceiver() { override val glanceAppWidget = EnSingleRemoteWidget() }
class EnRemotePanelWidget : RemoteAppWidget(WidgetKind.PANEL)
class EnRemotePanelWidgetReceiver : RemoteAppWidgetReceiver() { override val glanceAppWidget = EnRemotePanelWidget() }
class EnDeviceStatusWidget : RemoteAppWidget(WidgetKind.STATUS)
class EnDeviceStatusWidgetReceiver : RemoteAppWidgetReceiver() { override val glanceAppWidget = EnDeviceStatusWidget() }
class JaSingleRemoteWidget : RemoteAppWidget(WidgetKind.SINGLE)
class JaSingleRemoteWidgetReceiver : RemoteAppWidgetReceiver() { override val glanceAppWidget = JaSingleRemoteWidget() }
class JaRemotePanelWidget : RemoteAppWidget(WidgetKind.PANEL)
class JaRemotePanelWidgetReceiver : RemoteAppWidgetReceiver() { override val glanceAppWidget = JaRemotePanelWidget() }
class JaDeviceStatusWidget : RemoteAppWidget(WidgetKind.STATUS)
class JaDeviceStatusWidgetReceiver : RemoteAppWidgetReceiver() { override val glanceAppWidget = JaDeviceStatusWidget() }
class HansSingleRemoteWidget : RemoteAppWidget(WidgetKind.SINGLE)
class HansSingleRemoteWidgetReceiver : RemoteAppWidgetReceiver() { override val glanceAppWidget = HansSingleRemoteWidget() }
class HansRemotePanelWidget : RemoteAppWidget(WidgetKind.PANEL)
class HansRemotePanelWidgetReceiver : RemoteAppWidgetReceiver() { override val glanceAppWidget = HansRemotePanelWidget() }
class HansDeviceStatusWidget : RemoteAppWidget(WidgetKind.STATUS)
class HansDeviceStatusWidgetReceiver : RemoteAppWidgetReceiver() { override val glanceAppWidget = HansDeviceStatusWidget() }
class HantSingleRemoteWidget : RemoteAppWidget(WidgetKind.SINGLE)
class HantSingleRemoteWidgetReceiver : RemoteAppWidgetReceiver() { override val glanceAppWidget = HantSingleRemoteWidget() }
class HantRemotePanelWidget : RemoteAppWidget(WidgetKind.PANEL)
class HantRemotePanelWidgetReceiver : RemoteAppWidgetReceiver() { override val glanceAppWidget = HantRemotePanelWidget() }
class HantDeviceStatusWidget : RemoteAppWidget(WidgetKind.STATUS)
class HantDeviceStatusWidgetReceiver : RemoteAppWidgetReceiver() { override val glanceAppWidget = HantDeviceStatusWidget() }
