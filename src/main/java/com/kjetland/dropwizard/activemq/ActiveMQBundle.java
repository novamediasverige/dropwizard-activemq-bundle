package com.kjetland.dropwizard.activemq;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.jms.pool.PooledConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static java.lang.String.format;

public class ActiveMQBundle implements ConfiguredBundle<ActiveMQConfigHolder>, Managed, ActiveMQSenderFactory {

    private static final String QUESTION_MARK = "?";
    private static final String AMPERSAND = "&";
    private final Logger log = LoggerFactory.getLogger(getClass());
    private String healthCheckName = "ActiveMQ";
    private ActiveMQConnectionFactory realConnectionFactory;
    private PooledConnectionFactory connectionFactory = null;
    private ObjectMapper objectMapper;
    private Environment environment;
    private long shutdownWaitInSeconds;
    private Optional<Integer> defaultTimeToLiveInSeconds;
    private boolean healthcheckDisabled = false;
    public static final ThreadLocal<String> correlationID = new ThreadLocal<>();

    public ActiveMQBundle() {
    }

    public ActiveMQBundle(String brokerName) {
        this.healthCheckName = format("%s_%s", healthCheckName, brokerName);
    }

    @Override
    public void run(ActiveMQConfigHolder configuration, Environment environment) {
        init(configuration.getActiveMQ(), environment);
    }

    public void init(ActiveMQConfig activeMQConfig, Environment environment) {
        this.environment = environment;
        final String brokerUrl = activeMQConfig.brokerUrl;
        final int configuredTTL = activeMQConfig.timeToLiveInSeconds;
        defaultTimeToLiveInSeconds = Optional.ofNullable(configuredTTL > 0 ? configuredTTL : null);

        log.info("Setting up activeMq with brokerUrl {}", brokerUrl);

        log.debug("All activeMQ config: " + activeMQConfig);

        realConnectionFactory = getActiveMQConnectionFactory(brokerUrl, activeMQConfig.brokerUsername, activeMQConfig.brokerPassword);
        connectionFactory = new PooledConnectionFactory();
        connectionFactory.setConnectionFactory(realConnectionFactory);

        configurePool(activeMQConfig.pool);

        objectMapper = environment.getObjectMapper();

        environment.lifecycle().manage(this);
        registerHealthCheckIfRequired(activeMQConfig, environment);

        this.shutdownWaitInSeconds = activeMQConfig.shutdownWaitInSeconds;
    }

    private ActiveMQConnectionFactory getActiveMQConnectionFactory(String brokerUrl, String username, String password) {
        ActiveMQConnectionFactory activeMQConnectionFactory = new ActiveMQConnectionFactory(brokerUrl);
        if (username != null && password != null) {
            activeMQConnectionFactory.setUserName(username);
            activeMQConnectionFactory.setPassword(password);
        }
        return activeMQConnectionFactory;
    }

    private void registerHealthCheckIfRequired(ActiveMQConfig activeMQConfig, Environment environment) {

        if (activeMQConfig.healthCheckRequired) {

            String healthCheckBrokerUrl = getHealthCheckBrokerUrl(activeMQConfig);
            ActiveMQConnectionFactory healthCheckActiveMQConnectionFactory = getActiveMQConnectionFactory(healthCheckBrokerUrl, activeMQConfig.brokerUsername, activeMQConfig.brokerPassword);
            // Must use independent connectionFactory instead of (pooled) connectionFactory for the healthCheck
            // It needs its own connection since it is both sending and receiving.
            // If using pool, then it might be blocked when pool is short on idle threads and no connection is available..
            environment.healthChecks().register(healthCheckName,
                new ActiveMQHealthCheck(healthCheckActiveMQConnectionFactory, activeMQConfig.healthCheckMillisecondsToWait)
            );
        } else {
            healthcheckDisabled = true;
            log.info("ActiveMQ healthcheck is disabled for the service");
        }
    }

    private String getHealthCheckBrokerUrl(ActiveMQConfig activeMQConfig) {
        String healthCheckBrokerUrl = activeMQConfig.brokerUrl;

        if (activeMQConfig.healthCheckAppendToBrokerUrl != null) {
            activeMQConfig.healthCheckAppendToBrokerUrl = removeUrlSeparatorIfPresent(activeMQConfig.healthCheckAppendToBrokerUrl);

            String separatorChar = activeMQConfig.brokerUrl.contains(QUESTION_MARK) ? AMPERSAND : QUESTION_MARK;
            healthCheckBrokerUrl = activeMQConfig.brokerUrl + separatorChar + activeMQConfig.healthCheckAppendToBrokerUrl;
        }
        return healthCheckBrokerUrl;
    }

    private String removeUrlSeparatorIfPresent(String healthCheckAppendToBrokerUrl) {
        if (healthCheckAppendToBrokerUrl.startsWith(QUESTION_MARK) || healthCheckAppendToBrokerUrl.startsWith(AMPERSAND)) {
            return healthCheckAppendToBrokerUrl.substring(1);
        }
        return healthCheckAppendToBrokerUrl;
    }

