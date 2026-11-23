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
      out.add(new Block(type, normalize(i, type, m)));
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
      default -> throw invalid("block " + i + ": unknown block type");
    }
    return n;
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
