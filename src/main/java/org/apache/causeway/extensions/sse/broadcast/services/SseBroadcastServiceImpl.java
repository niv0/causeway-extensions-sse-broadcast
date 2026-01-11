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
package org.apache.causeway.extensions.sse.broadcast.services;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.inject.Named;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.commons.internal.base._Strings;
import org.apache.causeway.commons.internal.collections._Lists;
import org.apache.causeway.extensions.sse.applib.annotations.SseSource;
import org.apache.causeway.extensions.sse.applib.service.SseChannel;
import org.apache.causeway.extensions.sse.broadcast.CausewayModuleExtSseBroadcast;
import org.apache.causeway.extensions.sse.broadcast.service.SseBroadcastService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Broadcast-style Server-Sent Events service implementation.
 *
 * <p>
 * This service manages multiple broadcast channels, each identified by a
 * string name. Multiple clients can connect to the same channel and receive
 * events broadcast to that channel.
 * </p>
 *
 * <p>
 * Thread-safe implementation using concurrent data structures and minimal
 * locking for high-performance broadcasting.
 * </p>
 *
 * @see <a href="https://html.spec.whatwg.org/multipage/server-sent-events.html">Server-Sent Events Spec</a>
 * @since 3.1 {@index}
 */
@Service
@Named(SseBroadcastServiceImpl.LOGICAL_TYPE_NAME)
@Priority(PriorityPrecedence.MIDPOINT + 100)
@Qualifier("Broadcast")
@Slf4j
public class SseBroadcastServiceImpl implements SseBroadcastService {

    public static final String LOGICAL_TYPE_NAME = CausewayModuleExtSseBroadcast.NAMESPACE + ".SseBroadcastService";

    // Channel name validation pattern: alphanumeric, dash, underscore, colon, dot (1-100 chars)
    // Must not start with _system (reserved prefix)
    private static final Pattern VALID_CHANNEL_NAME =
            Pattern.compile("^(?!_system)[a-zA-Z0-9\\-_:.]{1,100}$");

    private static final int MAX_PAYLOAD_SIZE = 64 * 1024; // 64KB

    private final BroadcastChannelPool channelPool = new BroadcastChannelPool();

    @PostConstruct
    public void init() {
        log.info("SseBroadcastService initialized");
    }

    @PreDestroy
    public void destroy() {
        log.info("SseBroadcastService shutting down, closing all channels");
        closeAllChannels();
    }

    @Override
    public Optional<SseChannel> lookupByChannelName(final String channelName) {
        validateChannelName(channelName);
        return Optional.of(channelPool.getOrCreateChannel(channelName));
    }

    @Override
    public void broadcast(final String channelName, final String payload) {
        Objects.requireNonNull(channelName, "channelName is required");
        Objects.requireNonNull(payload, "payload is required");
        validateChannelName(channelName);
        validatePayload(payload);

        var channel = channelPool.getChannel(channelName);
        if (channel.isEmpty()) {
            log.debug("Channel '{}' does not exist, event discarded", channelName);
            return;
        }

        var broadcastChannel = (BroadcastChannel) channel.get();
        broadcastChannel.firePayload(payload);

        log.debug("Broadcast to channel '{}' completed, {} clients",
                channelName, broadcastChannel.getListenerCount());
    }


    @Override
    public List<String> getActiveChannels() {
        return channelPool.getActiveChannelNames();
    }

    @Override
    public int getClientCount(final String channelName) {
        return channelPool.getChannel(channelName)
                .map(channel -> ((BroadcastChannel) channel).getListenerCount())
                .orElse(0);
    }

    @Override
    public void closeChannel(final String channelName) {
        validateChannelName(channelName);
        channelPool.removeChannel(channelName);
    }

    @Override
    public void closeAllChannels() {
        channelPool.closeAll();
    }

    // -- HELPER METHODS --

    private void validateChannelName(final String channelName) {
        if (_Strings.isNullOrEmpty(channelName)) {
            throw new IllegalArgumentException("Channel name cannot be null or empty");
        }
        if (!VALID_CHANNEL_NAME.matcher(channelName).matches()) {
            throw new IllegalArgumentException(
                    "Invalid channel name: '" + channelName + "'. " +
                            "Must be 1-100 characters, alphanumeric with -_:. and not start with _system");
        }
    }

    private void validatePayload(final String payload) {
        if (payload.length() > MAX_PAYLOAD_SIZE) {
            throw new IllegalArgumentException(
                    "Payload size exceeds maximum: " + payload.length() + " > " + MAX_PAYLOAD_SIZE);
        }
    }

