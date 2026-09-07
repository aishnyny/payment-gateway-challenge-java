package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.BankAuthorizationResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Responsible only for calling the acquiring bank simulator and translating
 * its response - deliberately has no validation or business logic of its
 * own, so it can be tested and reasoned about in isolation.
 */
@Component
public class BankClient {

  private static final Logger LOG = LoggerFactory.getLogger(BankClient.class);

  private final RestTemplate restTemplate;
  private final String bankSimulatorBaseUrl;

  public BankClient(RestTemplate restTemplate,
      @Value("${bank.simulator.base-url}") String bankSimulatorBaseUrl) {
    this.restTemplate = restTemplate;
    this.bankSimulatorBaseUrl = bankSimulatorBaseUrl;
  }

  public BankAuthorizationResponse authorize(PostPaymentRequest request) {
    // Full card number and CVV are sent to the bank here, and nowhere else -
    // this is the one place in the system that legitimately needs them.
    try {
      return restTemplate.postForObject(
          bankSimulatorBaseUrl + "/payments",
          request,
          BankAuthorizationResponse.class);
    } catch (RestClientException ex) {
      // Covers both the simulator's 503 (card ending in 0) and genuine
      // network failures - from the gateway's perspective both mean the
      // same thing: we could not get a usable answer from the bank.
      LOG.error("Bank simulator call failed", ex);
      throw new BankUnavailableException("Unable to reach the acquiring bank", ex);
    }
  }
}