package ai.gravityfield.gravity_sdk.utils

import ai.gravityfield.gravity_sdk.prefs.Prefs
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DeviceIdConcurrencyTest {
    @Test fun concurrentFirstCallersShareOneGeneratedAndAppliedDeviceId() {
        checkConcurrentCreation(malformed = false)
    }

    @Test fun concurrentCallersReplaceNonStringDeviceIdOnlyOnce() {
        checkConcurrentCreation(malformed = true)
    }

    @Test fun concurrentCallersReplaceEmptyDeviceIdOnlyOnce() {
        checkConcurrentCreation(malformed = false, empty = true)
    }

    private fun checkConcurrentCreation(malformed: Boolean, empty: Boolean = false) {
        val context = RuntimeEnvironment.getApplication()
        val stored = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
        stored.edit().clear().apply {
            if (empty) putString("gravity_device_id", "")
            if (malformed) putInt("gravity_device_id", 42)
            putString("host-key", "preserved")
        }.commit()
        val reading = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writes = AtomicInteger()
        val applies = AtomicInteger()
        fun holdRead() {
            reading.countDown()
            check(release.await(10, TimeUnit.SECONDS)) { "Device ID read was not released" }
        }
        val preferences = object : SharedPreferences by stored {
            override fun contains(key: String): Boolean {
                val present = stored.contains(key)
                if (key == "gravity_device_id" && !present) holdRead()
                return present
            }

            override fun getString(key: String, defValue: String?): String? {
                val result = runCatching { stored.getString(key, defValue) }
                if (key == "gravity_device_id" && (result.exceptionOrNull() is ClassCastException || result.getOrNull() == "")) holdRead()
                return result.getOrThrow()
            }

            override fun edit(): SharedPreferences.Editor {
                val editor = stored.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putString(key: String, value: String?): SharedPreferences.Editor {
                        assertEquals("gravity_device_id", key)
                        writes.incrementAndGet()
                        editor.putString(key, value)
                        return this
                    }

                    override fun apply() {
                        applies.incrementAndGet()
                        editor.apply()
                    }

                    override fun commit(): Boolean = error("Device ID writes must remain asynchronous")
                }
            }
        }
        val instrumentedContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = preferences
        }
        val calls = List(16) { FutureTask { DeviceUtils.getDevice().id } }
        val threads = calls.mapIndexed { index, call -> Thread(call, "device-id-caller-$index") }
        try {
            Prefs.initialize(instrumentedContext)
            threads.first().start()
            assertTrue("First caller did not reach the stored ID read", reading.await(5, TimeUnit.SECONDS))
            threads.drop(1).forEach { it.start() }
            // Each caller must reach either the held read or the creation lock before release.
            // This forces the old implementation to observe absence on every caller, without sleeps.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (threads.any { it.state != Thread.State.WAITING && it.state != Thread.State.TIMED_WAITING && it.state != Thread.State.BLOCKED }) {
                check(System.nanoTime() < deadline) { "Concurrent callers did not reach the held read or lock" }
                Thread.yield()
            }
            release.countDown()
            val ids = calls.map { it.get(5, TimeUnit.SECONDS) }
            assertEquals("Concurrent requests used different device IDs", 1, ids.toSet().size)
            assertEquals(ids.first(), UUID.fromString(ids.first()).toString())
            assertEquals(ids.first(), stored.getString("gravity_device_id", null))
            assertEquals("Device ID must be generated and written only once", 1, writes.get())
            assertEquals("Device ID must use one asynchronous apply", 1, applies.get())
            assertEquals("preserved", stored.getString("host-key", null))
        } finally {
            release.countDown()
            threads.forEach { it.join(5000) }
            Prefs.initialize(context)
        }
    }
}
