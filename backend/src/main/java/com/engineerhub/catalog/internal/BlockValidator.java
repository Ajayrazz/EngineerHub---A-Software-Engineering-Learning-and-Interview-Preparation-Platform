package com.engineerhub.catalog.internal;

import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strict allowlist for lesson content blocks. Content is data, never markup: no raw HTML is
 * accepted anywhere, markdown is stored as text and rendered by the client with a renderer that
 * does not allow HTML, and unknown block types or keys are rejected rather than ignored.
 */
final class BlockValidator {

  static final int MAX_BLOCKS = 200;
  static final int MAX_MARKDOWN = 50_000;
  static final int MAX_CODE = 20_000;
  static final Set<String> LANGUAGES = Set.of("java", "python", "cpp", "text");
  static final Set<String> CALLOUT_KINDS = Set.of("info", "warning", "tip");

  /**
   * What a visualization block may hold: an allowlisted algorithm id with a strictly shaped input
   * (and value).
   */
  record VizSpec(String inputPattern, String paramPattern) {}

  private static final String NUMBERS_16 = "^-?\\d{1,3}(\\s*,\\s*-?\\d{1,3}){1,15}$";
  private static final String NUMBERS_12 = "^-?\\d{1,3}(\\s*,\\s*-?\\d{1,3}){1,11}$";

  /**
   * Kept in step with frontend/src/lib/viz/catalog.json (VizCatalogParityTest fails if they drift).
   */
  static final Map<String, VizSpec> VIZ =
      Map.ofEntries(
          Map.entry("bubble-sort", new VizSpec(NUMBERS_16, null)),
          Map.entry("selection-sort", new VizSpec(NUMBERS_16, null)),
          Map.entry("insertion-sort", new VizSpec(NUMBERS_16, null)),
          Map.entry("merge-sort", new VizSpec(NUMBERS_12, null)),
          Map.entry("binary-search", new VizSpec(NUMBERS_16, "^-?\\d{1,3}$")),
          Map.entry("two-pointers", new VizSpec(NUMBERS_16, "^-?\\d{1,4}$")),
          Map.entry("sliding-window", new VizSpec(NUMBERS_16, "^\\d{1,2}$")),
          Map.entry("valid-parentheses", new VizSpec("^[()\\[\\]{}]{1,24}$", null)),
          Map.entry("bfs", new VizSpec("^[0-7]-[0-7](\\s*,\\s*[0-7]-[0-7]){0,15}$", "^[0-7]$")),
          Map.entry("fibonacci-dp", new VizSpec("^\\d{1,2}$", null)));