    private void configurePool(ActiveMQPoolConfig poolConfig) {
        if (poolConfig == null) {
            return;
        }

        if (poolConfig.maxConnections != null) {
            connectionFactory.setMaxConnections(poolConfig.maxConnections);
        }

        if (poolConfig.maximumActiveSessionPerConnection != null) {
            connectionFactory.setMaximumActiveSessionPerConnection(poolConfig.maximumActiveSessionPerConnection);
        }

        if (poolConfig.blockIfSessionPoolIsFull != null) {
            connectionFactory.setBlockIfSessionPoolIsFull(poolConfig.blockIfSessionPoolIsFull);
        }

        if (poolConfig.idleTimeoutMills != null) {
            connectionFactory.setIdleTimeout(poolConfig.idleTimeoutMills);
        }

        if (poolConfig.expiryTimeoutMills != null) {
            connectionFactory.setExpiryTimeout(poolConfig.expiryTimeoutMills);
        }

        if (poolConfig.createConnectionOnStartup != null) {
            connectionFactory.setCreateConnectionOnStartup(poolConfig.createConnectionOnStartup);
        }

        if (poolConfig.timeBetweenExpirationCheckMillis != null) {
            connectionFactory.setTimeBetweenExpirationCheckMillis(poolConfig.timeBetweenExpirationCheckMillis);
        }
    }

    @Override
    public void initialize(Bootstrap<?> bootstrap) {

    }

    @Override
    public void start() {
        log.info("Starting activeMQ client");
        connectionFactory.start();
    }

    @Override
    public void stop() {
        log.info("Stopping activeMQ client");
        connectionFactory.stop();
    }

    public ActiveMQSender createSender(String destination, boolean persistent) {
        return createSender(destination, persistent, defaultTimeToLiveInSeconds);
    }

    public ActiveMQSender createSender(String destination, boolean persistent, Optional<Integer> timeToLiveInSeconds) {
        return new ActiveMQSenderImpl(connectionFactory, objectMapper, destination, timeToLiveInSeconds, persistent);
    }

    // This must be used during run-phase
    public <T> ActiveMQReceiverHandler<T> registerReceiver(String destination, ActiveMQReceiver<T> receiver, Class<? extends T> clazz,
                                                           final boolean ackMessageOnException) {
        return registerReceiver(destination, receiver, clazz, ackMessageOnException, null);
    }

    // This must be used during run-phase
    public <T> ActiveMQReceiverHandler<T> registerReceiver(String destination, ActiveMQReceiver<T> receiver, Class<? extends T> clazz,
                                                           final boolean ackMessageOnException, String messageSelector) {

        ActiveMQReceiverHandler<T> handler = new ActiveMQReceiverHandler<>(
            destination,
            realConnectionFactory,
            receiver,
            clazz,
            objectMapper,
            (message, exception) -> {
                if (ackMessageOnException) {
                    log.error("Error processing received message - acknowledging it anyway", exception);
                    return true;
                } else {
                    log.error("Error processing received message - NOT acknowledging it", exception);
                    return false;
                }
            },
            shutdownWaitInSeconds,
            messageSelector
        );

        internalRegisterReceiver(destination, handler);
        return handler;
    }

    private <T> void internalRegisterReceiver(String destination, ActiveMQReceiverHandler<T> handler) {
        environment.lifecycle().manage(handler);
        if (!healthcheckDisabled) {
            environment.healthChecks().register("ActiveMQ receiver for " + destination, handler.getHealthCheck());
        }
    }

    // This must be used during run-phase
    public <T> ActiveMQReceiverHandler<T> registerReceiver(String destination, ActiveMQReceiver<T> receiver, Class<? extends T> clazz,
                                                           ActiveMQBaseExceptionHandler exceptionHandler) {
        return registerReceiver(destination, receiver, clazz, exceptionHandler, null);
    }

    // This must be used during run-phase
    public <T> ActiveMQReceiverHandler<T> registerReceiver(String destination, ActiveMQReceiver<T> receiver, Class<? extends T> clazz,
                                                           ActiveMQBaseExceptionHandler exceptionHandler, String messageSelector) {

        ActiveMQReceiverHandler<T> handler = new ActiveMQReceiverHandler<>(
            destination,
            realConnectionFactory,
            receiver,
            clazz,
            objectMapper,
            exceptionHandler,
            shutdownWaitInSeconds,
            messageSelector);

        internalRegisterReceiver(destination, handler);
        return handler;
    }

    // This must be used during run-phase
    public <T> ActiveMQReceiverHandler<T> registerReceiver(String destination, ActiveMQReceiver<T> receiver, Class<? extends T> clazz,
                                                           ActiveMQExceptionHandler exceptionHandler) {
        return registerReceiver(destination, receiver, clazz, (ActiveMQBaseExceptionHandler) exceptionHandler);
    }
}
