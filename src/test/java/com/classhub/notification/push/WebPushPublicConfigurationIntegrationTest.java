package com.classhub.notification.push;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.classhub.support.PostgresTestcontainersConfiguration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "classhub.notifications.push.enabled=true",
        "classhub.notifications.push.vapid-public-key=test-public-vapid-key",
        "classhub.notifications.push.vapid-private-key=test-private-vapid-key",
        "classhub.notifications.push.subject=mailto:push@classhub.test"
})
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class WebPushPublicConfigurationIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void publicEndpointExposesOnlyAvailabilityAndPublicKeyWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/public/push-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.vapidPublicKey").value("test-public-vapid-key"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("test-private-vapid-key"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("vapidPrivateKey"))));
    }
}
