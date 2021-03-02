package com.kjetland.dropwizard.activemq;

import org.junit.jupiter.api.Test;

import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DestinationCreatorImplTest {

    private static final String QUEUE_NAME = "dest-name";

    private final Session session = mock(Session.class);
    private final Queue queue = mock(Queue.class);
    private final Topic topic = mock(Topic.class);

    @Test
    void testCreate() throws Exception {
        when(session.createQueue(eq(QUEUE_NAME))).thenReturn(queue);
        when(session.createTopic(eq(QUEUE_NAME))).thenReturn(topic);

        DestinationCreator destinationCreator = new DestinationCreatorImpl();

        assertSame(topic, destinationCreator.create(session, "topic:"+ QUEUE_NAME));
        assertSame(queue, destinationCreator.create(session, "queue:" + QUEUE_NAME));
        assertSame(queue, destinationCreator.create(session, QUEUE_NAME));
    }
}
