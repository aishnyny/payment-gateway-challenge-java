package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.BankAuthorizationResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.checkout.payment.gateway.validation.PaymentRequestValidator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final PaymentsRepository paymentsRepository;
  private final PaymentRequestValidator validator;
  private final BankClient bankClient;

  public PaymentGatewayService(PaymentsRepository paymentsRepository,
      PaymentRequestValidator validator, BankClient bankClient) {
    this.paymentsRepository = paymentsRepository;
    this.validator = validator;
    this.bankClient = bankClient;
  }

  public PostPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to to payment with ID {}", id);
    return paymentsRepository.get(id).orElseThrow(() -> new EventProcessingException("Invalid ID"));
  }

  public PostPaymentResponse processPayment(PostPaymentRequest request) {
    // Note: request.toString() deliberately omits the card number and CVV -
    // see PostPaymentRequest - so this log line is safe.
    LOG.info("Processing payment request: {}", request);

    List<String> validationErrors = validator.validate(request);
    if (!validationErrors.isEmpty()) {
      LOG.info("Payment rejected due to validation errors: {}", validationErrors);
      return buildResponse(request, PaymentStatus.REJECTED);
    }

    // Design decision: a bank-unavailable failure (BankUnavailableException)
    // is deliberately left to propagate up rather than caught here and
    // silently turned into a REJECTED or DECLINED response. Those two
    // statuses represent definite outcomes; "the bank didn't respond" is a
    // different kind of failure and merchants should be able to tell the
    // difference (e.g. to know whether it's safe to retry).
    BankAuthorizationResponse bankResponse = bankClient.authorize(request);

    PaymentStatus status = bankResponse.isAuthorized()
        ? PaymentStatus.AUTHORIZED
        : PaymentStatus.DECLINED;

    PostPaymentResponse response = buildResponse(request, status);
    paymentsRepository.add(response);
    LOG.info("Payment {} processed with status {}", response.getId(), status);
    return response;
  }

  private PostPaymentResponse buildResponse(PostPaymentRequest request, PaymentStatus status) {
    PostPaymentResponse response = new PostPaymentResponse();
    response.setId(UUID.randomUUID());
    response.setStatus(status);
    // Uses a safe, non-throwing extraction here deliberately: a REJECTED
    // response can be caused by an invalid card number itself, so we can't
    // assume getLastFourCardDigits() is safe to call in this path the way
    // it is once validation has already passed.
    response.setCardNumberLastFour(safeLastFourDigits(request));
    response.setExpiryMonth(request.getExpiryMonth());
    response.setExpiryYear(request.getExpiryYear());
    response.setCurrency(request.getCurrency());
    response.setAmount(request.getAmount());
    return response;
  }

  private int safeLastFourDigits(PostPaymentRequest request) {
    String cardNumber = request.getCardNumber();
    if (cardNumber == null || cardNumber.length() < 4
        || !cardNumber.substring(cardNumber.length() - 4).matches("\\d{4}")) {
      // The card number itself was part of why this request was rejected -
      // there's nothing meaningful to show here, so we return 0 rather than
      // propagate an exception out of response building.
      return 0;
    }
    return Integer.parseInt(cardNumber.substring(cardNumber.length() - 4));
  }
}