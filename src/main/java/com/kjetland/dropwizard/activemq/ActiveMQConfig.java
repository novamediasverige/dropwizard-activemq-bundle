package com.kjetland.dropwizard.activemq;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class ActiveMQConfig {

    @JsonProperty
    @NotNull
    public String brokerUrl;

    /**
     * {@literal
     *  Ex. &startupMaxReconnectAttempts=1 (use if healthcheck is on. Fixes hanging behavior of healthchecks when activeMQ is down).
     *  ? or & depends on whether brokerUrl already included options
     * }
     */
    @JsonProperty
    public String healthCheckAppendToBrokerUrl;

    @JsonProperty
    public String brokerUsername;

    @JsonProperty
    public String brokerPassword;

    @JsonProperty
    public long healthCheckMillisecondsToWait = 2000; // 2 seconds

    /**
     * Set to false if activeMQ outage is handled well in your service and activeMQ being unavailable should NOT mark service unhealthy.
     */
    @JsonProperty
    public boolean healthCheckRequired = true; //can be turned off by application using bundle

    @JsonProperty
    public int shutdownWaitInSeconds = 20;

    @JsonProperty
    public int timeToLiveInSeconds = -1; // Default no TTL. Jackson does not support java.util.Optional yet.

    @JsonProperty
    @Valid
    public ActiveMQPoolConfig pool;

    @Override
    public String toString() {
        return "ActiveMQConfig{" +
            "brokerUrl='" + brokerUrl + '\'' +
            ", healthCheckAppendToBrokerUrl=" + healthCheckAppendToBrokerUrl +
            ", healthCheckMillisecondsToWait=" + healthCheckMillisecondsToWait +
            ", healthCheckRequired=" + healthCheckRequired +
            ", shutdownWaitInSeconds=" + shutdownWaitInSeconds +
            ", timeToLiveInSeconds=" + timeToLiveInSeconds +
            ", brokerUsername=" + brokerUsername +
            ", brokerPassword=" + brokerPassword +
            ", pool=" + pool +
            '}';
    }
}
