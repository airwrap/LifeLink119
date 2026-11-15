package com.lifelink119.app.core

import com.lifelink119.app.data.ExperienceCatalogParser
import com.lifelink119.app.data.ExperienceStateCodec
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class VehicleContextTest {
    private val now = 100_000L

    private fun reading(
        metric: VehicleMetric = VehicleMetric.BATTERY_SOC,
        value: Double? = 80.0,
        status: DiagnosticStatus = DiagnosticStatus.READ,
        at: Long = now,
    ) = VehicleSample(metric, VehicleDataOrigin.SDK, status, value, at)

    private fun snapshot(sample: VehicleSample = reading()) =
        VehicleContextSnapshot(samples = mapOf(sample.metric to sample))

    private fun engine(target: ConnectionTarget = ConnectionTarget.EMERGENCY_119): SessionEngine {
        val c =
            ExperienceCatalogParser.parse(
                File("src/main/assets/experience-catalog.json").readText()
            )
        return SessionEngine(c).apply {
            setRestrictions(DrivingRestrictions.unrestrictedFixture())
            start("DEMO-161616", target)
            answer()
        }
    }

    private fun rejects(block: () -> Unit) {
        try {
            block()
            fail("Expected rejection")
        } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {}
    }

    @Test
    fun nullAndZeroRemainDifferentForBatteryAndAirbag() {
        assertFalse(reading(value = null).fresh(now))
        assertTrue(reading(value = 0.0).fresh(now))
        assertTrue(reading(VehicleMetric.DRIVER_AIRBAG, 0.0).display(now).contains("없음"))
        assertFalse(reading(VehicleMetric.DRIVER_AIRBAG, null).fresh(now))
    }

    @Test
    fun invalidPercentSignalsAndNanNeverBecomeUsable() {
        for (value in listOf(-1.0, 101.0, Double.NaN, Double.POSITIVE_INFINITY, 50.5)) assertFalse(
            reading(value = value).fresh(now)
        )
        assertFalse(reading(VehicleMetric.DRIVER_AIRBAG, 2.0).fresh(now))
        assertFalse(reading(VehicleMetric.DRIVER_DOOR, -1.0).fresh(now))
    }

    @Test
    fun reversedAndExceededBoundsAreRejected() {
        assertFalse(
            reading(VehicleMetric.DRIVER_DOOR, 1.0).copy(minimum = 2.0, maximum = 0.0).fresh(now)
        )
        assertFalse(
            reading(VehicleMetric.DRIVER_DOOR, 2.0).copy(minimum = 0.0, maximum = 1.0).fresh(now)
        )
    }

    @Test
    fun freshnessBoundaryFutureAndClockResetAreConservative() {
        assertTrue(reading().fresh(now + 30_000))
        assertFalse(reading().fresh(now + 30_001))
        assertFalse(reading(at = now + 1).fresh(now))
        assertFalse(reading(at = -1).fresh(now))
    }

    @Test
    fun readFailuresCannotSmuggleNumericSuccessOrFallbackExample() {
        for (status in DiagnosticStatus.entries.filter { it != DiagnosticStatus.READ }) {
            val sample =
                VehicleContextPolicy.observation(
                    VehicleMetric.BATTERY_SOC,
                    VehicleDiagnostic(status, "error", 80.0),
                    now,
                )
            assertNull(sample.value)
            assertFalse(sample.fresh(now))
            assertEquals(VehicleDataOrigin.SDK, sample.origin)
        }
        assertEquals(
            DiagnosticStatus.ERROR,
            VehicleContextPolicy.observation(
                    VehicleMetric.BATTERY_SOC,
                    VehicleDiagnostic(DiagnosticStatus.READ, "", 110.0),
                    now,
                )
                .status,
        )
    }

    @Test
    fun batteryContractIsPercentageAndNotArbitraryRawInteger() {
        assertEquals(100.0, BatteryReadPolicy.position(100).rawValue!!, 0.0)
        assertEquals(DiagnosticStatus.ERROR, BatteryReadPolicy.position(101).status)
        assertEquals(DiagnosticStatus.UNAVAILABLE, BatteryReadPolicy.position(null).status)
    }

    @Test
    fun partialSnapshotOnlyIncludesFreshMatchingSourceAndMetric() {
        val data =
            mapOf(
                VehicleMetric.BATTERY_SOC to reading(),
                VehicleMetric.DRIVER_DOOR to
                    reading(VehicleMetric.DRIVER_DOOR, 0.0, at = now - 31_000),
                VehicleMetric.DRIVER_AIRBAG to
                    reading(VehicleMetric.DRIVER_AIRBAG, 1.0)
                        .copy(origin = VehicleDataOrigin.EXAMPLE),
            )
        assertEquals(
            listOf(VehicleMetric.BATTERY_SOC),
            VehicleContextSnapshot(samples = data).usable(now).map { it.metric },
        )
        val mismatched =
            VehicleContextSnapshot(
                samples =
                    mapOf(VehicleMetric.BATTERY_SOC to reading(VehicleMetric.DRIVER_DOOR, 0.0))
            )
        assertTrue(mismatched.usable(now).isEmpty())
        assertNull(mismatched.soc(now))
    }

    @Test
    fun sharingRequiresConsentAndFreshRead() {
        val e = engine()
        rejects { e.shareVehicleContext(snapshot(), false, now) }
        rejects { e.shareVehicleContext(snapshot(), true, now + 31_000) }
        assertNull(e.vehicleContextShare)
        e.shareVehicleContext(snapshot(), true, now)
        assertEquals("DEMO-161616", e.vehicleContextShare!!.sessionId)
        assertTrue(e.current!!.shared.isEmpty())
    }

    @Test
    fun sharedSnapshotIsFrozenAndNeverTracksLaterSensorChanges() {
        val e = engine()
        val mutable = mutableMapOf(VehicleMetric.BATTERY_SOC to reading())
        e.shareVehicleContext(VehicleContextSnapshot(samples = mutable), true, now)
        mutable[VehicleMetric.BATTERY_SOC] = reading(value = 15.0)
        assertEquals(80.0, e.vehicleContextShare!!.samples.single().value!!, 0.0)
    }

    @Test
    fun sharedTelemetryNeverEntersSessionPersistence() {
        val e = engine()
        val before = ExperienceStateCodec.encodePrivacySafe(e.current)
        e.shareVehicleContext(snapshot(), true, now)
        assertEquals(before, ExperienceStateCodec.encodePrivacySafe(e.current))
        e.restore(e.current, null)
        assertNull(e.vehicleContextShare)
    }

    @Test
    fun backgroundClearsTelemetryButDoesNotEndEmergency() {
        val e = engine()
        e.shareVehicleContext(snapshot(), true, now)
        e.background()
        assertNull(e.vehicleContextShare)
        assertTrue(e.current!!.active)
    }

    @Test
    fun expiryWithdrawsReceiptWithoutEndingEmergency() {
        val e = engine()
        e.shareVehicleContext(snapshot(), true, now)
        e.expireVehicleContext(now + 30_000)
        assertNotNull(e.vehicleContextShare)
        e.expireVehicleContext(now + 30_001)
        assertNull(e.vehicleContextShare)
        assertTrue(e.current!!.active)
    }

    @Test
    fun newPersonSessionFinishAndRevokeClearTelemetry() {
        val e = engine()
        e.shareVehicleContext(snapshot(), true, now)
        e.selectPerson(null)
        assertNull(e.vehicleContextShare)
        e.shareVehicleContext(snapshot(), true, now)
        e.revoke()
        assertNull(e.vehicleContextShare)
        e.shareVehicleContext(snapshot(), true, now)
        e.finish()
        assertNull(e.vehicleContextShare)
        e.start("DEMO-171717", ConnectionTarget.EMERGENCY_112)
        e.answer()
        e.shareVehicleContext(snapshot(), true, now)
        e.start("DEMO-181818", ConnectionTarget.EMERGENCY_119)
        assertNull(e.vehicleContextShare)
    }

    @Test
    fun actualRestrictionsAnd109RejectVehicleSharingButNotConnection() {
        val e = engine(ConnectionTarget.COUNSEL_109)
        rejects { e.shareVehicleContext(snapshot(), true, now) }
        assertTrue(e.current!!.active)
        val urgent = engine()
        urgent.shareVehicleContext(snapshot(), true, now)
        urgent.setRestrictions(DrivingRestrictions())
        assertNull(urgent.vehicleContextShare)
        rejects { urgent.shareVehicleContext(snapshot(), true, now) }
        assertTrue(urgent.current!!.active)
    }

    @Test
    fun fixturesAreExplicitAndMissingOrStaleFixturesCannotShare() {
        for (kind in listOf("NORMAL", "INCIDENT")) {
            val s = VehicleContextPolicy.example(kind, now)
            assertEquals(VehicleDataOrigin.EXAMPLE, s.origin)
            assertEquals(3, s.usable(now).size)
        }
        for (kind in listOf("NO_DATA", "STALE")) assertTrue(
            VehicleContextPolicy.example(kind, now).usable(now).isEmpty()
        )
    }

    @Test
    fun freshSdkSocDrivesPowerFormulaAndLowSocNeverCreatesEnergy() {
        val s = snapshot().soc(now)!!
        assertEquals(
            27.2,
            V2lCalculator.estimate(true, true, 64.0, s.value!!, 30.0, .85, 200.0).availableKwh!!,
            .00001,
        )
        assertEquals(
            0.0,
            V2lCalculator.estimate(true, true, 64.0, 15.0, 30.0, .85, 200.0).availableKwh!!,
            0.0,
        )
        assertNull(snapshot().soc(now + 30_001))
    }
}
