package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the response body returned by the acquiring bank simulator.
 * authorizationCode is empty (not null) when a payment is declined - see
 * bank_simulator.ejs, which returns "" rather than omitting the field.
 */
public class BankAuthorizationResponse {

  private boolean authorized;
  @JsonProperty("authorization_code")
  private String authorizationCode;

  public boolean isAuthorized() {
    return authorized;
  }

  public void setAuthorized(boolean authorized) {
    this.authorized = authorized;
  }

  public String getAuthorizationCode() {
    return authorizationCode;
  }

  public void setAuthorizationCode(String authorizationCode) {
    this.authorizationCode = authorizationCode;
  }
}