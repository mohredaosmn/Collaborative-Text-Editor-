package com.jetbrains.marco.photoz.clone.common;

import com.google.gson.Gson;

public class JSONUtils {
  private static final Gson G = new Gson();

  public static <T> T fromJson(String s, Class<T> cls) {
    return G.fromJson(s, cls);
  }

  public static String toJson(Object o) {
    return G.toJson(o);
  }
}
