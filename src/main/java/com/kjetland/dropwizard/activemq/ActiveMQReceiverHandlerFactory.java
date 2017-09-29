package com.kjetland.dropwizard.activemq;

public interface ActiveMQReceiverHandlerFactory {

    <T> ActiveMQReceiverHandler<T> createActiveMQReceiverHandler(String destination, ActiveMQReceiver<T> receiver, Class<? extends T> clazz, boolean ackMessageOnException);

    <T> ActiveMQReceiverHandler<T> createActiveMQReceiverHandler(String destination, ActiveMQReceiver<T> receiver, Class<? extends T> clazz, ActiveMQBaseExceptionHandler exceptionHandler);
}
