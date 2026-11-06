package io.veille.examples;

import io.veille.ApiResponse;
import io.veille.VeilleApiException;
import io.veille.VeilleClient;
import java.util.Map;

/** Run with VEILLE_API_KEY set. */
public final class RequestExample {
  private RequestExample() {}

  public static void main(String[] args) throws Exception {
    VeilleClient client = VeilleClient.fromEnv();
    try {
      ApiResponse response = client.request("GET", "/v1/usage", Map.of());
      System.out.println(response.text());
    } catch (VeilleApiException e) {
      System.err.println("HTTP " + e.status() + ": " + e.getMessage());
    }
  }
}
