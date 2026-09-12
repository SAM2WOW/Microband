package com.unsame.microband

import android.app.Application
import com.unsame.microband.bluetooth.BandAssociationManager
import com.unsame.microband.bluetooth.BandConnectionManager
import com.unsame.microband.data.AppDatabase
import com.unsame.microband.data.MicrobandPreferences

class MicrobandApplication : Application() {
    val preferences by lazy { MicrobandPreferences(this) }
    val database by lazy { AppDatabase.create(this) }
    val associationManager by lazy { BandAssociationManager(this, preferences) }
    val connectionManager by lazy {
        BandConnectionManager(
            application = this,
            packetLogDao = database.packetLogDao(),
            preferences = preferences,
        )
    }
}
