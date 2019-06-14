package com.kjetland.dropwizard.activemq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQMessageConsumer;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jetty.util.ConcurrentHashSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.internal.verification.VerificationModeFactory;
import org.mockito.runners.MockitoJUnitRunner;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ActiveMQReceiverHandlerTest {

    public static final String THROW_EXCEPTION_IN_CONSUMER = "THROW_EXCEPTION_IN_CONSUMER";
    public static final String THROW_EXCEPTION_IN_CONSUMER_CLOSED = "THROW_EXCEPTION_IN_CONSUMER_CLOSED";
    public static final String THROW_EXCEPTION_IN_RECEIVER = "THROW_EXCEPTION_IN_RECEIVER";

    String destinationName = "ourQueue";

    @Mock
    ConnectionFactory connectionFactory;

    @Mock
    Connection connection;

    @Mock
    Session session;

    @Mock
    Queue destinationQueue;

    @Mock
    Topic destinationTopic;

    @Mock
    ActiveMQMessageConsumer messageConsumer;

    ObjectMapper objectMapper;

    int messageIndex = 0;
    List<String> messagesList;
    Pair<String, Object> messageProperties;

    Map<String, Map<String, Object>> receivedMessages = new ConcurrentHashMap<>();
    Set<Throwable> receivedExceptions = new ConcurrentHashSet<>();

    public void setUpMocks(List<String> messages, Pair<String, Object> messageProperties) throws Exception {
        when(connectionFactory.createConnection()).thenReturn(connection);
        when(connection.createSession(anyBoolean(), anyInt())).thenReturn(session);
        when(session.createQueue(anyString())).thenReturn(destinationQueue);
        when(session.createTopic(anyString())).thenReturn(destinationTopic);
        when(session.createConsumer(eq(destinationQueue), eq(null))).thenReturn(messageConsumer);
        when(session.createConsumer(eq(destinationTopic), eq(null))).thenReturn(messageConsumer);

        messageIndex = 0;
        messagesList = messages;
        this.messageProperties = messageProperties;
        when(messageConsumer.receive(anyLong())).then((i) -> popMessage());
        receivedMessages.clear();
        receivedExceptions.clear();
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

        Vector<String> possibleProperties = new Vector();
        possibleProperties.add("null_property");
        possibleProperties.add(messageProperties.getKey());
        TextMessage msg = mock(TextMessage.class);
        when(msg.getText()).thenReturn(m);
        when(msg.getPropertyNames()).thenReturn(possibleProperties.elements());
        when(msg.getObjectProperty(messageProperties.getKey())).thenReturn(messageProperties.getValue());
        return msg;
    }

    public boolean exceptionHandler(String message, Exception exception) {
        System.out.println("exceptionHandler: " + message + " - " + exception.getMessage());
        receivedExceptions.add(exception);
        return true;
    }

    @Test
    public void testNormal() throws Exception {
        setUpMocks(Arrays.asList(null, "a", "b", null, "d"), Pair.of(null, null));
        ActiveMQReceiverHandler<String> h = new ActiveMQReceiverHandler<>(
            destinationName,
            connectionFactory,
            (m, i) -> receiveMessage(m, i),
            String.class,
            objectMapper,
            (m, e) -> exceptionHandler(m, e),
            1,
            null);

        h.start();
        Thread.sleep(100);
        verify(connection, VerificationModeFactory.times(1)).start();
        Thread.sleep(200);
        assertTrue(receivedMessages.containsKey("a"));
        assertTrue(receivedMessages.containsKey("b"));
        assertTrue(receivedMessages.containsKey("d"));
        assertEquals(3, receivedMessages.size());
        assertTrue(receivedExceptions.size() == 0);
        assertTrue(receivedMessages.get("a").size() == 0);
        assertTrue(receivedMessages.get("b").size() == 0);
        assertTrue(receivedMessages.get("d").size() == 0);
        h.stop();
    }

    @Test
    public void testExceptionInReceiver() throws Exception {
        setUpMocks(Arrays.asList(null, "a", THROW_EXCEPTION_IN_RECEIVER, "b", null, "d"), Pair.of("my-key", "my-value"));
        ActiveMQReceiverHandler<String> h = new ActiveMQReceiverHandler<>(
            destinationName,
            connectionFactory,
            (m, i) -> receiveMessage(m, i),
            String.class,
            objectMapper,
            (m, e) -> exceptionHandler(m, e),
            1,
            null);

        h.start();
        Thread.sleep(100);
        verify(connection, VerificationModeFactory.times(1)).start();
        Thread.sleep(200);
        assertTrue(receivedMessages.containsKey("a"));
        assertTrue(receivedMessages.containsKey("b"));
        assertTrue(receivedMessages.containsKey("d"));
        assertTrue(receivedMessages.get("a").get("my-key").equals("my-value"));
        assertTrue(receivedMessages.get("a").get("my-key").equals("my-value"));
        assertEquals(3, receivedMessages.size());
        assertTrue(receivedExceptions.size() > 0);
        h.stop();
    }

    @Test
    public void testExceptionInMessageConsumer() throws Exception {

        setUpMocks(Arrays.asList(null, "a", THROW_EXCEPTION_IN_CONSUMER, "b", null, "d"), Pair.of("key", "value"));
        ActiveMQReceiverHandler<String> h = new ActiveMQReceiverHandler<>(
            destinationName,
            connectionFactory,
            (m, i) -> receiveMessage(m, i),
            String.class,
            objectMapper,
            (m, e) -> exceptionHandler(m, e),
            1,
            null);

        h.start();
        Thread.sleep(100);
        verify(connection, VerificationModeFactory.atLeast(2)).start();
        Thread.sleep(200);
        assertTrue(receivedMessages.containsKey("a"));
        assertTrue(receivedMessages.containsKey("b"));
        assertTrue(receivedMessages.containsKey("d"));
        assertTrue(receivedMessages.get("a").get("key").equals("value"));
        assertTrue(receivedMessages.get("a").get("key").equals("value"));
        assertEquals(3, receivedMessages.size());
        assertTrue(receivedExceptions.size() == 0);

        h.stop();
    }

    @Test
    public void testExceptionInMessageConsumer_ConsumerIsClosed() throws Exception {

        setUpMocks(Arrays.asList(null, "a", THROW_EXCEPTION_IN_CONSUMER_CLOSED, "b", null, "d",
            THROW_EXCEPTION_IN_CONSUMER_CLOSED, THROW_EXCEPTION_IN_CONSUMER_CLOSED), Pair.of("key", "value"));
        ActiveMQReceiverHandler<String> h = new ActiveMQReceiverHandler<>(
            destinationName,
            connectionFactory,
            (m, i) -> receiveMessage(m, i),
            String.class,
            objectMapper,
            (m, e) -> exceptionHandler(m, e),
            1,
            null);

        h.start();
        Thread.sleep(100);
        verify(connection, VerificationModeFactory.atLeast(2)).start();
        Thread.sleep(200);
        assertTrue(receivedMessages.containsKey("a"));
        assertTrue(receivedMessages.containsKey("b"));
        assertTrue(receivedMessages.containsKey("d"));
        assertTrue(receivedMessages.get("a").get("key").equals("value"));
        assertTrue(receivedMessages.get("a").get("key").equals("value"));
        assertEquals(3, receivedMessages.size());
        assertTrue(receivedExceptions.size() == 0);
        h.stop();
    }
}