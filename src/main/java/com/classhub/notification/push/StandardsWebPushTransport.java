package com.classhub.notification.push;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;

@Component
public class StandardsWebPushTransport implements WebPushTransport {

    private static final int TTL_SECONDS = 3_600;

    public StandardsWebPushTransport() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Override
    public WebPushTransportResponse send(WebPushTransportRequest request)
            throws WebPushTransportException {
        try {
            PushService pushService = new PushService(
                    request.vapidPublicKey(), request.vapidPrivateKey(), request.vapidSubject());
            Notification notification = new Notification(
                    request.endpoint(),
                    request.p256dh(),
                    request.auth(),
                    request.payload().getBytes(StandardCharsets.UTF_8),
                    TTL_SECONDS);
            HttpResponse response = pushService.send(notification);
            Header location = response.getFirstHeader("Location");
            return new WebPushTransportResponse(
                    response.getStatusLine().getStatusCode(),
                    location == null ? null : location.getValue());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new WebPushTransportException("Web Push request was interrupted", ex);
        } catch (Exception ex) {
            throw new WebPushTransportException("Web Push request failed", ex);
        }
    }
}
