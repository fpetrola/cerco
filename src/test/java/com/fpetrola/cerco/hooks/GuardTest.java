package com.fpetrola.cerco.hooks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GuardTest {
  private final Guard guard = new Guard();

  private Decision decide(String command) {
    return guard.decide(new HookInput("PreToolUse", "Bash", command, "", false));
  }

  @Test
  void readingTheGateFilesIsFine() {
    assertFalse(decide("cat .cerco/baseline/no-switch.txt | head").denied());
    assertFalse(decide("git diff -- .cerco/").denied());
    assertFalse(decide("ls -la .claude/settings.json").denied());
    assertFalse(decide("git commit -m 'freeze .cerco/baseline\n\nCo-Authored-By: X <x@example.com>'").denied());
    assertTrue(decide("echo x 2> .cerco/approved").denied());
    assertFalse(decide("mvn -q verify").denied());
    assertFalse(decide("").denied());
  }

  @Test
  void writingTheGateFilesIsRefused() {
    assertTrue(decide("rm -rf .cerco/baseline").denied());
    assertTrue(decide("echo '' > .cerco/exemptions.yaml").denied());
    assertTrue(decide("cd x && sed -i 's/a/b/' .cerco/approved").denied());
    assertTrue(decide("git checkout -- .cerco/baseline/frozen").denied());
    assertTrue(decide("python3 -c \"open('.cerco/approved','w')\"").denied());
    assertTrue(decide("jq '.hooks={}' .claude/settings.json > tmp && mv tmp .claude/settings.json").denied());
  }

  @Test
  void switchingTheGateOffIsRefused() {
    Decision decision = decide("mvn -Dcerco.skip=true verify");
    assertTrue(decision.denied());
    assertTrue(decision.reason().contains("not optional"));
    assertTrue(decision.toHookJson().contains("\"permissionDecision\":\"deny\""));
    assertFalse(decide("grep -n 'cerco.skip' README.md").denied());
  }
}
