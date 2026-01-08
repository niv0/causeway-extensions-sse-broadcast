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
package org.apache.causeway.extensions.sse.broadcast;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import org.apache.causeway.extensions.sse.broadcast.services.SseBroadcastServiceImpl;
import org.apache.causeway.extensions.sse.broadcast.webmodule.WebModuleServerSentBroadcastEvents;

/**
 * Configuration for the Server-Sent Events Broadcast extension.
 *
 * <p>
 * Provides {@link org.apache.causeway.extensions.sse.broadcast.service.SseBroadcastService}
 * implementation for broadcasting events to named channels.
 * </p>
 *
 * @since 3.5.0
 */
@Configuration
@Import({
    SseBroadcastServiceImpl.class,
    WebModuleServerSentBroadcastEvents.class
})
public class CausewayModuleExtSseBroadcast {

    /**
     * Extension identifier.
     */
    public static final String NAMESPACE = "causeway.ext.sse.broadcast";

}

