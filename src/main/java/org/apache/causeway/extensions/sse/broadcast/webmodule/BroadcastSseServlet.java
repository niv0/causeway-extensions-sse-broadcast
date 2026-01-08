/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.causeway.extensions.sse.broadcast.webmodule;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.causeway.extensions.sse.broadcast.service.SseBroadcastService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import org.apache.causeway.applib.services.iactnlayer.InteractionService;
import org.apache.causeway.commons.internal.base._Strings;
import org.apache.causeway.core.config.CausewayConfiguration;
import org.apache.causeway.extensions.sse.applib.annotations.SseSource;
import org.apache.causeway.extensions.sse.applib.service.SseChannel;

import lombok.extern.slf4j.Slf4j;

/**
 * Broadcast-based Server-Sent Events servlet.
 *
 * <p>
 * This servlet handles broadcast SSE connections where multiple clients can
 * connect to the same named channel and receive events broadcast to that channel.
 * </p>
 *
 * <p>
 * URL pattern: <code>/sse/broadcast?channel=&lt;channelName&gt;</code>
 * </p>
 *
 * <h2>Usage Examples</h2>
 * <ul>
 *   <li><code>/sse/broadcast?channel=test-channel</code> - General test channel</li>
 *   <li><code>/sse/broadcast?channel=device:barrier-01</code> - Device-specific updates</li>
 *   <li><code>/sse/broadcast?channel=workflow:12345</code> - Workflow state changes</li>
 *   <li><code>/sse/broadcast?channel=type:BARRIER</code> - All devices of a type</li>
 * </ul>
 *
 * <h2>JavaScript Client Example</h2>
 * <pre>
 * const eventSource = new EventSource('/sse/broadcast?channel=device:barrier-01');
 * eventSource.onmessage = (event) => {
 *     console.log('Received:', event.data);
 * };
 * </pre>
 *
 * <p>
 * Note: This servlet is registered programmatically in {@link WebModuleServerSentEvents},
 * not via @WebServlet annotation, to ensure proper integration with Apache Causeway's
 * servlet registration mechanism and avoid Spring MVC interference.
 * </p>
 *
 * @see <a href="https://html.spec.whatwg.org/multipage/server-sent-events.html">Server-Sent Events Spec</a>
 *
 * @since 3.1
 */
