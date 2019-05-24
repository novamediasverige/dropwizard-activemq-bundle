package com.kjetland.dropwizard.activemq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ActiveMQSenderImplTest {

    @Mock
    private ConnectionFactory connectionFactory;
    @Mock
    private Connection connection;
    @Mock
    private Session session;
    @Mock
    private Queue queue;
    @Mock
    private MessageProducer messageProducer;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private TextMessage textMessage;

    @Before
    public void setUp() throws Exception {
        when(connectionFactory.createConnection()).thenReturn(connection);
        when(connection.createSession(anyBoolean(), anyInt())).thenReturn(session);
    }

    @Test
    public void testSendSimpleQueueWithCreatorFunction() throws Exception {
        final String queueName = "myQueue";
        final String myJson = "{'a': 2, 'b': 'Some text'}";
        final String myCorrelationId = UUID.randomUUID().toString();
        final ActiveMQSender sender = new ActiveMQSenderImpl(connectionFactory, objectMapper, queueName, Optional.empty(), false);

        when(session.createQueue(queueName)).thenReturn(queue);
        when(session.createProducer(queue)).thenReturn(messageProducer);
        when(session.createTextMessage()).thenReturn(textMessage);

        // Send a message
        sender.send((Session session) -> {
            TextMessage message = session.createTextMessage();
            message.setText(myJson);
            message.setJMSCorrelationID(myCorrelationId);
            message.setJMSReplyTo(queue);
            return message;
        });

        // Verify that the message was constructed as intended
        verify(textMessage).setText(myJson);
        verify(textMessage).setJMSCorrelationID(myCorrelationId);
        verify(textMessage).setJMSReplyTo(queue);
        // Verify that the message was sent by the producer
        verify(messageProducer).send(textMessage);
        verify(messageProducer).setDeliveryMode(DeliveryMode.NON_PERSISTENT);
        // Verify that everything was cleaned up afterwards
        verify(messageProducer).close();
        verify(session).close();
        verify(connection).close();
        verifyNoMoreInteractions(textMessage, messageProducer);
    }

    @Test
    public void testSendSimpleQueueWithCreatorFunctionWhenExceptionIsThrown() throws Exception {
        final String queueName = "myQueue";
        final String myJson = "{'a': 2, 'b': 'Some text'}";
        final String myCorrelationId = UUID.randomUUID().toString();
        final ActiveMQSender sender = new ActiveMQSenderImpl(connectionFactory, objectMapper, queueName, Optional.empty(), false);
        final JMSException thrownException = new JMSException("Test");

        when(session.createQueue(queueName)).thenReturn(queue);
        when(session.createProducer(queue)).thenReturn(messageProducer);
        doThrow(thrownException).when(session).createTextMessage();

        // Send a message and verify that a wrapped RuntimeException is thrown
        try {

            sender.send((Session session) -> {
                TextMessage message = session.createTextMessage();
                message.setText(myJson);
                message.setJMSCorrelationID(myCorrelationId);
                return message;
            });
            // We should not arrive here
            fail("Expected JMSException was not thrown");
        } catch (RuntimeException re) {
            assertEquals(thrownException, re.getCause());
        }
        // Verify that the message was not sent by the producer
        verify(messageProducer, never()).send(any(Message.class));
        // Verify that everything was cleaned up afterwards
        verify(messageProducer).close();
        verify(session).close();
        verify(connection).close();
    }

    @Test
    public void testFiltersAreAppliedSendFunction() throws JMSException {
        doTestSenderFilters((sender) -> sender.send((Session session) -> {
            TextMessage message = session.createTextMessage();
            message.setText("jmsfunction");
            return message;
        }));
    }

    @Test
    public void testFiltersAreAppliedSendJson() throws JMSException {
        doTestSenderFilters((sender) -> sender.sendJson("{\"key\": \"value\"}"));
    }

    @Test
    public void testFiltersAreAppliedSendObject() throws JMSException {
        doTestSenderFilters((sender) -> sender.send("object"));
    }

    private void doTestSenderFilters(Consumer<ActiveMQSender> senderConsumer) throws JMSException {
        final String queueName = "myQueue";
        final ActiveMQSender sender = new ActiveMQSenderImpl(connectionFactory, objectMapper, queueName, Optional.empty(), false);

        when(session.createQueue(queueName)).thenReturn(queue);
        when(session.createProducer(queue)).thenReturn(messageProducer);
        when(session.createTextMessage(any())).thenReturn(textMessage);
        when(session.createTextMessage()).thenReturn(textMessage);

        sender.addFilter(m -> {
            try {
                m.setStringProperty("property-from-filter", "apply-me");
            } catch (JMSException e) {
                fail(e.getMessage());
            }
        });

        senderConsumer.accept(sender);

        verify(textMessage, times(1)).setStringProperty("property-from-filter", "apply-me");
    }
}