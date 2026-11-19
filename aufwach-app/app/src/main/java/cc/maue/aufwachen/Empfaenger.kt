package cc.maue.aufwachen

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class Empfaenger : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED -> {
                val ende = goAsync()
                Logik.weckerGeaendert(c)
                // dem Netzwerkaufruf etwas Zeit geben
                Thread { Thread.sleep(8000); ende.finish() }.start()
            }
            Logik.AKTION_WAECHTER_START, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                Logik.planen(c)
        }
    }
}
