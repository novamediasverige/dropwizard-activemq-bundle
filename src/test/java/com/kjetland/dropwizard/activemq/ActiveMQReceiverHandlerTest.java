package com.kjetland.dropwizard.activemq;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import org.apache.activemq.ActiveMQMessageConsumer;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.mockito.internal.verification.VerificationModeFactory;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ActiveMQReceiverHandlerTest {

    private static final String THROW_EXCEPTION_IN_CONSUMER = "THROW_EXCEPTION_IN_CONSUMER";
    private static final String THROW_EXCEPTION_IN_CONSUMER_CLOSED = "THROW_EXCEPTION_IN_CONSUMER_CLOSED";
    private static final String THROW_EXCEPTION_IN_RECEIVER = "THROW_EXCEPTION_IN_RECEIVER";

    private static final String destinationName = "ourQueue";
    private static final ObjectMapper objectMapper = Jackson.newObjectMapper();

    private final ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
    private final Connection connection = mock(Connection.class);
    private final Session session = mock(Session.class);
    private final Queue destinationQueue = mock(Queue.class);
    private final Topic destinationTopic = mock(Topic.class);
    private final ActiveMQMessageConsumer messageConsumer = mock(ActiveMQMessageConsumer.class);

    private int messageIndex = 0;
    private List<String> messagesList;
    private Pair<String, Object> messageProperties;

    private final Map<String, Map<String, Object>> receivedMessages = new ConcurrentHashMap<>();
    private final Set<Throwable> receivedExceptions = ConcurrentHashMap.newKeySet();

    @Test
    void testNormal() throws Exception {
        setUpMocks(Arrays.asList(null, "a", "b", null, "d"), Pair.of(null, null));
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
        Thread.sleep(100);
        verify(connection, VerificationModeFactory.times(1)).start();
        Thread.sleep(200);

        assertAll(
            () -> assertTrue(receivedMessages.containsKey("a")),
            () -> assertTrue(receivedMessages.containsKey("b")),
            () -> assertTrue(receivedMessages.containsKey("d")),
            () -> assertEquals(3, receivedMessages.size()),
            () -> assertEquals(0, receivedExceptions.size()),
            () -> assertEquals(0, receivedMessages.get("a").size()),
            () -> assertEquals(0, receivedMessages.get("b").size()),
            () -> assertEquals(0, receivedMessages.get("d").size())
        );

        h.stop();
    }

    @Test
    void testExceptionInReceiver() throws Exception {
        setUpMocks(Arrays.asList(null, "a", THROW_EXCEPTION_IN_RECEIVER, "b", null, "d"), Pair.of("my-key", "my-value"));
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
        Thread.sleep(100);
        verify(connection, VerificationModeFactory.times(1)).start();
        Thread.sleep(200);

        assertAll(
            () -> assertTrue(receivedMessages.containsKey("a")),
            () -> assertTrue(receivedMessages.containsKey("b")),
            () -> assertTrue(receivedMessages.containsKey("d")),
            () -> assertEquals("my-value", receivedMessages.get("a").get("my-key")),
            () -> assertEquals("my-value", receivedMessages.get("a").get("my-key")),
            () -> assertEquals(3, receivedMessages.size()),
            () -> assertTrue(receivedExceptions.size() > 0)
        );

        h.stop();
    }

    @Test
    void testExceptionInMessageConsumer() throws Exception {
        setUpMocks(Arrays.asList(null, "a", THROW_EXCEPTION_IN_CONSUMER, "b", null, "d"), Pair.of("key", "value"));
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
        Thread.sleep(100);
        verify(connection, VerificationModeFactory.atLeast(2)).start();
        Thread.sleep(200);

        assertAll(
            () -> assertTrue(receivedMessages.containsKey("a")),
            () -> assertTrue(receivedMessages.containsKey("b")),
            () -> assertTrue(receivedMessages.containsKey("d")),
            () -> assertEquals("value", receivedMessages.get("a").get("key")),
            () -> assertEquals("value", receivedMessages.get("a").get("key")),
            () -> assertEquals(3, receivedMessages.size()),
            () -> assertEquals(0, receivedExceptions.size())
        );

        h.stop();
    }

    @Test
    void testExceptionInMessageConsumer_ConsumerIsClosed() throws Exception {
        setUpMocks(Arrays.asList(null, "a", THROW_EXCEPTION_IN_CONSUMER_CLOSED, "b", null, "d",
            THROW_EXCEPTION_IN_CONSUMER_CLOSED, THROW_EXCEPTION_IN_CONSUMER_CLOSED), Pair.of("key", "value"));
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
        Thread.sleep(100);
        verify(connection, VerificationModeFactory.atLeast(2)).start();
        Thread.sleep(200);

        assertAll(
            () -> assertTrue(receivedMessages.containsKey("a")),
            () -> assertTrue(receivedMessages.containsKey("b")),
            () -> assertTrue(receivedMessages.containsKey("d")),
            () -> assertEquals("value", receivedMessages.get("a").get("key")),
            () -> assertEquals("value", receivedMessages.get("a").get("key")),
            () -> assertEquals(3, receivedMessages.size()),
            () -> assertEquals(0, receivedExceptions.size())
        );

        h.stop();
    }

    private void setUpMocks(List<String> messageList, Pair<String, Object> messageProperties) throws Exception {
        this.messagesList = messageList;
        this.messageProperties = messageProperties;
        this.messageIndex = 0;
        receivedMessages.clear();
        receivedExceptions.clear();

        when(connectionFactory.createConnection()).thenReturn(connection);
        when(connection.createSession(anyBoolean(), anyInt())).thenReturn(session);
        when(session.createQueue(anyString())).thenReturn(destinationQueue);
        when(session.createTopic(anyString())).thenReturn(destinationTopic);
        when(session.createConsumer(eq(destinationQueue), eq(null))).thenReturn(messageConsumer);
        when(session.createConsumer(eq(destinationTopic), eq(null))).thenReturn(messageConsumer);
        when(messageConsumer.receive(anyLong())).then((i) -> popMessage());
    }

    private void receiveMessage(String m, Map<String, Object> messageProperties) {
        if (THROW_EXCEPTION_IN_RECEIVER.equals(m)) {
            throw new RuntimeException(THROW_EXCEPTION_IN_RECEIVER);
        }
        receivedMessages.put(m, messageProperties);
    }

    private TextMessage popMessage() throws Exception {
        String m = messagesList.get(messageIndex);
        messageIndex++;
        if (messageIndex >= messagesList.size()) {
            messageIndex = 0;
        }

        if (m == null) {
            return null;
        }

        if (THROW_EXCEPTION_IN_CONSUMER.equals(m)) {
            throw new RuntimeException(THROW_EXCEPTION_IN_CONSUMER);
        }

        if (THROW_EXCEPTION_IN_CONSUMER_CLOSED.equals(m)) {
            throw new javax.jms.IllegalStateException("The Consumer is closed");
        }

        Vector<String> possibleProperties = new Vector<>();
        possibleProperties.add("null_property");
        possibleProperties.add(messageProperties.getKey());
        TextMessage msg = mock(TextMessage.class);
        when(msg.getText()).thenReturn(m);
        when(msg.getPropertyNames()).thenReturn(possibleProperties.elements());
        when(msg.getObjectProperty(messageProperties.getKey())).thenReturn(messageProperties.getValue());
        return msg;
    }

    private boolean exceptionHandler(String message, Exception exception) {
        System.out.println("exceptionHandler: " + message + " - " + exception.getMessage());
        receivedExceptions.add(exception);
        return true;
    }
}