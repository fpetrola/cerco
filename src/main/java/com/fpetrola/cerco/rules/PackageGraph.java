package com.fpetrola.cerco.rules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** The dependency graph between packages, and its strongly connected components by Tarjan's algorithm. */
public final class PackageGraph {
  private final Map<String, Set<String>> edges;
  private final Map<String, Integer> index = new HashMap<>();
  private final Map<String, Integer> low = new HashMap<>();
  private final Deque<String> stack = new ArrayDeque<>();
  private final List<Set<String>> components = new ArrayList<>();
  private int counter;

  public PackageGraph(Map<String, Set<String>> edges) {
    this.edges = edges;
  }

  /** Every group of packages that reach each other, smallest package name first. */
  public List<Set<String>> cycles() {
    for (String node : new TreeSet<>(edges.keySet())) {
      if (!index.containsKey(node)) {
        connect(node);
      }
    }
    return components.stream().filter(component -> component.size() > 1).toList();
  }

  private void connect(String node) {
    index.put(node, counter);
    low.put(node, counter);
    counter++;
    stack.push(node);
    for (String next : edges.getOrDefault(node, Set.of())) {
      if (!index.containsKey(next)) {
        connect(next);
        low.put(node, Math.min(low.get(node), low.get(next)));
      } else if (stack.contains(next)) {
        low.put(node, Math.min(low.get(node), index.get(next)));
      }
    }
    if (low.get(node).equals(index.get(node))) {
      Set<String> component = new TreeSet<>();
      String member;
      do {
        member = stack.pop();
        component.add(member);
      } while (!member.equals(node));
      components.add(component);
    }
  }
}