    // -- BROADCAST CHANNEL POOL --

    private static class BroadcastChannelPool {

        private final Map<String, BroadcastChannel> channelsByName = new ConcurrentHashMap<>();

        public Optional<SseChannel> getChannel(final String channelName) {
            return Optional.ofNullable(channelsByName.get(channelName));
        }

        public synchronized BroadcastChannel getOrCreateChannel(final String channelName) {
            return channelsByName.computeIfAbsent(channelName,
                    name -> {
                        log.info("Creating new broadcast channel: {}", name);
                        return new BroadcastChannel(UUID.randomUUID(), name);
                    });
        }

        public synchronized void removeChannel(final String channelName) {
            var channel = channelsByName.remove(channelName);
            if (channel != null) {
                log.info("Closing channel: {}", channelName);
                channel.close();
            }
        }

        public synchronized void closeAll() {
            channelsByName.values().forEach(BroadcastChannel::close);
            channelsByName.clear();
            log.info("All channels closed");
        }

        public List<String> getActiveChannelNames() {
            return _Lists.newArrayList(channelsByName.keySet());
        }
    }

    // -- BROADCAST CHANNEL IMPLEMENTATION --

    /**
     * A broadcast channel that supports multiple concurrent listeners.
     *
     * <p>
     * Thread-safe implementation using concurrent queue and defensive copy
     * pattern for iteration during broadcast.
     * </p>
     */
    @Slf4j
    private static class BroadcastChannel implements SseChannel {

        private static final Object $LOCK = new Object[0];

        private final UUID uuid;
        @Getter
        private final String channelName;
        private final CountDownLatch latch;
        private final Queue<Predicate<SseSource>> listeners;

        public BroadcastChannel(final UUID uuid, final String channelName) {
            this.uuid = uuid;
            this.channelName = channelName;
            this.latch = new CountDownLatch(1);
            this.listeners = new ConcurrentLinkedQueue<>();
        }

        @Override
        public UUID uuid() {
            return uuid;
        }

        @Override
        public Class<?> sourceType() {
            // Not applicable for broadcast channels
            return BroadcastSource.class;
        }

        @Override
        public void fire(final SseSource source) {
            final List<Predicate<SseSource>> defensiveCopyOfListeners;

            synchronized ($LOCK) {
                if (!isActive()) {
                    log.debug("Channel {} is not active, fire ignored", channelName);
                    return;
                }
                defensiveCopyOfListeners = _Lists.newArrayList(listeners);
            }

            log.debug("Broadcasting to {} listeners on channel {}",
                    defensiveCopyOfListeners.size(), channelName);

            final List<Predicate<SseSource>> markedForRemoval = _Lists.newArrayList();

            defensiveCopyOfListeners.forEach(listener -> {
                try {
                    var retain = listener.test(source);
                    if (!retain) {
                        markedForRemoval.add(listener);
                        log.debug("Listener disconnected from channel {}", channelName);
                    }
                } catch (Exception e) {
                    log.warn("Error during broadcast to listener on channel {}", channelName, e);
                    markedForRemoval.add(listener);
                }
            });

            synchronized ($LOCK) {
                if (!isActive()) return;
                listeners.removeAll(markedForRemoval);
            }
        }

        /**
         * Fire a string payload directly without creating a full SseSource.
         */
        public void firePayload(final String payload) {
            var source = new BroadcastSource(payload);
            fire(source);
        }

        @Override
        public void listenWhile(final Predicate<SseSource> listener) {
            synchronized ($LOCK) {
                if (isActive()) {
                    listeners.add(listener);
                    log.debug("New listener added to channel {}, total: {}",
                            channelName, listeners.size());
                }
            }
        }

        @Override
        public void close() {
            synchronized ($LOCK) {
                listeners.clear();
                latch.countDown();
                log.info("Channel {} closed", channelName);
            }
        }

        @Override
        public void awaitClose() throws InterruptedException {
            latch.await();
        }

        public int getListenerCount() {
            return listeners.size();
        }

        private boolean isActive() {
            return latch.getCount() > 0L;
        }
    }

    // -- BROADCAST SOURCE --

    /**
     * Simple SseSource implementation for broadcast payloads.
     */
    @RequiredArgsConstructor
    private static class BroadcastSource implements SseSource {

        private final String payload;

        @Override
        public void run(final SseChannel eventStream) {
            // Not used in broadcast mode
            throw new UnsupportedOperationException("BroadcastSource does not support run()");
        }

        @Override
        public String getPayload() {
            return payload;
        }
    }
}

