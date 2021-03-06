/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.transport.http.netty.http2;

import io.netty.handler.codec.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.transport.http.netty.common.Constants;
import org.wso2.transport.http.netty.config.ListenerConfiguration;
import org.wso2.transport.http.netty.config.SenderConfiguration;
import org.wso2.transport.http.netty.config.TransportsConfiguration;
import org.wso2.transport.http.netty.contract.HttpClientConnector;
import org.wso2.transport.http.netty.contract.HttpWsConnectorFactory;
import org.wso2.transport.http.netty.contract.ServerConnector;
import org.wso2.transport.http.netty.contract.ServerConnectorFuture;
import org.wso2.transport.http.netty.contractimpl.DefaultHttpWsConnectorFactory;
import org.wso2.transport.http.netty.http2.listeners.Http2RedirectListener;
import org.wso2.transport.http.netty.message.HTTPCarbonMessage;
import org.wso2.transport.http.netty.message.HTTPConnectorUtil;
import org.wso2.transport.http.netty.message.HttpMessageDataStreamer;
import org.wso2.transport.http.netty.util.TestUtil;
import org.wso2.transport.http.netty.util.client.http2.MessageGenerator;
import org.wso2.transport.http.netty.util.client.http2.MessageSender;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * This contains test cases for HTTP2 redirection.
 */
public class Http2RedirectionTestCase {

    private static Logger log = LoggerFactory.getLogger(Http2RedirectionTestCase.class);

    private HttpClientConnector httpClientConnector;
    private ServerConnector serverConnector;
    private SenderConfiguration senderConfiguration;
    private HttpWsConnectorFactory connectorFactory;

    private final String expectedResponse = "Expected-Response";

    @BeforeClass
    public void setup() throws InterruptedException {
        connectorFactory = new DefaultHttpWsConnectorFactory();
        ListenerConfiguration listenerConfiguration = new ListenerConfiguration();
        listenerConfiguration.setPort(TestUtil.HTTP_SERVER_PORT);
        listenerConfiguration.setScheme(Constants.HTTP_SCHEME);
        listenerConfiguration.setVersion(String.valueOf(Constants.HTTP_2_0));
        serverConnector = connectorFactory
                .createServerConnector(TestUtil.getDefaultServerBootstrapConfig(), listenerConfiguration);
        ServerConnectorFuture future = serverConnector.start();
        future.setHttpConnectorListener(new Http2RedirectListener(2, expectedResponse));
        future.sync();

        TransportsConfiguration transportsConfiguration = new TransportsConfiguration();
        senderConfiguration =
                HTTPConnectorUtil.getSenderConfiguration(transportsConfiguration, Constants.HTTP_SCHEME);
        senderConfiguration.setHttpVersion(String.valueOf(Constants.HTTP_2_0));
        senderConfiguration.setFollowRedirect(true);
        senderConfiguration.setMaxRedirectCount(5);
        httpClientConnector = connectorFactory.createHttpClientConnector(
                HTTPConnectorUtil.getTransportProperties(transportsConfiguration), senderConfiguration);
    }

    @Test
    public void testHttp2Redirection() {
        HTTPCarbonMessage httpCarbonMessage = MessageGenerator.generateRequest(HttpMethod.GET, null);
        HTTPCarbonMessage response = new MessageSender(httpClientConnector).sendMessage(httpCarbonMessage);
        assertNotNull(response, "Expected response not received");
        String result = TestUtil.getStringFromInputStream(new HttpMessageDataStreamer(response).getInputStream());
        assertEquals(result, expectedResponse, "Expected response not received, but received" + result);
    }

    @AfterClass
    public void cleanUp() {
        senderConfiguration.setHttpVersion(String.valueOf(Constants.HTTP_1_1));
        senderConfiguration.setFollowRedirect(false);
        httpClientConnector.close();
        serverConnector.stop();
        try {
            connectorFactory.shutdown();
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for HttpWsFactory to close");
        }
    }
}
