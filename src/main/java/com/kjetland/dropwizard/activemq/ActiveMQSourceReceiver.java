package com.kjetland.dropwizard.activemq;

public interface ActiveMQSourceReceiver<T> extends ActiveMQBaseReceiver {

    void receive(T message, String source);

    default void processMessage(T message, String source) {
        receive(message);
    }
}
