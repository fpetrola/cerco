package com.fpetrola.cerco.surface;

import java.util.regex.Pattern;

/**
 * A signature of the surface as a test names it, read from the text alone: a type by its simple name, a constructor
 * by `new Type` or `Type::new`, any other member by its name. Lexical on purpose: the test is what the agent wrote
 * first, and naming is what it can be asked to show.
 */
final class Named {
  private final String signature;

  Named(String signature) {
    this.signature = signature.replaceFirst("^<[^ ]*> ", "");
  }

  /** Whether this text names the signature. */
  boolean in(String text) {
    int hash = signature.indexOf('#');
    String type = simple(hash < 0 ? signature.substring(0, signature.indexOf(' ') < 0 ? signature.length()
        : signature.indexOf(' ')) : signature.substring(0, hash));
    if (hash < 0) {
      return word(type).matcher(text).find();
    }
    String name = signature.substring(hash + 1).split("[(:]", 2)[0].strip();
    if (name.equals("<init>")) {
      return Pattern.compile("new\\s+" + Pattern.quote(type) + "\\b|\\b" + Pattern.quote(type) + "::new").matcher(text)
          .find();
    }
    return word(name).matcher(text).find();
  }

  private static String simple(String qualified) {
    String bare = qualified.replaceFirst("<.*", "");
    return bare.substring(Math.max(bare.lastIndexOf('.'), bare.lastIndexOf('$')) + 1);
  }

  private static Pattern word(String name) {
    return Pattern.compile("\\b" + Pattern.quote(name) + "\\b");
  }
}
