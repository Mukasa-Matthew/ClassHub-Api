package com.classhub.notification.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.classhub.common.api.ApiResponse;
import com.classhub.notification.config.NotificationProperties;
import org.junit.jupiter.api.Test;

class WebPushPublicConfigurationControllerTest {

    @Test
    void disabledOrIncompleteConfigurationIsReportedAsUnavailable() {
        NotificationProperties properties = new NotificationProperties();
        properties.getPush().setVapidPublicKey("public-key-must-not-be-returned-while-disabled");
        WebPushPublicConfigurationController controller =
                new WebPushPublicConfigurationController(properties);

        ApiResponse<WebPushPublicConfigurationResponse> response = controller.getConfiguration();

        assertThat(response.data().available()).isFalse();
        assertThat(response.data().vapidPublicKey()).isNull();

        properties.getPush().setEnabled(true);
        response = controller.getConfiguration();
        assertThat(response.data().available()).isFalse();
        assertThat(response.data().vapidPublicKey()).isNull();
    }
}
