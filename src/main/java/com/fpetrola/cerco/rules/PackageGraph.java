package com.fpetrola.cerco.rules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** The dependency graph between packages, and its strongly connected components by Tarjan's algorithm. */
public final class PackageGraph {
  private final Map<String, Node> nodes = new TreeMap<>();
  private final Deque<Node> stack = new ArrayDeque<>();
  private final List<Set<String>> components = new ArrayList<>();
  private int counter;

  /** A package in the walk: what it depends on, when it was reached, and the earliest package it leads back to. */
  private static final class Node {
    private final String name;
    private final Set<String> targets;
    private Optional<Integer> reached = Optional.empty();
    private int low;

    Node(String name, Set<String> targets) {
      this.name = name;
      this.targets = targets;
    }

    void reach(int at) {
      reached = Optional.of(at);
      low = at;
    }

    void leadsBackTo(int earlier) {
      low = Math.min(low, earlier);
    }

    boolean rootsItsComponent() {
      return reached.filter(at -> at == low).isPresent();
    }
  }

  public PackageGraph(Map<String, Set<String>> edges) {
    edges.forEach((name, targets) -> nodes.put(name, new Node(name, targets)));
  }

  /** Every group of packages that reach each other, smallest package name first. */
  public List<Set<String>> cycles() {
    nodes.values().stream().filter(node -> node.reached.isEmpty()).forEach(this::connect);
    return components.stream().filter(component -> component.size() > 1).toList();
  }

  private void connect(Node node) {
    node.reach(counter++);
    stack.push(node);
    for (String target : node.targets) {
      Optional.ofNullable(nodes.get(target)).ifPresent(next -> follow(node, next));
    }
    if (node.rootsItsComponent()) {
      Set<String> component = new TreeSet<>();
      Node member = stack.pop();
      component.add(member.name);
      while (member != node) {
        member = stack.pop();
        component.add(member.name);
      }
      components.add(component);
    }
  }

  private void follow(Node node, Node next) {
    if (next.reached.isEmpty()) {
      connect(next);
      node.leadsBackTo(next.low);
    } else if (stack.contains(next)) {
      node.leadsBackTo(next.reached.orElse(next.low));
    }
  }
}
