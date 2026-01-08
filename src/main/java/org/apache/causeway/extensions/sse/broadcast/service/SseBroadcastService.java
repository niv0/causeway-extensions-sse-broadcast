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
package org.apache.causeway.extensions.sse.broadcast.service;

import java.util.List;
import java.util.Optional;

import org.apache.causeway.extensions.sse.applib.service.SseChannel;

/**
 * Broadcast-style Server-Sent Events service.
 *
 * <p>
 * This service allows broadcasting events to multiple clients subscribed to named channels
 * without requiring a running task.
 * </p>
 *
 * <h2>Usage Example</h2>
 * <pre>
 * &#64;Inject SseBroadcastService sseBroadcast;
 *
 * public void notifyClients() {
 *     sseBroadcast.broadcast("device:barrier-01",
 *         "{\"type\":\"update\",\"state\":\"OPEN\"}");
 * }
 * </pre>
 *
 * <h2>Client Connection</h2>
 * <pre>
 * const eventSource = new EventSource('/sse/broadcast?channel=device:barrier-01');
 * eventSource.onmessage = (event) => {
 *     const data = JSON.parse(event.data);
 *     console.log('Received:', data);
 * };
 * </pre>
 *
 * <h2>Channel Naming Conventions</h2>
 * <ul>
 *   <li><code>device:{id}</code> - Device-specific updates</li>
 *   <li><code>workflow:{id}</code> - Workflow state changes</li>
 *   <li><code>type:{deviceType}</code> - All devices of a type</li>
 *   <li><code>global:{eventType}</code> - System-wide notifications</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * All methods are thread-safe and can be called concurrently from multiple
 * threads. Broadcasting is optimized for high concurrency with minimal
 * lock contention.
 * </p>
 *
 * @see <a href="https://html.spec.whatwg.org/multipage/server-sent-events.html">Server-Sent Events Spec</a>
 *
 * @since 3.1 {@index}
 */
public interface SseBroadcastService {

    /**
     * Lookup a channel by its name.
     *
     * <p>
     * If the channel does not exist, it will be created automatically.
     * Empty channels (no connected clients) are removed after a timeout period.
     * </p>
     *
     * @param channelName the channel name (e.g., "device:barrier-01")
     * @return the channel, always present
     * @throws IllegalArgumentException if channelName is invalid
     */
    Optional<SseChannel> lookupByChannelName(String channelName);

    /**
     * Broadcast a string payload to all clients on the specified channel.
     *
     * <p>
     * The payload is sent as-is to all connected clients. For JSON events,
     * send a properly formatted JSON string. For plain text, send the text directly.
     * </p>
     *
     * <p>
     * If the channel does not exist, it will be created. If no clients
     * are connected, the event is silently discarded (fire-and-forget).
     * </p>
     *
     * @param channelName the channel name
     * @param payload the payload to send (typically JSON)
     * @throws IllegalArgumentException if channelName is invalid or payload is null
     */
    void broadcast(String channelName, String payload);

    /**
     * Get all currently active channel names.
     *
     * <p>
     * A channel is considered active if it has been created and not yet
     * removed due to inactivity. This includes channels with zero clients.
     * </p>
     *
     * @return list of active channel names, may be empty
     */
    List<String> getActiveChannels();

    /**
     * Get the number of clients currently connected to a channel.
     *
     * @param channelName the channel name
     * @return the number of connected clients, or 0 if channel doesn't exist
     */
    int getClientCount(String channelName);

    /**
     * Close a specific channel and disconnect all clients.
     *
     * <p>
     * All clients will receive a close event and their connections will
     * be terminated. The channel will be removed from the pool.
     * </p>
     *
     * <p>
     * If the channel does not exist, this method does nothing.
     * </p>
     *
     * @param channelName the channel name to close
     */
    void closeChannel(String channelName);

    /**
     * Close all channels and disconnect all clients.
     *
     * <p>
     * This should be called during application shutdown to ensure
     * graceful cleanup of all SSE connections.
     * </p>
     */
    void closeAllChannels();
}


