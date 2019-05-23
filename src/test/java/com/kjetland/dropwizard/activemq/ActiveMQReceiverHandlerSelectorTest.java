package com.kjetland.dropwizard.activemq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.jms.pool.PooledConnectionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.jms.TextMessage;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class ActiveMQReceiverHandlerSelectorTest {

    final String url = "tcp://localhost:31219?" +
        "jms.redeliveryPolicy.maximumRedeliveries=3" +
        "&jms.redeliveryPolicy.initialRedeliveryDelay=100" +
        "&jms.redeliveryPolicy.redeliveryDelay=100";

    final String DESTINATION = "somewherenothere";
    final String DESTINATION_QUEUE = "queue:" + DESTINATION;

    int receivedCount;
    int errorCount;
    BrokerService broker;

    @Before
    public void setUp() throws Exception {
        broker = new BrokerService();
        // configure the broker
        broker.addConnector(url);
        broker.start();

        errorCount = 0;
        receivedCount = 0;
    }

    @After
    public void tearDown() throws Exception {
        broker.stop();
        // Just give the broker some time to stop
        Thread.sleep(1500);
    }

    private void receiveMessage(String message, Map<String, Object> messageProperties) {
        receivedCount++;
    }

    public boolean exceptionHandler(String message, Exception exception) {
        errorCount++;
        return false;
    }

    @Test
    public void testMessageSelector() throws Exception {
        ActiveMQConnectionFactory realConnectionFactory = new ActiveMQConnectionFactory(url);
        PooledConnectionFactory connectionFactory = new PooledConnectionFactory();
        connectionFactory.setConnectionFactory(realConnectionFactory);

        ObjectMapper objectMapper = new ObjectMapper();

        ActiveMQReceiverHandler<String> h = new ActiveMQReceiverHandler<>(
            DESTINATION_QUEUE,
            connectionFactory,
            this::receiveMessage,
            String.class,
            objectMapper,
            this::exceptionHandler,
            1,
            "destination = 'server-a'");

        h.start();

        ActiveMQSender sender = new ActiveMQSenderImpl(connectionFactory, objectMapper, DESTINATION_QUEUE, Optional.empty(), false);

        sender.send(session -> {
            TextMessage textMessage = session.createTextMessage();
            textMessage.setText("Consume me!");
            textMessage.setStringProperty("destination", "server-a");
            return textMessage;
        });

        sender.send(session -> {
            TextMessage textMessage = session.createTextMessage();
            textMessage.setText("But not me...");
            textMessage.setStringProperty("destination", "server-b");
            return textMessage;
        });

        Thread.sleep(1000);

        assertEquals(receivedCount, 1);
        assertEquals(errorCount, 0);
        //Check to see that the broker has 1 message waiting
        assertEquals(broker.getDestination(ActiveMQDestination.createDestination(DESTINATION, (byte) 0x01)).browse().length, 1);
    }
}
