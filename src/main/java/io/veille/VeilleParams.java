package io.veille;

import java.util.Map;

/** Parameters of one endpoint, in wire order. Implemented by every class in {@code io.veille.params}. */
public interface VeilleParams {
  /** Wire name to value; null values are omitted from requests. */
  Map<String, Object> toMap();
}
