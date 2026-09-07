package com.checkout.payment.gateway.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.BankAuthorizationResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

class BankClientTest {

  private RestTemplate restTemplate;
  private BankClient bankClient;

  @BeforeEach
  void setUp() {
    restTemplate = mock(RestTemplate.class);
    bankClient = new BankClient(restTemplate, "http://localhost:8080");
  }

  private PostPaymentRequest samplePaymentRequest() {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("2222405343248877");
    request.setExpiryMonth(12);
    request.setExpiryYear(2027);
    request.setCurrency("GBP");
    request.setAmount(1000);
    request.setCvv("123");
    return request;
  }

  @Test
  void authorizedResponseIsPassedThrough() {
    BankAuthorizationResponse mockResponse = new BankAuthorizationResponse();
    mockResponse.setAuthorized(true);
    mockResponse.setAuthorizationCode("some-auth-code");
    when(restTemplate.postForObject(anyString(), any(), eq(BankAuthorizationResponse.class)))
        .thenReturn(mockResponse);

    BankAuthorizationResponse result = bankClient.authorize(samplePaymentRequest());

    assertThat(result.isAuthorized()).isTrue();
    assertThat(result.getAuthorizationCode()).isEqualTo("some-auth-code");
  }

  @Test
  void callsTheCorrectSimulatorUrl() {
    BankAuthorizationResponse mockResponse = new BankAuthorizationResponse();
    mockResponse.setAuthorized(true);
    when(restTemplate.postForObject(anyString(), any(), eq(BankAuthorizationResponse.class)))
        .thenReturn(mockResponse);

    bankClient.authorize(samplePaymentRequest());

    // Confirms the configurable base URL is actually used, not hardcoded
    // somewhere else in the class.
    verify(restTemplate).postForObject(eq("http://localhost:8080/payments"), any(),
        eq(BankAuthorizationResponse.class));
  }

  @Test
  void bankFailureIsTranslatedToBankUnavailableException() {
    when(restTemplate.postForObject(anyString(), any(), eq(BankAuthorizationResponse.class)))
        .thenThrow(new ResourceAccessException("simulated connection failure"));

    assertThatThrownBy(() -> bankClient.authorize(samplePaymentRequest()))
        .isInstanceOf(BankUnavailableException.class);
  }
}