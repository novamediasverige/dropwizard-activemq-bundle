package com.kjetland.dropwizard.activemq;

import java.util.Map;

public interface ActiveMQReceiver<T> {

    void receive(T message, Map<String, Object> messageProperties);
}
