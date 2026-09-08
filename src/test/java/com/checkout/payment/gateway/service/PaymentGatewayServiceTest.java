package com.checkout.payment.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.BankAuthorizationResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.checkout.payment.gateway.validation.PaymentRequestValidator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentGatewayServiceTest {

  private PaymentsRepository paymentsRepository;
  private PaymentRequestValidator validator;
  private BankClient bankClient;
  private PaymentGatewayService service;

  @BeforeEach
  void setUp() {
    paymentsRepository = mock(PaymentsRepository.class);
    validator = mock(PaymentRequestValidator.class);
    bankClient = mock(BankClient.class);
    service = new PaymentGatewayService(paymentsRepository, validator, bankClient);
  }

  private PostPaymentRequest sampleRequest() {
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
  void invalidRequestIsRejectedWithoutCallingTheBankOrSaving() {
    List<String> reasons = List.of("cardNumber is required");
    when(validator.validate(any())).thenReturn(reasons);

    PostPaymentResponse response = service.processPayment(sampleRequest());

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.REJECTED);
    assertThat(response.getRejectionReasons()).isEqualTo(reasons);
    verify(bankClient, never()).authorize(any());
    verify(paymentsRepository, never()).add(any());
  }

  @Test
  void declinedPaymentDoesNotExposeAnyRejectionReasons() {
    // Deliberate distinction from a REJECTED response: decline reasons come
    // from the bank, not our own validation, and are not surfaced to the
    // caller - see the comment on PostPaymentResponse.rejectionReasons.
    when(validator.validate(any())).thenReturn(List.of());
    BankAuthorizationResponse bankResponse = new BankAuthorizationResponse();
    bankResponse.setAuthorized(false);
    when(bankClient.authorize(any())).thenReturn(bankResponse);

    PostPaymentResponse response = service.processPayment(sampleRequest());

    assertThat(response.getRejectionReasons()).isNull();
  }

  @Test
  void authorizedPaymentIsSavedToTheRepository() {
    when(validator.validate(any())).thenReturn(List.of());
    BankAuthorizationResponse bankResponse = new BankAuthorizationResponse();
    bankResponse.setAuthorized(true);
    when(bankClient.authorize(any())).thenReturn(bankResponse);

    PostPaymentResponse response = service.processPayment(sampleRequest());

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    verify(paymentsRepository).add(response);
  }

  @Test
  void declinedPaymentIsSavedToTheRepository() {
    when(validator.validate(any())).thenReturn(List.of());
    BankAuthorizationResponse bankResponse = new BankAuthorizationResponse();
    bankResponse.setAuthorized(false);
    when(bankClient.authorize(any())).thenReturn(bankResponse);

    PostPaymentResponse response = service.processPayment(sampleRequest());

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.DECLINED);
    verify(paymentsRepository).add(response);
  }

  @Test
  void rejectedPaymentIsNeverSavedToTheRepository() {
    // Distinct from the "never calls the bank" test above - this confirms
    // the repository interaction specifically, matching the README's "no
    // payment could be created" wording for a Rejected outcome.
    when(validator.validate(any())).thenReturn(List.of("amount must be a positive integer"));

    service.processPayment(sampleRequest());

    verify(paymentsRepository, never()).add(any());
  }

  @Test
  void bankUnavailableExceptionPropagatesAndNothingIsSaved() {
    when(validator.validate(any())).thenReturn(List.of());
    when(bankClient.authorize(any()))
        .thenThrow(new BankUnavailableException("simulated failure", new RuntimeException()));

    assertThatThrownBy(() -> service.processPayment(sampleRequest()))
        .isInstanceOf(BankUnavailableException.class);
    verify(paymentsRepository, never()).add(any());
  }

  @Test
  void getPaymentByIdReturnsTheStoredPayment() {
    UUID id = UUID.randomUUID();
    PostPaymentResponse stored = new PostPaymentResponse();
    stored.setId(id);
    stored.setStatus(PaymentStatus.AUTHORIZED);
    when(paymentsRepository.get(id)).thenReturn(Optional.of(stored));

    PostPaymentResponse result = service.getPaymentById(id);

    assertThat(result).isEqualTo(stored);
  }

  @Test
  void getPaymentByIdThrowsWhenNotFound() {
    UUID id = UUID.randomUUID();
    when(paymentsRepository.get(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getPaymentById(id))
        .isInstanceOf(EventProcessingException.class);
  }
}