  static final int MAX_QUESTIONS = 20;
  static final int MAX_PROMPT = 2_000;
  static final int MAX_OPTION = 300;
  static final java.util.regex.Pattern UUID_PATTERN =
      java.util.regex.Pattern.compile(
          "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

  record Block(String type, Map<String, Object> data) {}

  private BlockValidator() {}

  static List<Block> validate(List<? extends Map<String, Object>> raw) {
    if (raw == null) {
      throw invalid("blocks are required");
    }
    if (raw.size() > MAX_BLOCKS) {
      throw invalid("a lesson can have at most " + MAX_BLOCKS + " blocks");
    }
    List<Block> out = new ArrayList<>();
    Set<String> quizIds = new java.util.HashSet<>();
    int i = 0;
    for (Map<String, Object> b : raw) {
      i++;
      if (b == null || !(b.get("type") instanceof String type)) {
        throw invalid("block " + i + ": type is required");
      }
      Object data = b.get("data");
      if (!(data instanceof Map<?, ?> d)) {
        throw invalid("block " + i + ": data is required");
      }
      Map<String, Object> m = new LinkedHashMap<>();
      d.forEach((k, v) -> m.put(String.valueOf(k), v));
      Block block = new Block(type, normalize(i, type, m));
      if (type.equals("quiz") && !quizIds.add((String) block.data().get("quizId"))) {
        throw invalid("block " + i + ": quizId is used by another quiz in this lesson");
      }
      out.add(block);
    }
    return out;
  }

  private static Map<String, Object> normalize(int i, String type, Map<String, Object> d) {
    Map<String, Object> n = new LinkedHashMap<>();
    switch (type) {
      case "markdown" -> {
        only(i, d, Set.of("text"));
        n.put("text", text(i, d, "text", 1, MAX_MARKDOWN, true));
      }
      case "code" -> {
        only(i, d, Set.of("language", "code", "caption"));
        String lang = text(i, d, "language", 1, 20, false);
        if (!LANGUAGES.contains(lang)) {
          throw invalid(
              "block " + i + ": language must be one of " + new java.util.TreeSet<>(LANGUAGES));
        }
        n.put("language", lang);
        n.put("code", text(i, d, "code", 1, MAX_CODE, true));
        if (d.get("caption") != null) {
          n.put("caption", text(i, d, "caption", 1, 200, false));
        }
      }
      case "callout" -> {
        only(i, d, Set.of("kind", "text"));
        String kind = text(i, d, "kind", 1, 20, false);
        if (!CALLOUT_KINDS.contains(kind)) {
          throw invalid(
              "block " + i + ": kind must be one of " + new java.util.TreeSet<>(CALLOUT_KINDS));
        }
        n.put("kind", kind);
        n.put("text", text(i, d, "text", 1, 2_000, true));
      }
      case "quiz" -> {
        only(i, d, Set.of("quizId", "title", "questions"));
        n.put("quizId", uuid(i, "quizId", d.get("quizId")));
        if (d.get("title") != null) {
          n.put("title", text(i, d, "title", 1, 100, false));
        }
        n.put("questions", questions(i, d.get("questions")));
      }
      case "viz" -> {
        only(i, d, Set.of("algorithm", "input", "param"));
        String algorithm = text(i, d, "algorithm", 1, 40, false);
        VizSpec spec = VIZ.get(algorithm);
        if (spec == null) {
          throw invalid("block " + i + ": unknown visualization");
        }
        String input = text(i, d, "input", 1, 120, false).strip();
        if (!java.util.regex.Pattern.matches(spec.inputPattern(), input)) {
          throw invalid("block " + i + ": the input does not fit this visualization");
        }
        n.put("algorithm", algorithm);
        n.put("input", input);
        Object rawParam = d.get("param");
        boolean hasParam = rawParam instanceof String p && !p.isBlank();
        if (spec.paramPattern() != null) {
          String param = text(i, d, "param", 1, 10, false).strip();
          if (!java.util.regex.Pattern.matches(spec.paramPattern(), param)) {
            throw invalid("block " + i + ": the value does not fit this visualization");
          }
          n.put("param", param);
        } else if (hasParam || (rawParam != null && !(rawParam instanceof String))) {
          throw invalid("block " + i + ": this visualization takes no extra value");
        }
      }
      default -> throw invalid("block " + i + ": unknown block type");
    }
    return n;
  }

  // ------------------------------------------------------------------ quiz

  /**
   * A quiz block: 1-20 questions, each single choice, multiple choice or true/false, with an answer
   * key and an optional explanation. The key never leaves the server before an attempt is graded
   * (the reader strips it), and every id is a client-chosen UUID so edits keep their identity.
   */
  private static List<Map<String, Object>> questions(int i, Object raw) {
    if (!(raw instanceof List<?> list) || list.isEmpty() || list.size() > MAX_QUESTIONS) {
      throw invalid("block " + i + ": a quiz needs 1-" + MAX_QUESTIONS + " questions");
    }
    List<Map<String, Object>> out = new ArrayList<>();
    Set<String> questionIds = new java.util.HashSet<>();
    int q = 0;
    for (Object item : list) {
      q++;
      String at = "block " + i + ", question " + q;
      Map<String, Object> d = map(at, item);
      onlyKeys(at, d, Set.of("id", "type", "prompt", "options", "correct", "explanation"));
      Map<String, Object> n = new LinkedHashMap<>();
      String id = uuid(i, "question id", d.get("id"));
      if (!questionIds.add(id)) {
        throw invalid(at + ": duplicate question id");
      }
      n.put("id", id);
      if (!(d.get("type") instanceof String type)
          || !Set.of("single", "multi", "true_false").contains(type)) {
        throw invalid(at + ": type must be single, multi or true_false");
      }
      n.put("type", type);
      n.put("prompt", boundedText(at, "prompt", d.get("prompt"), 1, MAX_PROMPT, true));
      List<Map<String, Object>> options = options(at, type, d.get("options"));
      n.put("options", options);
      Set<String> optionIds = new java.util.LinkedHashSet<>();
      options.forEach(o -> optionIds.add((String) o.get("id")));
      n.put("correct", correct(at, type, d.get("correct"), optionIds));
      if (d.get("explanation") != null) {
        n.put(
            "explanation",
            boundedText(at, "explanation", d.get("explanation"), 1, MAX_PROMPT, true));
      }
      out.add(n);
    }
    return out;
  }

  private static List<Map<String, Object>> options(String at, String type, Object raw) {
    int min = 2;
    int max = type.equals("multi") ? 8 : type.equals("true_false") ? 2 : 6;
    if (!(raw instanceof List<?> list) || list.size() < min || list.size() > max) {
      throw invalid(
          at
              + ": "
              + (type.equals("true_false")
                  ? "a true/false question has exactly 2 options"
                  : "needs " + min + "-" + max + " options"));
    }
    List<Map<String, Object>> out = new ArrayList<>();
    Set<String> ids = new java.util.HashSet<>();
    int k = 0;
    for (Object item : list) {
      k++;
      String where = at + ", option " + k;
      Map<String, Object> d = map(where, item);
      onlyKeys(where, d, Set.of("id", "text"));
      Map<String, Object> o = new LinkedHashMap<>();
      String id = uuid(0, where + " id", d.get("id"));
      if (!ids.add(id)) {
        throw invalid(where + ": duplicate option id");
      }
      o.put("id", id);
      o.put("text", boundedText(where, "text", d.get("text"), 1, MAX_OPTION, false));
      out.add(o);
    }
    return out;
  }

  private static List<String> correct(String at, String type, Object raw, Set<String> optionIds) {
    if (!(raw instanceof List<?> list) || list.isEmpty()) {
      throw invalid(at + ": mark the correct answer");
    }
    Set<String> chosen = new java.util.LinkedHashSet<>();
    for (Object o : list) {
      if (!(o instanceof String id) || !optionIds.contains(id.toLowerCase(java.util.Locale.ROOT))) {
        throw invalid(at + ": the correct answer must be one of the options");
      }
      if (!chosen.add(id.toLowerCase(java.util.Locale.ROOT))) {
        throw invalid(at + ": the same option is marked correct twice");
      }
    }
    if (!type.equals("multi") && chosen.size() != 1) {
      throw invalid(at + ": exactly one option must be correct");
    }
    return new ArrayList<>(chosen);
  }

  private static Map<String, Object> map(String at, Object item) {
    if (!(item instanceof Map<?, ?> m)) {
      throw invalid(at + ": must be an object");
    }
    Map<String, Object> out = new LinkedHashMap<>();
    m.forEach((k, v) -> out.put(String.valueOf(k), v));
    return out;
  }

  private static void onlyKeys(String at, Map<String, Object> d, Set<String> allowed) {
    for (String k : d.keySet()) {
      if (!allowed.contains(k)) {
        throw invalid(
            at + ": unexpected field '" + (k.length() > 30 ? k.substring(0, 30) : k) + "'");
      }
    }
  }

  private static String uuid(int i, String what, Object v) {
    if (!(v instanceof String s)
        || !UUID_PATTERN.matcher(s.toLowerCase(java.util.Locale.ROOT)).matches()) {
      throw invalid((i > 0 ? "block " + i + ": " : "") + what + " must be a UUID");
    }
    return s.toLowerCase(java.util.Locale.ROOT);
  }

  private static String boundedText(
      String at, String key, Object v, int min, int max, boolean multiline) {
    if (!(v instanceof String s) || s.strip().length() < min || s.length() > max) {
      throw invalid(at + ": " + key + " must be " + min + "-" + max + " characters");
    }
    for (int c = 0; c < s.length(); c++) {
      char ch = s.charAt(c);
      boolean ok = ch >= 0x20 || ch == '\t' || (multiline && (ch == '\n' || ch == '\r'));
      if (!ok || ch == 0x7f) {
        throw invalid(at + ": " + key + " contains a control character");
      }
    }
    return s;
  }

  private static void only(int i, Map<String, Object> d, Set<String> allowed) {
    for (String k : d.keySet()) {
      if (!allowed.contains(k)) {
        throw invalid(
            "block "
                + i
                + ": unexpected field '"
                + (k.length() > 30 ? k.substring(0, 30) : k)
                + "'");
      }
    }
  }

  private static String text(
      int i, Map<String, Object> d, String key, int min, int max, boolean multiline) {
    if (!(d.get(key) instanceof String s)) {
      throw invalid("block " + i + ": " + key + " must be text");
    }
    if (s.strip().length() < min || s.length() > max) {
      throw invalid("block " + i + ": " + key + " must be " + min + "-" + max + " characters");
    }
    for (int c = 0; c < s.length(); c++) {
      char ch = s.charAt(c);
      boolean ok = ch >= 0x20 || ch == '\t' || (multiline && (ch == '\n' || ch == '\r'));
      if (!ok || ch == 0x7f) {
        throw invalid("block " + i + ": " + key + " contains a control character");
      }
    }
    return s;
  }

  private static ApiException invalid(String detail) {
    return new ApiException(ErrorCode.VALIDATION_FAILED, detail);
  }
}
