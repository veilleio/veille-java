package io.veille;

/** Base class for every SDK-defined exception. */
public class VeilleException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /** Creates the exception. */
  public VeilleException(String message, Throwable cause) {
    super(message, cause);
  }
}
