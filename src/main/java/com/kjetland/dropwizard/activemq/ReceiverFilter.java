package com.kjetland.dropwizard.activemq;

import javax.jms.Message;

/**
 * A filter used upon receiving a JMS message
 */
public interface ReceiverFilter<T> {

    /**
     * Called upon receiving the message and before it its processed
     * @param message The JMS message received
     */
    T apply(Message message);

    /**
     * Called after message has been processed
     */
    void after(T context);
}
