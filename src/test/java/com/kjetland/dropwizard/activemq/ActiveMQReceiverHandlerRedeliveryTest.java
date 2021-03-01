package com.kjetland.dropwizard.activemq;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.jms.pool.PooledConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ActiveMQReceiverHandlerRedeliveryTest {

    private static final String url = "tcp://localhost:31219?" +
        "jms.redeliveryPolicy.maximumRedeliveries=3" +
        "&jms.redeliveryPolicy.initialRedeliveryDelay=100" +
        "&jms.redeliveryPolicy.redeliveryDelay=100";

    private static final ObjectMapper objectMapper = Jackson.newObjectMapper();

    private BrokerService broker;
    private int errorCount;
    private int okCount;

    @BeforeEach
    void setUp() throws Exception {
        broker = new BrokerService();
        // configure the broker
        broker.addConnector(url);
        broker.start();

        errorCount = 0;
        okCount = 0;
    }

    @AfterEach
    void tearDown() throws Exception {
        broker.stop();
        // Just give the broker some time to stop
        Thread.sleep(1500);
    }

    @Test
    void testRedeliveryQueue() throws Exception {
        doTestRedelivery("queue:someQueue");
    }

    @Test
    void testRedeliveryTopic() throws Exception {
        doTestRedelivery("topic:someTopic");
    }

    private void doTestRedelivery(String destinationName) throws Exception {
        ActiveMQConnectionFactory realConnectionFactory = new ActiveMQConnectionFactory(url);
        PooledConnectionFactory connectionFactory = new PooledConnectionFactory();
        connectionFactory.setConnectionFactory(realConnectionFactory);

        ActiveMQReceiverHandler<String> h = new ActiveMQReceiverHandler<>(
            destinationName,
            connectionFactory,
            this::receiveMessage,
            String.class,
            objectMapper,
            this::exceptionHandler,
            1,
            null);

        h.start();

        ActiveMQSender sender = new ActiveMQSenderImpl(connectionFactory, objectMapper, destinationName, Optional.empty(), false);

        sender.sendJson("fail");
        sender.sendJson("ok1");
        sender.sendJson("ok2");

        Thread.sleep(1000);

        assertAll(
            () -> assertEquals(3 + 1, errorCount),
            () -> assertEquals(2, okCount)
        );
    }

    private void receiveMessage(String message, Map<String, Object> messageProperties) {
        if (message.equals("fail")) {
            errorCount++;
            throw new RuntimeException("Error in receiveMessage");
        } else {
            okCount++;
            System.out.printf("receiveMessage: %s. messageProperties: %s", message, messageProperties);
        }
    }

    public boolean exceptionHandler(String message, Exception exception) {
        System.out.println("exceptionHandler: " + message + " - " + exception.getMessage());
        return false;
    }
}
