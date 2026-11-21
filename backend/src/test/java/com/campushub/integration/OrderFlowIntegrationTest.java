package com.campushub.integration;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.*;
import java.util.regex.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

/** End to end: register → shop → stock → cart → checkout → pay (twice, to prove idempotency) → READY → remaining payment → OTP → collected. Needs Docker. */
@SpringBootTest(properties = {"app.jwt.secret=test-secret-test-secret-test-secret-1234", "app.otp.secret=otp-secret-otp-secret", "app.payments.webhook-secret=whsec", "app.rate-limit.enabled=false"})
@AutoConfigureMockMvc @Testcontainers
class OrderFlowIntegrationTest {
  @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4").withCommand("--default-time-zone=+00:00");
  @DynamicPropertySource static void db(DynamicPropertyRegistry r){ r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername); r.add("spring.datasource.password", mysql::getPassword); }
  @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper om;

  String call(String method, String url, String token, String body) throws Exception {
    var rb = switch (method) { case "POST" -> post(url); case "PUT" -> put(url); default -> get(url); };
    rb.contentType(MediaType.APPLICATION_JSON); if (token != null) rb.header("Authorization", "Bearer " + token); if (body != null) rb.content(body);
    return mvc.perform(rb).andReturn().getResponse().getContentAsString();
  }
  JsonNode json(String s) throws Exception { return om.readTree(s); }
  @Autowired com.campushub.sms.ConsoleSmsSender sms;
  /** Registers, reads the SMS code the dev sender "sent", verifies it, and returns the access token. */
  String register(String email, String role) throws Exception {
    String phone = "9" + String.format("%09d", Math.abs(email.hashCode()) % 1_000_000_000);
    JsonNode pending = json(call("POST", "/api/auth/register", null, "{\"fullName\":\"Test User\",\"email\":\"" + email + "\",\"password\":\"password123\",\"phone\":\"" + phone + "\",\"role\":\"" + role + "\"}"));
    assertTrue(pending.at("/data/phoneVerificationRequired").asBoolean(), "sign-up asks for the SMS code");
    assertEquals("PHONE_NOT_VERIFIED", json(call("POST", "/api/auth/login", null, "{\"email\":\"" + email + "\",\"password\":\"password123\"}")).at("/data/code").asText());
    Matcher m = Pattern.compile("(\\d{6})").matcher(sms.lastMessage("+91" + phone)); assertTrue(m.find());
    return json(call("POST", "/api/auth/verify-phone", null, "{\"email\":\"" + email + "\",\"otp\":\"" + m.group(1) + "\"}")).at("/data/accessToken").asText();
  }

  @Test void roles_are_enforced_on_the_server() throws Exception {
    String student = register("s1@x.in", "STUDENT");
    mvc.perform(get("/api/shop-owner/orders").header("Authorization", "Bearer " + student)).andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + student)).andExpect(status().isForbidden());
    mvc.perform(get("/api/student/orders")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"A\",\"email\":\"a@x.in\",\"password\":\"password123\",\"phone\":\"9999999999\",\"role\":\"SUPER_ADMIN\"}")).andExpect(status().isBadRequest());
  }

  @Test void full_pickup_flow_with_idempotent_payment() throws Exception {
    String student = register("stu@x.in", "STUDENT"), owner = register("own@x.in", "SHOP_OWNER");
    long shop = json(call("POST", "/api/shop-owner/shop", owner, "{\"vertical\":\"DESK\",\"name\":\"Copy Corner\"}")).at("/data").asLong();
    jdbc.update("INSERT INTO categories(vertical,name) VALUES ('DESK','Stationery')");
    long cat = jdbc.queryForObject("SELECT id FROM categories WHERE name='Stationery'", Long.class);
    assertEquals("SHOP_NOT_APPROVED", json(call("POST", "/api/shop-owner/products", owner, "{\"categoryId\":" + cat + ",\"name\":\"Pen\",\"sellingPrice\":10}")).at("/data/code").asText());   // locked until verified
    jdbc.update("UPDATE shops SET approval='APPROVED' WHERE id=?", shop);                                                                                                                  // admin approval
    mvc.perform(patch("/api/shop-owner/shop/status").header("Authorization", "Bearer " + owner).contentType(MediaType.APPLICATION_JSON).content("{\"openStatus\":\"OPEN\"}")).andExpect(status().isOk());
    long pen = json(call("POST", "/api/shop-owner/products", owner, "{\"categoryId\":" + cat + ",\"name\":\"Pen\",\"sellingPrice\":10,\"initialStock\":100,\"minStock\":5}")).at("/data").asLong();

    // the client sends only ids and quantities; the server prices everything
    call("POST", "/api/student/cart/items", student, "{\"shopId\":" + shop + ",\"productId\":" + pen + ",\"quantity\":3}");
    JsonNode co = json(call("POST", "/api/student/orders/checkout", student, "{\"method\":\"PHONEPE\"}"));
    assertEquals(0, co.at("/data/total").decimalValue().compareTo(new java.math.BigDecimal("30")));
    assertEquals(0, co.at("/data/advanceAmount").decimalValue().compareTo(new java.math.BigDecimal("15")));
    long orderId = co.at("/data/orderId").asLong(); String txn = co.at("/data/payment/transactionId").asText();
    assertEquals(100, jdbc.queryForObject("SELECT quantity FROM inventory WHERE product_id=?", Integer.class, pen));   // not deducted before payment

    for (int i = 0; i < 2; i++) mvc.perform(post("/api/payments/sandbox/" + txn + "/succeed").header("Authorization", "Bearer " + student)).andExpect(status().isOk());
    assertEquals(97, jdbc.queryForObject("SELECT quantity FROM inventory WHERE product_id=?", Integer.class, pen));      // deducted exactly once
    assertEquals("PLACED", jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, orderId));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE order_id=? AND status='SUCCESS'", Integer.class, orderId));

    for (String s : new String[]{"ACCEPTED", "PROCESSING", "READY"}) call("POST", "/api/shop-owner/orders/" + orderId + "/status", owner, "{\"status\":\"" + s + "\"}");
    String body = jdbc.queryForObject("SELECT body FROM notifications WHERE order_id=? AND title LIKE '%ready for pickup%'", String.class, orderId);
    Matcher m = Pattern.compile("OTP: (\\d{6})").matcher(body); assertTrue(m.find()); String otp = m.group(1);

    // collecting before the remaining payment must fail, and a wrong OTP must not collect
    assertTrue(json(call("POST", "/api/shop-owner/orders/" + orderId + "/verify-pickup", owner, "{\"otp\":\"" + otp + "\"}")).at("/data/code").asText().equals("REMAINING_PAYMENT_PENDING"));
    String rem = json(call("POST", "/api/student/orders/" + orderId + "/pay", student, "{\"method\":\"GOOGLE_PAY\"}")).at("/data/transactionId").asText();
    mvc.perform(post("/api/payments/sandbox/" + rem + "/succeed").header("Authorization", "Bearer " + student)).andExpect(status().isOk());
    assertEquals("INVALID_OTP", json(call("POST", "/api/shop-owner/orders/" + orderId + "/verify-pickup", owner, "{\"otp\":\"" + (otp.equals("000000") ? "111111" : "000000") + "\"}")).at("/data/code").asText());
    mvc.perform(post("/api/shop-owner/orders/" + orderId + "/verify-pickup").header("Authorization", "Bearer " + owner).contentType(MediaType.APPLICATION_JSON).content("{\"otp\":\"" + otp + "\"}")).andExpect(status().isOk());
    assertEquals("COLLECTED", jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, orderId));
    mvc.perform(post("/api/shop-owner/orders/" + orderId + "/verify-pickup").header("Authorization", "Bearer " + owner).contentType(MediaType.APPLICATION_JSON).content("{\"otp\":\"" + otp + "\"}")).andExpect(status().isConflict());   // OTP can't be reused
  }
}