@Slf4j
public class BroadcastSseServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Autowired
    @Qualifier("Broadcast")
    private SseBroadcastService broadcastService;

    @Autowired
    private InteractionService interactionService;

    @Autowired
    private CausewayConfiguration causewayConfiguration;

    @Value("${causeway.extensions.sse.broadcast.bypass-authentication:false}")
    private boolean bypassAuthentication;

    @Override
    public void init() throws ServletException {
        super.init();
        Objects.requireNonNull(broadcastService, "broadcastService is required");
        Objects.requireNonNull(interactionService, "interactionService is required");
        log.info("BroadcastSseServlet initialized (authentication bypass: {})", bypassAuthentication);
        if (bypassAuthentication) {
            log.warn("SSE Broadcast authentication bypass is ENABLED - should only be used in development!");
        }
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {

        // Handle CORS headers
        handleCorsHeaders(request, response);

        var channelName = request.getParameter("channel");

        if (_Strings.isNullOrEmpty(channelName)) {
            response.setStatus(400);
            response.getWriter().write("Missing required parameter: 'channel'");
            log.warn("Broadcast SSE request missing channel parameter");
            return;
        }

        log.debug("Broadcast SSE connection requested for channel: {}", channelName);

        // ✅ CHECK AUTHENTICATION FIRST - before any response handling
        // Session must be accessed from the request thread, BEFORE committing response
        final boolean hasSession;
        if (bypassAuthentication) {
            hasSession = true; // Bypass mode - no session needed
            log.debug("Authentication bypass enabled for channel: {}", channelName);
        } else {
            // Debug: Log cookie information
            var cookies = request.getCookies();
            if (cookies != null) {
                log.debug("Request has {} cookies for channel: {}", cookies.length, channelName);
                for (var cookie : cookies) {
                    log.debug("Cookie: name={}, value={}, path={}, domain={}",
                        cookie.getName(),
                        cookie.getValue().substring(0, Math.min(10, cookie.getValue().length())) + "...",
                        cookie.getPath(),
                        cookie.getDomain());
                }
            } else {
                log.warn("No cookies in request for channel: {}", channelName);
            }

            var session = request.getSession(false); // Don't create new session
            hasSession = (session != null);

            if (hasSession) {
                log.debug("HTTP session found for channel: {} - session ID: {}",
                    channelName, session.getId());
            } else {
                log.warn("No HTTP session found for SSE broadcast channel: {} - authentication required", channelName);
                log.warn("Request URI: {}, Context Path: {}, Session ID from request: {}",
                    request.getRequestURI(),
                    request.getContextPath(),
                    request.getRequestedSessionId());
                log.warn("HINT: For development/testing, set causeway.extensions.sse.broadcast.bypass-authentication=true");
                response.setStatus(401);
                response.setContentType("text/event-stream");
                response.getWriter().write("event: error\ndata: Authentication required - session not found. For development, enable bypass-authentication.\n\n");
                response.getWriter().flush();
                return; // Exit early - don't start async processing
            }
        }

        // ✅ Authentication validated - now proceed with SSE setup

        // Lookup or create the channel
        var eventStream = broadcastService.lookupByChannelName(channelName)
                .orElse(null);

        if (eventStream == null) {
            log.error("Failed to create/lookup channel: {}", channelName);
            response.setStatus(500);
            response.getWriter().write("Failed to create channel");
            return;
        }

        // Setup SSE response headers
        response.setStatus(200);
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache,no-store");
        response.setHeader("X-Accel-Buffering", "no"); // Disable nginx buffering

        // Flush immediately to commit the response
        if (!flushBuffer(response)) {
            log.warn("Failed to flush initial response buffer for channel: {}", channelName);
            return;
        }

        // Write a comment to fully commit the response
        try {
            response.getWriter().write(": connected\n\n");
            response.getWriter().flush();
        } catch (IOException e) {
            log.error("Failed to write initial comment for channel: {}", channelName);
            return;
        }

        // Start async processing
        asyncContext(request).ifPresent(asyncContext -> {
            ForkJoinPool.commonPool().submit(() -> {
                // Open interaction context
                if (bypassAuthentication) {
                    // Development mode: bypass authentication with anonymous user
                    interactionService.runAnonymous(() -> {
                        forkWithinInteraction(asyncContext, eventStream, channelName);
                    });
                } else {
                    // Production mode: open interaction from HTTP session
                    // We already verified session exists (in request thread above)
                    try {
                        var interactionLayer = interactionService.openInteraction();
                        try {
                            forkWithinInteraction(asyncContext, eventStream, channelName);
                        } finally {
                            interactionService.closeInteractionLayers();
                        }
                    } catch (Exception e) {
                        log.error("Failed to open interaction for SSE broadcast channel: {} - {}", channelName, e.getMessage());
                        try {
                            asyncContext.getResponse().getWriter().write("event: error\ndata: Authentication required - session may have expired\n\n");
                            asyncContext.complete();
                        } catch (IOException ioException) {
                            log.error("Failed to write authentication error", ioException);
                        }
                    }
                }
            });
        });

        log.info("Client connected to broadcast channel: {} (clients: {})",
            channelName, broadcastService.getClientCount(channelName));
    }

    // -- HELPER METHODS --

    private Optional<AsyncContext> asyncContext(final HttpServletRequest request) {
        try {
            AsyncContext asyncContext = request.startAsync();
            // Set timeout to 0 (infinite) to prevent automatic timeout
            asyncContext.setTimeout(0);
            log.debug("AsyncContext created with infinite timeout");
            return Optional.of(asyncContext);
        } catch (IllegalStateException e) {
            log.warn("Failed to put request into asynchronous mode", e);
            return Optional.empty();
        }
    }

    private boolean flushBuffer(final HttpServletResponse response) {
        try {
            response.flushBuffer();
            return true;
        } catch (IOException e) {
            log.warn("Failed to flush response buffer", e);
        }
        return false;
    }

    /**
     * Fork async processing for the SSE connection within an interaction context.
     * Registers a listener and keeps the connection alive.
     *
     * <p>
     * This method must be called within an active interaction (via {@link InteractionService}).
     * The async context remains open until the client disconnects or an error occurs.
     * </p>
     */
    private void forkWithinInteraction(final AsyncContext asyncContext, final SseChannel eventStream, final String channelName) {

        var response = asyncContext.getResponse();

        log.debug("Starting SSE listener for channel: {}", channelName);

        // Send initial heartbeat comment to establish connection
        try {
            response.getWriter().write(": heartbeat\n\n");
            response.getWriter().flush();
        } catch (IOException e) {
            log.warn("Failed to send initial heartbeat for channel: {}", channelName);
            asyncContext.complete();
            return;
        }

        // Start heartbeat thread to prevent connection timeout
        var heartbeatFuture = ForkJoinPool.commonPool().submit(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(15000); // Send heartbeat every 15 seconds
                    try {
                        var writer = response.getWriter();
                        if (writer != null) {
                            writer.write(": heartbeat\n\n");
                            writer.flush();
                            log.trace("Heartbeat sent for channel: {}", channelName);
                        }
                    } catch (IOException e) {
                        log.debug("Heartbeat failed for channel: {}, client likely disconnected", channelName);
                        Thread.currentThread().interrupt();
                        // Client disconnected - complete async context
                        try {
                            asyncContext.complete();
                        } catch (Exception ex) {
                            // Ignore - may already be completed
                        }
                    }
                }
            } catch (InterruptedException e) {
                log.debug("Heartbeat thread interrupted for channel: {}", channelName);
                Thread.currentThread().interrupt();
            }
        });

        // Register listener to receive broadcast events
        // NOTE: listenWhile() does NOT block - it just registers the listener
        // The listener will be called asynchronously when events are fired
        eventStream.listenWhile(source -> {

            if (ForkJoinPool.commonPool().isShutdown()) {
                log.debug("Fork join pool is shutdown, stopping listener for channel: {}", channelName);
                heartbeatFuture.cancel(true);
                try {
                    asyncContext.complete();
                } catch (Exception e) {
                    // Ignore
                }
                return false; // stop listening
            }

            try {
                var writer = response.getWriter();
                if (writer == null) {
                    log.warn("Writer is null for channel: {}, stopping listener", channelName);
                    heartbeatFuture.cancel(true);
                    try {
                        asyncContext.complete();
                    } catch (Exception e) {
                        // Ignore
                    }
                    return false; // stop listening
                }

                // Get the payload as string - no need to marshal/encode
                var payload = source.getPayload();

                writer
                    .append("data: ")
                    .append(payload)
                    .append("\n\n")
                    .flush();

                log.trace("Event sent to client on channel: {}", channelName);
                return true; // continue listening

            } catch (IOException e) {
                log.debug("Client disconnected from channel: {} ({})", channelName, e.getMessage());
                heartbeatFuture.cancel(true);
                try {
                    asyncContext.complete();
                } catch (Exception ex) {
                    // Ignore
                }
                log.info("Client disconnected from broadcast channel: {} (remaining clients: {})",
                    channelName, broadcastService.getClientCount(channelName));
                return false; // stop listening (client disconnected)
            } catch (Exception e) {
                log.warn("Failed to send event to client on channel: {}", channelName, e);
                heartbeatFuture.cancel(true);
                try {
                    asyncContext.complete();
                } catch (Exception ex) {
                    // Ignore
                }
                return false; // stop listening
            }

        });

        // ✅ IMPORTANT: Do NOT complete async context here!
        // listenWhile() returns immediately after registering the listener
        // The listener will be called later when events are fired
        // The async context should remain open until the client disconnects

        log.debug("SSE listener registered for channel: {} - connection will remain open", channelName);
    }

    /**
     * Handle CORS preflight requests (OPTIONS method).
     */
    @Override
    protected void doOptions(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        handleCorsHeaders(request, response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * Handle CORS headers based on configuration from application.yml.
     * Reads causeway.extensions.cors.allowed-origins and other CORS settings.
     */
    private void handleCorsHeaders(final HttpServletRequest request, final HttpServletResponse response) {
        String origin = request.getHeader("Origin");

        if (origin == null || origin.isEmpty()) {
            // No Origin header, likely a same-origin request
            return;
        }

        // Get CORS configuration from Causeway configuration
        var corsConfig = causewayConfiguration.getExtensions().getCors();
        var allowedOrigins = corsConfig.getAllowedOrigins();
        var allowCredentials = corsConfig.isAllowCredentials();

        // Check if origin is allowed
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            if (allowedOrigins.contains(origin)) {
                // Set CORS headers for allowed origin
                response.setHeader("Access-Control-Allow-Origin", origin);

                if (allowCredentials) {
                    response.setHeader("Access-Control-Allow-Credentials", "true");
                }

                response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
                response.setHeader("Access-Control-Allow-Headers",
                    "Content-Type, Authorization, X-Requested-With, Accept, Origin, Cache-Control");
                response.setHeader("Access-Control-Max-Age", "3600");

                log.debug("CORS headers set for allowed origin: {}", origin);
            } else {
                log.warn("CORS request from disallowed origin: {} (allowed: {})", origin, allowedOrigins);
            }
        } else {
            // No CORS configuration - don't set any headers
            // Let the request fail if CORS is required
            log.warn("No CORS configuration found and origin header present: {}", origin);
            log.warn("Configure causeway.extensions.cors.allowed-origins in application.yml");
        }
    }
}
