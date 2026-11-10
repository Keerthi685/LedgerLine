package com.ledgerline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class LedgerLineIntegrityIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
        .withDatabaseName("ledgerline_test")
        .withUsername("test")
        .withPassword("testpass");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    String user, pass = "TestPass@12345";

    @BeforeEach void createUser() throws Exception {
        user = "holder_" + UUID.randomUUID().toString().substring(0, 8);
        String body = mapper.writeValueAsString(java.util.Map.of(
            "username", user, "email", user + "@example.test", "password", pass));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
    }

    String createAccount() throws Exception {
        String response = mvc.perform(post("/api/accounts").with(httpBasic(user, pass)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("accountNumber").asText();
    }

    void deposit(String account, String key, String amount) throws Exception {
        mvc.perform(post("/api/transactions/deposit/" + account).with(httpBasic(user, pass))
            .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":" + amount + "}"))
            .andExpect(status().isOk());
    }

    @Test void replayedDepositMovesMoneyOnceAndReturnsSameReference() throws Exception {
        String account = createAccount();
        String body = "{\"amount\":500.00}";
        String key = "fixed-" + UUID.randomUUID();
        String first = mvc.perform(post("/api/transactions/deposit/" + account).with(httpBasic(user, pass))
            .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/api/transactions/deposit/" + account).with(httpBasic(user, pass))
            .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(mapper.readTree(first).get("reference").asText(), mapper.readTree(second).get("reference").asText());
        String accounts = mvc.perform(get("/api/accounts/my").with(httpBasic(user, pass)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("500.00"), new BigDecimal(mapper.readTree(accounts).get(0).get("balance").asText()));
    }

    @Test void concurrentWithdrawalsCannotOverdraw() throws Exception {
        String account = createAccount();
        deposit(account, "seed-" + UUID.randomUUID(), "100.00");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> withdraw = () -> {
            start.await();
            return mvc.perform(post("/api/transactions/withdraw/" + account).with(httpBasic(user, pass))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":80.00}"))
                .andReturn().getResponse().getContentAsString();
        };
        Future<String> a = pool.submit(withdraw), b = pool.submit(withdraw);
        start.countDown();
        JsonNode ra = mapper.readTree(a.get(20, TimeUnit.SECONDS));
        JsonNode rb = mapper.readTree(b.get(20, TimeUnit.SECONDS));
        pool.shutdownNow();
        int successes = ("SUCCESS".equals(ra.get("status").asText()) ? 1 : 0) + ("SUCCESS".equals(rb.get("status").asText()) ? 1 : 0);
        org.junit.jupiter.api.Assertions.assertEquals(1, successes);
        String accounts = mvc.perform(get("/api/accounts/my").with(httpBasic(user, pass)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        BigDecimal balance = new BigDecimal(mapper.readTree(accounts).get(0).get("balance").asText());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("20.00"), balance);
    }

    @Test void sameIdempotencyKeyWithDifferentPayloadConflicts() throws Exception {
        String account = createAccount();
        String key = "conflict-" + UUID.randomUUID();
        deposit(account, key, "10.00");
        mvc.perform(post("/api/transactions/deposit/" + account).with(httpBasic(user, pass))
            .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":11.00}"))
            .andExpect(status().isConflict());
    }

    @Test void transferConservesMoneyAndCreatesTwoStatementPostings() throws Exception {
        String source = createAccount();
        String destination = createAccount();
        deposit(source, "fund-" + UUID.randomUUID(), "250.00");
        mvc.perform(post("/api/transactions/transfer/" + source).with(httpBasic(user, pass))
            .header("Idempotency-Key", "transfer-" + UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"destinationAccount\":\"" + destination + "\",\"amount\":75.00}"))
            .andExpect(status().isOk());
        JsonNode accounts = mapper.readTree(mvc.perform(get("/api/accounts/my").with(httpBasic(user, pass)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        BigDecimal total = BigDecimal.ZERO;
        for (JsonNode account : accounts) total = total.add(new BigDecimal(account.get("balance").asText()));
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("250.00"), total);
        String statement = mvc.perform(get("/api/accounts/" + source + "/statement").with(httpBasic(user, pass)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(mapper.readTree(statement).size() >= 2);
    }

    @Test void anotherHolderCannotMoveFundsFromSomeoneElsesAccount() throws Exception {
        String source = createAccount();
        deposit(source, "owner-fund-" + UUID.randomUUID(), "100.00");
        String other = "other_" + UUID.randomUUID().toString().substring(0, 8);
        String otherBody = mapper.writeValueAsString(java.util.Map.of(
            "username", other, "email", other + "@example.test", "password", pass));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(otherBody))
            .andExpect(status().isCreated());
        String destination = mvc.perform(post("/api/accounts").with(httpBasic(other, pass)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/transactions/transfer/" + source).with(httpBasic(other, pass))
            .header("Idempotency-Key", "unauthorized-" + UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"destinationAccount\":\"" + mapper.readTree(destination).get("accountNumber").asText() + "\",\"amount\":10.00}"))
            .andExpect(status().isForbidden());
    }
}
