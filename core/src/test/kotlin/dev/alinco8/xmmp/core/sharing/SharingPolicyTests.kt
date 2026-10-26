package dev.alinco8.xmmp.core.sharing

import java.util.UUID

/** Minimal test runner independent of Minecraft and Gradle dependencies. */
fun main() {
    val alice = UUID.randomUUID(); val bob = UUID.randomUUID(); val eve = UUID.randomUUID()
    val teamA = UUID.randomUUID(); val teamB = UUID.randomUUID()
    var checked = 0
    fun expect(actual: Boolean, wanted: Boolean) { checked++; check(actual == wanted) { "Policy check #$checked" } }
    val a = SharingIdentity(alice, teamA)
    val b = SharingIdentity(bob, teamA)
    val e = SharingIdentity(eve, teamB)
    val private = SharingPolicy.captureGrant(a, SharingMode.PRIVATE)
    val party = SharingPolicy.captureGrant(a, SharingMode.TEAM)
    val global = SharingPolicy.captureGrant(a, SharingMode.GLOBAL)
    expect(SharingPolicy.allowed(private, a), true)
    expect(SharingPolicy.allowed(private, b), false)
    expect(SharingPolicy.allowed(private, e), false)
    expect(SharingPolicy.allowed(party, a), true)
    expect(SharingPolicy.allowed(party, b), true)
    expect(SharingPolicy.allowed(party, e), false)
    expect(SharingPolicy.allowed(party, SharingIdentity(bob, null)), false)
    expect(SharingPolicy.allowed(party, SharingIdentity(eve, teamA)), true)
    expect(SharingPolicy.allowed(global, e), true)
    expect(SharingPolicy.allowed(global, SharingIdentity(eve, null)), true)
    expect(SharingPolicy.allowed(party, SharingIdentity(alice, teamB)), true) // owner retains personal map
    val nonTeam = SharingIdentity(alice, null)
    expect(SharingPolicy.canPublishTeam(nonTeam), false)
    expect(SharingPolicy.canPublishTeam(a), true)
    try { SharingPolicy.captureGrant(nonTeam, SharingMode.TEAM); error("expected refusal") }
    catch (_: IllegalArgumentException) { checked++ }
    // A later team change never changes the original recipient audience.
    expect(SharingPolicy.allowed(party, SharingIdentity(bob, teamB)), false)
    expect(SharingPolicy.allowed(party, SharingIdentity(eve, teamB)), false)
    check(!SharingPolicy.TERRAIN_TRANSPORT_ENABLED) { "P0 must not enable unsafe legacy map sharing" }; checked++
    println("Rogler XMMP v0.1.0 SharingPolicyTests: $checked checks OK")
}
