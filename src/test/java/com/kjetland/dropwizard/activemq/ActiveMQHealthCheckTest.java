package com.kjetland.dropwizard.activemq;

import com.codahale.metrics.health.HealthCheck;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.jms.Connection;
import javax.jms.JMSException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ActiveMQHealthCheckTest {

    final String url = "tcp://localhost:31219";
    BrokerService broker;

    @Before
    public void setUp() throws Exception {
        broker = new BrokerService();
        // configure the broker
        broker.addConnector(url);
        broker.start();
    }

    @After
    public void tearDown() throws Exception {
        broker.stop();
        Thread.sleep(1500);
    }

    @Test
    public void testCheck() throws Exception {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(url);
        ActiveMQHealthCheck h = new ActiveMQHealthCheck(connectionFactory, 3000);
        assertEquals(HealthCheck.Result.healthy(), h.check());
        assertEquals(HealthCheck.Result.healthy(), h.check());
        assertEquals(HealthCheck.Result.healthy(), h.check());
    }

    @Test(expected = JMSException.class)
    public void testCheckConnectionCloseHandling() throws Exception {
        //given
        ActiveMQConnectionFactory connectionFactory = mock(ActiveMQConnectionFactory.class);
        Connection connection = mock(Connection.class);
        when(connectionFactory.createConnection()).thenReturn(connection);

        doThrow(new JMSException("JmsError", "999")).when(connection).start();

        ActiveMQHealthCheck h = new ActiveMQHealthCheck(connectionFactory, 3000);

        //when
        h.check();

        //then
        verify(connection).close();
    }
}
