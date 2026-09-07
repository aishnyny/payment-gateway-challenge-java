package com.checkout.payment.gateway.controller;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.BankAuthorizationResponse;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {

  @Autowired
  private MockMvc mvc;
  @Autowired
  PaymentsRepository paymentsRepository;

  // The real BankClient calls out over HTTP to the simulator. Mocking it
  // here means these tests are fast, deterministic, and don't need the
  // simulator running - the simulator's own behaviour is exercised
  // separately via manual end-to-end testing.
  @MockBean
  private BankClient bankClient;

  private static final String VALID_PAYMENT_JSON = """
      {
        "card_number": "2222405343248877",
        "expiry_month": 12,
        "expiry_year": 2027,
        "currency": "GBP",
        "amount": 1000,
        "cvv": "123"
      }
      """;

  @Test
  void whenPaymentWithIdExistThenCorrectPaymentIsReturned() throws Exception {
    PostPaymentResponse payment = new PostPaymentResponse();
    payment.setId(UUID.randomUUID());
    payment.setAmount(10);
    payment.setCurrency("USD");
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setExpiryMonth(12);
    payment.setExpiryYear(2024);
    payment.setCardNumberLastFour(4321);

    paymentsRepository.add(payment);

    mvc.perform(MockMvcRequestBuilders.get("/payments/" + payment.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(payment.getStatus().getName()))
        .andExpect(jsonPath("$.cardNumberLastFour").value(payment.getCardNumberLastFour()))
        .andExpect(jsonPath("$.expiryMonth").value(payment.getExpiryMonth()))
        .andExpect(jsonPath("$.expiryYear").value(payment.getExpiryYear()))
        .andExpect(jsonPath("$.currency").value(payment.getCurrency()))
        .andExpect(jsonPath("$.amount").value(payment.getAmount()));
  }

  @Test
  void whenPaymentWithIdDoesNotExistThen404IsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/payments/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Page not found"));
  }

  @Test
  void whenBankAuthorizesThenPaymentIsCreatedAsAuthorized() throws Exception {
    BankAuthorizationResponse bankResponse = new BankAuthorizationResponse();
    bankResponse.setAuthorized(true);
    bankResponse.setAuthorizationCode(UUID.randomUUID().toString());
    when(bankClient.authorize(any())).thenReturn(bankResponse);

    mvc.perform(MockMvcRequestBuilders.post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_PAYMENT_JSON))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value(8877));
  }

  @Test
  void whenBankDeclinesThenPaymentIsCreatedAsDeclined() throws Exception {
    BankAuthorizationResponse bankResponse = new BankAuthorizationResponse();
    bankResponse.setAuthorized(false);
    bankResponse.setAuthorizationCode("");
    when(bankClient.authorize(any())).thenReturn(bankResponse);

    mvc.perform(MockMvcRequestBuilders.post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_PAYMENT_JSON))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Declined"));
  }

  @Test
  void whenRequestIsInvalidThenPaymentIsRejectedWithoutCallingTheBank() throws Exception {
    String invalidPaymentJson = """
        {
          "card_number": "123",
          "expiry_month": 12,
          "expiry_year": 2027,
          "currency": "GBP",
          "amount": 1000,
          "cvv": "123"
        }
        """;

    mvc.perform(MockMvcRequestBuilders.post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(invalidPaymentJson))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Rejected"));

    // Confirms the gateway short-circuits on invalid input rather than
    // wastefully calling out to the bank with data we already know is bad.
    verify(bankClient, never()).authorize(any());
  }

  @Test
  void whenBankIsUnavailableThen503IsReturned() throws Exception {
    when(bankClient.authorize(any()))
        .thenThrow(new BankUnavailableException("simulated failure", new RuntimeException()));

    mvc.perform(MockMvcRequestBuilders.post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_PAYMENT_JSON))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.message").value("Unable to process payment: acquiring bank unavailable"));
  }
}