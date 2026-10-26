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
    val aScope="team-$teamA"; val bScope="team-$teamB"
    expect(SharingPolicy.scope(a,SharingMode.PRIVATE)=="private-$alice",true)
    expect(SharingPolicy.scope(a,SharingMode.TEAM)==aScope,true)
    expect(SharingPolicy.scope(b,SharingMode.TEAM)==aScope,true)
    expect(SharingPolicy.scope(e,SharingMode.TEAM)==bScope,true)
    expect(SharingPolicy.scope(nonTeam,SharingMode.TEAM)==null,true)
    expect(SharingPolicy.scope(a,SharingMode.GLOBAL)=="global",true)
    expect(SharingPolicy.canReadScope(a,SharingMode.TEAM,aScope),true)
    expect(SharingPolicy.canReadScope(b,SharingMode.TEAM,aScope),true)
    expect(SharingPolicy.canReadScope(e,SharingMode.TEAM,aScope),false)
    expect(SharingPolicy.canReadScope(SharingIdentity(alice,teamB),SharingMode.TEAM,aScope),false)
    expect(SharingPolicy.canReadScope(a,SharingMode.PRIVATE,"private-$bob"),false)
    expect(SharingPolicy.canReadScope(a,SharingMode.TEAM,"global"),false)
    expect(SharingPolicy.isValidScope(aScope),true)
    expect(SharingPolicy.isValidScope("private-$alice"),true)
    expect(SharingPolicy.isValidScope("global"),true)
    expect(SharingPolicy.isValidScope("../../evil"),false)
    expect(SharingPolicy.isValidScope("team-not-a-uuid"),false)
    expect(SharingPolicy.isValidScope("team-$teamA/../../evil"),false)
    expect(SharingPolicy.resolveMode(0)==SharingMode.PRIVATE,true)
    expect(SharingPolicy.resolveMode(1)==SharingMode.TEAM,true)
    expect(SharingPolicy.resolveMode(2)==SharingMode.GLOBAL,true)
    expect(SharingPolicy.resolveMode(3)==null,true)
    expect(SharingPolicy.resolveMode(-1)==null,true)
    expect(SharingPolicy.TERRAIN_TRANSPORT_ENABLED,true)
    println("Rogler XMMP v0.2.0 SharingPolicyTests: $checked checks OK")
}
