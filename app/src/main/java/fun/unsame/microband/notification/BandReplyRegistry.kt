package com.unsame.microband.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.app.RemoteInput
import java.util.concurrent.ConcurrentHashMap

/** Keeps only short-lived Android direct-reply handles; notification text is never persisted. */
object BandReplyRegistry {
    private data class Target(
        val action: PendingIntent,
        val remoteInputs: Array<RemoteInput>,
        val registeredAt: Long,
    )

    private val targets = ConcurrentHashMap<Int, Target>()

    fun idFor(notificationKey: String): Int = (notificationKey.hashCode() and Int.MAX_VALUE).coerceAtLeast(1)

    fun register(notificationKey: String, action: PendingIntent, remoteInputs: Array<RemoteInput>) {
        if (remoteInputs.isEmpty()) return
        prune()
        targets[idFor(notificationKey)] = Target(action, remoteInputs, System.currentTimeMillis())
    }

    fun remove(notificationKey: String) {
        targets.remove(idFor(notificationKey))
    }

    fun canReply(id: Int): Boolean = targets[id]?.let {
        System.currentTimeMillis() - it.registeredAt <= MAX_AGE_MILLIS
    } == true

    fun send(context: Context, id: Int, text: String): Result<Unit> = runCatching {
        val target = targets[id] ?: error("The original notification is no longer available")
        if (System.currentTimeMillis() - target.registeredAt > MAX_AGE_MILLIS) {
            targets.remove(id)
            error("The original notification reply expired")
        }
        val results = Bundle().apply {
            target.remoteInputs.forEach { putCharSequence(it.resultKey, text) }
        }
        val fillIn = Intent()
        RemoteInput.addResultsToIntent(target.remoteInputs, fillIn, results)
        target.action.send(context, 0, fillIn)
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MILLIS
        targets.entries.removeAll { it.value.registeredAt < cutoff }
    }

    private const val MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L
}
