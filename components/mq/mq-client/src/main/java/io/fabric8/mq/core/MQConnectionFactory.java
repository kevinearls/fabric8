/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.mq.core;

import org.apache.activemq.ActiveMQConnectionFactory;

/**
 * A replacement for the {@link ActiveMQConnectionFactory} which is configured with the
 * Kubernetes service name {@link #setServiceName(String)} to connect to different broker groups
 * and it then discovers the broker to connect to via Kubernetes services.
 */
public class MQConnectionFactory extends ActiveMQConnectionFactory {
    private String serviceName;
    private String failoverUrlParameters;

    public MQConnectionFactory() {
    }

    public MQConnectionFactory(String userName, String password) {
        this();
        setUserName(userName);
        setPassword(password);
    }


    @Override
    public String getBrokerURL() {
        return MQs.getBrokerURL(getServiceName(), getFailoverUrlParameters());
    }

    @Override
    public void setBrokerURL(String brokerURL) {
        throw new UnsupportedOperationException("brokerURL property cannot be modified for this component. Please modify the serviceName instead!");
    }

    public String getServiceName() {
        return serviceName;
    }

    /**
     * Sets the Kubernetes service name used to resolve against the <code>$serviceName_SERVICE_HOST</code> and
     * <code>$serviceName_SERVICE_PORT</code> environment variables to find the broker group to connect t.
     */
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getFailoverUrlParameters() {
        return failoverUrlParameters;
    }

    /**
     * Allows any failover parameters to be specified after the <code>failover://host:port/</code> part of the brokerURL.
     */
    public void setFailoverUrlParameters(String failoverUrlParameters) {
        this.failoverUrlParameters = failoverUrlParameters;
    }
}
