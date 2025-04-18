/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.processors.standard;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.distributed.cache.client.Deserializer;
import org.apache.nifi.distributed.cache.client.DistributedMapCacheClient;
import org.apache.nifi.distributed.cache.client.MapCacheClientService;
import org.apache.nifi.distributed.cache.client.Serializer;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.state.MockStateManager;
import org.apache.nifi.util.MockComponentLog;
import org.apache.nifi.util.MockControllerServiceInitializationContext;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestDetectDuplicate {

    @Test
    public void testDuplicate() throws InitializationException {
        final TestRunner runner = TestRunners.newTestRunner(DetectDuplicate.class);
        final EphemeralMapCacheClientService client = createClient();
        final Map<String, String> clientProperties = new HashMap<>();
        clientProperties.put(MapCacheClientService.HOSTNAME.getName(), "localhost");
        runner.addControllerService("client", client, clientProperties);
        runner.setProperty(DetectDuplicate.DISTRIBUTED_CACHE_SERVICE, "client");
        runner.setProperty(DetectDuplicate.FLOWFILE_DESCRIPTION, "The original flow file");
        runner.setProperty(DetectDuplicate.AGE_OFF_DURATION, "48 hours");
        final Map<String, String> props = new HashMap<>();
        props.put("hash.value", "1000");
        runner.enqueue(new byte[]{}, props);
        runner.enableControllerService(client);

        runner.run();
        runner.assertAllFlowFilesTransferred(DetectDuplicate.REL_NON_DUPLICATE, 1);
        runner.clearTransferState();
        runner.enqueue(new byte[]{}, props);
        runner.run();
        runner.assertAllFlowFilesTransferred(DetectDuplicate.REL_DUPLICATE, 1);
        runner.assertTransferCount(DetectDuplicate.REL_NON_DUPLICATE, 0);
        runner.assertTransferCount(DetectDuplicate.REL_FAILURE, 0);
    }

    @Test
    public void testDuplicateWithAgeOff() throws InitializationException, InterruptedException {

        final TestRunner runner = TestRunners.newTestRunner(DetectDuplicate.class);
        final EphemeralMapCacheClientService client = createClient();
        final Map<String, String> clientProperties = new HashMap<>();
        clientProperties.put(MapCacheClientService.HOSTNAME.getName(), "localhost");
        runner.addControllerService("client", client, clientProperties);
        runner.setProperty(DetectDuplicate.DISTRIBUTED_CACHE_SERVICE, "client");
        runner.setProperty(DetectDuplicate.FLOWFILE_DESCRIPTION, "The original flow file");
        runner.setProperty(DetectDuplicate.AGE_OFF_DURATION, "2 secs");
        runner.enableControllerService(client);

        final Map<String, String> props = new HashMap<>();
        props.put("hash.value", "1000");
        runner.enqueue(new byte[]{}, props);

        runner.run();
        runner.assertAllFlowFilesTransferred(DetectDuplicate.REL_NON_DUPLICATE, 1);
        runner.clearTransferState();
        Thread.sleep(3000);
        runner.enqueue(new byte[]{}, props);
        runner.run();
        runner.assertAllFlowFilesTransferred(DetectDuplicate.REL_NON_DUPLICATE, 1);
        runner.assertTransferCount(DetectDuplicate.REL_DUPLICATE, 0);
        runner.assertTransferCount(DetectDuplicate.REL_FAILURE, 0);
    }

    private EphemeralMapCacheClientService createClient() throws InitializationException {

        final EphemeralMapCacheClientService client = new EphemeralMapCacheClientService();
        final ComponentLog logger = new MockComponentLog("client", client);
        final MockControllerServiceInitializationContext clientInitContext = new MockControllerServiceInitializationContext(client, "client", logger, new MockStateManager(client));
        client.initialize(clientInitContext);

        return client;
    }

    @Test
    public void testDuplicateNoCache() throws InitializationException {
        final TestRunner runner = TestRunners.newTestRunner(DetectDuplicate.class);
        final EphemeralMapCacheClientService client = createClient();
        final Map<String, String> clientProperties = new HashMap<>();
        clientProperties.put(MapCacheClientService.HOSTNAME.getName(), "localhost");
        runner.addControllerService("client", client, clientProperties);
        runner.setProperty(DetectDuplicate.DISTRIBUTED_CACHE_SERVICE, "client");
        runner.setProperty(DetectDuplicate.FLOWFILE_DESCRIPTION, "The original flow file");
        runner.setProperty(DetectDuplicate.AGE_OFF_DURATION, "48 hours");
        runner.setProperty(DetectDuplicate.CACHE_IDENTIFIER, "false");
        final Map<String, String> props = new HashMap<>();
        props.put("hash.value", "1000");
        runner.enqueue(new byte[]{}, props);
        runner.enableControllerService(client);

        runner.run();
        runner.assertAllFlowFilesTransferred(DetectDuplicate.REL_NON_DUPLICATE, 1);
        runner.clearTransferState();

        runner.setProperty(DetectDuplicate.CACHE_IDENTIFIER, "true");
        runner.enqueue(new byte[]{}, props);
        runner.run();
        runner.assertAllFlowFilesTransferred(DetectDuplicate.REL_NON_DUPLICATE, 1);
        runner.assertTransferCount(DetectDuplicate.REL_DUPLICATE, 0);
        runner.assertTransferCount(DetectDuplicate.REL_FAILURE, 0);
        runner.clearTransferState();

        runner.enqueue(new byte[]{}, props);
        runner.run();
        runner.assertAllFlowFilesTransferred(DetectDuplicate.REL_DUPLICATE, 1);
        runner.assertTransferCount(DetectDuplicate.REL_NON_DUPLICATE, 0);
        runner.assertTransferCount(DetectDuplicate.REL_FAILURE, 0);
    }

    @Test
    public void testDuplicateNoCacheWithAgeOff() throws InitializationException, InterruptedException {

        final TestRunner runner = TestRunners.newTestRunner(DetectDuplicate.class);
        final EphemeralMapCacheClientService client = createClient();
        final Map<String, String> clientProperties = new HashMap<>();
        clientProperties.put(MapCacheClientService.HOSTNAME.getName(), "localhost");
        runner.addControllerService("client", client, clientProperties);
        runner.setProperty(DetectDuplicate.DISTRIBUTED_CACHE_SERVICE, "client");
        runner.setProperty(DetectDuplicate.FLOWFILE_DESCRIPTION, "The original flow file");
        runner.setProperty(DetectDuplicate.AGE_OFF_DURATION, "2 secs");
        runner.enableControllerService(client);

        final Map<String, String> props = new HashMap<>();
        props.put("hash.value", "1000");
        runner.enqueue(new byte[]{}, props);

        runner.run();
        runner.assertAllFlowFilesTransferred(DetectDuplicate.REL_NON_DUPLICATE, 1);

        runner.clearTransferState();
        Thread.sleep(3000);

        runner.setProperty(DetectDuplicate.CACHE_IDENTIFIER, "false");
        runner.enqueue(new byte[]{}, props);
        runner.run();
        runner.assertAllFlowFilesTransferred(DetectDuplicate.REL_NON_DUPLICATE, 1);
        runner.assertTransferCount(DetectDuplicate.REL_DUPLICATE, 0);
        runner.assertTransferCount(DetectDuplicate.REL_FAILURE, 0);
    }

    static final class EphemeralMapCacheClientService extends AbstractControllerService implements DistributedMapCacheClient {

        boolean exists = false;
        private Object cacheValue;

        @Override
        public void close() throws IOException {
        }

        @Override
        protected java.util.List<PropertyDescriptor> getSupportedPropertyDescriptors() {
            final List<PropertyDescriptor> props = new ArrayList<>();
            props.add(MapCacheClientService.HOSTNAME);
            props.add(MapCacheClientService.COMMUNICATIONS_TIMEOUT);
            props.add(MapCacheClientService.PORT);
            props.add(MapCacheClientService.SSL_CONTEXT_SERVICE);
            return props;
        }

        @Override
        public <K, V> boolean putIfAbsent(final K key, final V value, final Serializer<K> keySerializer, final Serializer<V> valueSerializer) {
            if (exists) {
                return false;
            }

            cacheValue = value;
            exists = true;
            return true;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <K, V> V getAndPutIfAbsent(final K key, final V value, final Serializer<K> keySerializer, final Serializer<V> valueSerializer,
                final Deserializer<V> valueDeserializer) {
            if (exists) {
                return (V) cacheValue;
            }
            cacheValue = value;
            exists = true;
            return null;
        }

        @Override
        public <K> boolean containsKey(final K key, final Serializer<K> keySerializer) throws IOException {
            return exists;
        }

        @Override
        public <K, V> V get(final K key, final Serializer<K> keySerializer, final Deserializer<V> valueDeserializer) throws IOException {
            if (exists) {
                return (V) cacheValue;
            } else {
                return null;
            }
        }

        @Override
        public <K> boolean remove(final K key, final Serializer<K> serializer) throws IOException {
            exists = false;
            return true;
        }

        @Override
        public <K, V> void put(final K key, final V value, final Serializer<K> keySerializer, final Serializer<V> valueSerializer) throws IOException {
            cacheValue = value;
            exists = true;
        }
    }
}
