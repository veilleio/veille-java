package io.veille.examples;

import io.veille.ApiResponse;
import io.veille.VeilleClient;
import io.veille.params.IntelligenceEmailParams;
import java.time.Duration;

/** Run with VEILLE_API_KEY set. */
public final class WaitForJobExample {
  private WaitForJobExample() {}

  public static void main(String[] args) throws Exception {
    VeilleClient client = VeilleClient.fromEnv();
    ApiResponse submitted = client.intelligenceEmail(IntelligenceEmailParams.builder().query("jane.doe@example.com").isAsync(true).build());
    ApiResponse job = client.waitForJob(
        submitted.json().get("job_id").asText(),
        Duration.ofSeconds(3),
        Duration.ofHours(12),
        progress -> System.out.println(progress.path("status").asText() + " " + progress.path("progress").asInt()));
    System.out.println(job.json().toPrettyString());
  }
}
