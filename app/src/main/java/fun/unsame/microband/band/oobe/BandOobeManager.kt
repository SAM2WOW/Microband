package com.unsame.microband.band.oobe

import com.unsame.microband.band.model.BandDeviceInfo
import com.unsame.microband.band.model.BandException
import com.unsame.microband.band.model.FirmwareApplication
import com.unsame.microband.band.model.OobeStage
import com.unsame.microband.band.protocol.BandProtocol
import com.unsame.microband.data.MicrobandPreferences
import java.time.Instant
import java.time.ZoneId

enum class BandOobeStep(val userLabel: String) {
    Inspect("Checking Band"),
    Preparing("Preparing Band"),
    SettingClock("Setting clock"),
    Configuring("Configuring Band"),
    UpdatingProfile("Updating profile"),
    Finalizing("Finishing setup"),
    Verifying("Verifying setup"),
    Complete("Ready"),
}

class BandOobeManager(private val preferences: MicrobandPreferences) {
    suspend fun prepare(
        protocol: BandProtocol,
        device: BandDeviceInfo,
        onStep: (BandOobeStep) -> Unit,
    ) {
        validate(device)
        val profile = protocol.getProfileBytes()

        step(BandOobeStep.Preparing, onStep) {
            protocol.setOobeStage(OobeStage.CheckingForUpdate)
            protocol.setOobeStage(OobeStage.StartingUpdate)
            protocol.setOobeStage(OobeStage.UpdateComplete)
        }
        val now = Instant.now()
        step(BandOobeStep.SettingClock, onStep) { protocol.setUtcTime(now) }
        step(BandOobeStep.Configuring, onStep) {
            protocol.navigateToOobeBoot()
            protocol.setPlaceholderEphemeris()
            protocol.setTimeZone(ZoneId.systemDefault(), now)
            protocol.setOobeStage(OobeStage.WaitingOnPhoneToCompleteOobe)
        }
        step(BandOobeStep.UpdatingProfile, onStep) { protocol.setProfile(profile, Instant.now()) }
        step(BandOobeStep.Finalizing, onStep) { protocol.finalizeOobe() }
    }

    suspend fun markVerified(onStep: (BandOobeStep) -> Unit) {
        step(BandOobeStep.Complete, onStep) { }
    }

    private fun validate(device: BandDeviceInfo) {
        if (!device.isBand2) throw BandException.DeviceUnsupported()
        if (device.firmwareApplication != FirmwareApplication.App) throw BandException.WrongFirmware()
        if (device.oobeComplete == true) throw BandException.AlreadyConfigured()
        when (device.oobeStage) {
            OobeStage.PreStateLanguageSelect -> throw BandException.LanguageRequired()
            OobeStage.PreStateCharging -> throw BandException.InvalidPacket("Charge the Band before continuing")
            else -> Unit
        }
    }

    private suspend fun step(
        step: BandOobeStep,
        onStep: (BandOobeStep) -> Unit,
        block: suspend () -> Unit,
    ) {
        onStep(step)
        block()
        preferences.setOobeStep(step)
    }
}
