package com.fpetrola.cerco.hooks;

import java.io.Console;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

/**
 * Whether this JVM talks to a person at a terminal. From Java 22 on {@code System.console()} answers even when the
 * streams are pipes, so the newer {@code isTerminal()} is asked through reflection when the JVM has it.
 */
public final class Terminal {

  public boolean interactive() {
    Console console = System.console();
    if (console == null) {
      return false;
    }
    Optional<Method> isTerminal = Arrays.stream(Console.class.getMethods())
        .filter(method -> method.getName().equals("isTerminal") && method.getParameterCount() == 0).findFirst();
    return isTerminal.map(method -> ask(method, console)).orElse(true);
  }

  private boolean ask(Method isTerminal, Console console) {
    try {
      return Boolean.TRUE.equals(isTerminal.invoke(console));
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("could not ask the console whether it is a terminal", e);
    }
  }
}
