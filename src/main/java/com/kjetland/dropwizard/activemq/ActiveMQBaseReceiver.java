package com.kjetland.dropwizard.activemq;

public interface ActiveMQBaseReceiver<T> {

    public void receive(T message);
}
