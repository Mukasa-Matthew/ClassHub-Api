package com.classhub.notification.push;

public interface WebPushTransport {

    WebPushTransportResponse send(WebPushTransportRequest request) throws WebPushTransportException;
}
