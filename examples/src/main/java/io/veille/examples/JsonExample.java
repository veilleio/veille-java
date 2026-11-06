package io.veille.examples;

import io.veille.ApiResponse;
import io.veille.VeilleClient;
import io.veille.params.IntelligenceEmailParams;

/** Run with VEILLE_API_KEY set. */
public final class JsonExample {
  private JsonExample() {}

  public static void main(String[] args) throws Exception {
    VeilleClient client = VeilleClient.fromEnv();
    ApiResponse response = client.intelligenceEmail(IntelligenceEmailParams.builder().query("jane.doe@example.com").build());
    System.out.println(response.json().toPrettyString());
  }
}
