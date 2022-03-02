/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.livisismarthome.internal;

import java.net.URI;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.openhab.binding.livisismarthome.internal.listener.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link LivisiWebSocket} implements the websocket for receiving constant updates
 * from the LIVISI SmartHome web service.
 *
 * @author Oliver Kuhl - Initial contribution
 * @author Sven Strohschein - Renamed from Innogy to Livisi
 */
@NonNullByDefault
@WebSocket
public class LivisiWebSocket {

    private final Logger logger = LoggerFactory.getLogger(LivisiWebSocket.class);

    private final HttpClient httpClient;
    private final EventListener eventListener;
    private final URI webSocketURI;
    private final int maxIdleTimeout;

    private @Nullable Session session;
    private @Nullable WebSocketClient client;
    private boolean closing;

    /**
     * Constructs the {@link LivisiWebSocket}.
     *
     * @param eventListener the responsible
     *            {@link org.openhab.binding.livisismarthome.internal.handler.LivisiBridgeHandler}
     * @param webSocketURI the {@link URI} of the websocket endpoint
     * @param maxIdleTimeout max idle timeout
     */
    public LivisiWebSocket(HttpClient httpClient, EventListener eventListener, URI webSocketURI, int maxIdleTimeout) {
        this.httpClient = httpClient;
        this.eventListener = eventListener;
        this.webSocketURI = webSocketURI;
        this.maxIdleTimeout = maxIdleTimeout;
    }

    /**
     * Starts the {@link LivisiWebSocket}.
     */
    public synchronized void start() throws Exception {
        if (client == null || client.isStopped()) {
            client = startWebSocketClient();
        }

        if (session != null) {
            session.close();
        }

        logger.debug("Connecting to LIVISI SmartHome webSocket...");
        session = client.connect(this, webSocketURI).get();
    }

    /**
     * Stops the {@link LivisiWebSocket}.
     */
    public synchronized void stop() {
        this.closing = true;
        if (isRunning()) {
            logger.debug("Closing session...");
            session.close();
            session = null;
        } else {
            session = null;
            logger.trace("Stopping websocket ignored - was not running.");
        }
        if (client != null) {
            try {
                client.stop();
                client.destroy();
            } catch (Exception e) {
                logger.debug("Stopping websocket failed", e);
            }
            client = null;
        }
    }

    /**
     * Return true, if the websocket is running.
     *
     * @return true if the websocket is running, otherwise false
     */
    public synchronized boolean isRunning() {
        return session != null && session.isOpen();
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        this.closing = false;
        logger.info("Connected to LIVISI SmartHome webservice.");
        logger.trace("LIVISI SmartHome websocket session: {}", session);
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        if (statusCode == StatusCode.NORMAL) {
            logger.info("Connection to LIVISI SmartHome webservice was closed normally.");
        } else if (!closing) {
            // An additional reconnect attempt is only required when the close/stop wasn't executed by the binding.
            logger.info("Connection to LIVISI SmartHome webservice was closed abnormally (code: {}). Reason: {}",
                    statusCode, reason);
            eventListener.connectionClosed();
        }
    }

    @OnWebSocketError
    public void onError(Throwable cause) {
        logger.debug("LIVISI SmartHome websocket onError() - {}", cause.getMessage());
        eventListener.onError(cause);
    }

    @OnWebSocketMessage
    public void onMessage(String msg) {
        logger.debug("LIVISI SmartHome websocket onMessage() - {}", msg);
        if (closing) {
            logger.debug("LIVISI SmartHome websocket onMessage() - ignored, WebSocket is closing...");
        } else {
            eventListener.onEvent(msg);
        }
    }

    WebSocketClient startWebSocketClient() throws Exception {
        WebSocketClient client = new WebSocketClient(httpClient);
        client.setMaxIdleTimeout(this.maxIdleTimeout);
        client.start();
        return client;
    }
}