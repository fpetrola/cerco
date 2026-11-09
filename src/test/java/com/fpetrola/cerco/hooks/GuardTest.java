package com.fpetrola.cerco.hooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GuardTest {
  private final Guard guard = new Guard();

  private Decision decide(String command) {
    return guard.decide(new HookInput("PreToolUse", "Bash", command, "", false));
  }

  @Test
  void readingTheGateFilesIsFine() {
    assertFalse(decide("cat .cerco/baseline/no-switch.txt | head").denied());
    assertFalse(decide("cat pom.xml .claude/settings.json .cerco/baseline/frozen").denied());
    assertFalse(decide("git diff fd3860a HEAD --stat -- .cerco .claude").denied());
    assertFalse(decide("ls -la .claude/settings.json").denied());
    assertFalse(decide("ls -la .cerco .cerco/baseline && wc -l .cerco/baseline/* 2>/dev/null").denied());
    assertFalse(decide("find .cerco -type f | sort").denied());
    assertFalse(decide("git commit -m 'freeze .cerco/baseline\n\nCo-Authored-By: X <x@example.com>'").denied());
    assertFalse(decide("mvn -q -B -o com.fpetrola:cerco-maven-plugin:install-hooks && cat .claude/settings.json")
        .denied());
    assertFalse(decide("mvn -q verify").denied());
    assertFalse(decide("rm -rf target && cat .cerco/baseline/frozen").denied());
    assertFalse(decide("").denied());
  }

  @Test
  void writingTheGateFilesIsRefused() {
    assertTrue(decide("rm -rf .cerco/baseline").denied());
    assertTrue(decide("echo '' > .cerco/exemptions.yaml").denied());
    assertTrue(decide("echo x 2> .cerco/approved").denied());
    assertTrue(decide("cat <<EOF >> .cerco/exemptions.yaml\nfoo\nEOF").denied());
    assertTrue(decide("cd x && sed -i 's/a/b/' .cerco/approved").denied());
    assertTrue(decide("git checkout -- .cerco/baseline/frozen").denied());
    assertTrue(decide("python3 -c \"open('.cerco/approved','w')\"").denied());
    assertTrue(decide("jq '.hooks={}' .claude/settings.json > tmp && mv tmp .claude/settings.json").denied());
    assertTrue(decide("cd .cerco && rm -f approved").denied());
    assertTrue(decide("find .cerco -name '*.txt' -delete").denied());
    assertTrue(decide("FOO=1 sudo rm .cerco/approved").denied());
    assertTrue(decide("find .cerco -name '*.txt' | xargs rm").denied());
    assertTrue(decide("ls .cerco/baseline | xargs -I{} rm .cerco/baseline/{}").denied());
  }

  @Test
  void switchingTheGateOffIsRefused() {
    Decision decision = decide("mvn -Dcerco.skip=true verify");
    assertTrue(decision.denied());
    assertTrue(decision.reason().contains("not optional"));
    assertTrue(decision.toHookJson().contains("\"permissionDecision\":\"deny\""));
    assertFalse(decide("grep -n 'cerco.skip' README.md").denied());
  }

  @Test
  void theShellIsReadLikeAShell() {
    ShellCommand shell = new ShellCommand("cat a.txt 2>/dev/null | tee out.log; echo \"x > y\" >> log");
    assertEquals(3, shell.simples().size());
    assertEquals(List.of("/dev/null"), shell.simples().get(0).redirectTargets());
    assertEquals(List.of("echo", "x > y"), shell.simples().get(2).words());
    assertEquals(List.of("log"), shell.simples().get(2).redirectTargets());
    assertTrue(shell.writesAnything());
    assertFalse(new ShellCommand("ls -la && git status").writesAnything());
    assertFalse(new ShellCommand("mvn -q -o compile 2>&1 | tail -5").writesAnything());
    assertTrue(new ShellCommand("cat > X.java <<'EOF'\nclass X {}\nEOF").writesAnything());
    assertTrue(new ShellCommand("python3 fix.py").writesAnything());
  }
}
