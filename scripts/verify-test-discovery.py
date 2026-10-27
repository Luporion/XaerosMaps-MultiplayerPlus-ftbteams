#!/usr/bin/env python3
"""Fail-closed source checks: JUnit must EXECUTE tests, not merely compile CLI mains.

These guards do not replace a full NeoForge/Gradle CI run.
"""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
neo=(r/'build.neoforge.gradle.kts').read_text(encoding='utf-8')
core=(r/'core/build.gradle.kts').read_text(encoding='utf-8')
workflow=(r/'.github/workflows/rogler-neoforge-build.yml').read_text(encoding='utf-8')
wrappers=[
 r/'src/test/kotlin/dev/alinco8/xmmp/client/io/PersonalExplorationJournalGradleTest.kt',
 r/'core/src/test/kotlin/dev/alinco8/xmmp/core/sharing/SharingPolicyGradleTest.kt',
]
for text in [neo,core]:
    assert 'useJUnitPlatform()' in text
    assert 'junit-jupiter:5.11.4' in text
    assert 'junit-platform-launcher:1.11.4' in text
    assert 'failOnNoDiscoveredTests = false' not in text
for wrapper in wrappers:
    value=wrapper.read_text(encoding='utf-8')
    assert '@Test' in value and 'main()' in value and 'class ' in value
assert ':1.21.1-neoforge:build :core:test' in workflow
assert 'scripts/verify-test-discovery.py' in workflow
print('v0.3.0 test discovery: both JUnit wrappers, engines, launcher and Gradle test tasks configured')
