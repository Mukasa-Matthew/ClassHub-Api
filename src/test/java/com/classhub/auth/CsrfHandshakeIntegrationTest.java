package com.classhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.classhub.common.api.ErrorCodes;
import com.classhub.support.PostgresTestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "classhub.test.csrf-handshake=true")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class CsrfHandshakeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void responseTokenAndCookieAuthorizeTheRealBrowserLoginHandshake() throws Exception {
        MvcResult bootstrap = bootstrap();
        Cookie cookie = requiredCookie(bootstrap);
        String responseToken = JsonPath.read(bootstrap.getResponse().getContentAsString(), "$.data.token");
        String headerName = JsonPath.read(bootstrap.getResponse().getContentAsString(), "$.data.headerName");

        mockMvc.perform(post("/api/v1/auth/login")
                        .header(headerName, responseToken)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"missing@example.com","password":"NotThePassword1!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.AUTHENTICATION_FAILED));
    }

    @Test
    void matchingCookieRejectsAnIncorrectHeaderToken() throws Exception {
        Cookie cookie = requiredCookie(bootstrap());

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-XSRF-TOKEN", "incorrect-token")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"missing@example.com","password":"NotThePassword1!"}
                                """))
                .andExpect(status().isForbidden());
    }

    private MvcResult bootstrap() throws Exception {
        return mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.headerName").value("X-XSRF-TOKEN"))
                .andReturn();
    }

    private Cookie requiredCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return cookie;
    }
}
