/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.map;

import com.hazelcast.config.Config;
import com.hazelcast.config.NearCacheConfig;
import com.hazelcast.config.InMemoryFormat;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.test.HazelcastJUnit4ClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.TestHazelcastInstanceFactory;
import com.hazelcast.test.annotation.ParallelTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(HazelcastJUnit4ClassRunner.class)
@Category(ParallelTest.class)
public class SizeEstimatorTest extends HazelcastTestSupport {

    @Test
    public void testIdleState() throws InterruptedException {
        final String MAP_NAME = "default";

        final TestHazelcastInstanceFactory factory = createHazelcastInstanceFactory(1);
        final HazelcastInstance h = factory.newHazelcastInstance(new Config());

        final IMap<String, String> map = h.getMap(MAP_NAME);

        Assert.assertEquals(0, map.getLocalMapStats().getHeapCost());
    }

    @Test
    public void testPuts() throws InterruptedException {
        final String MAP_NAME = "default";

        Config config = new Config();
        final TestHazelcastInstanceFactory factory = createHazelcastInstanceFactory(1);
        final HazelcastInstance h = factory.newHazelcastInstance(config);

        final IMap<Integer, Long> map = h.getMap(MAP_NAME);
        map.put(0, 10L);
        Assert.assertEquals(156, map.getLocalMapStats().getHeapCost());
    }

    @Test
    public void testPutRemove() throws InterruptedException {
        final String MAP_NAME = "default";
        final Config config = new Config();
        config.getMapConfig(MAP_NAME).setBackupCount(1).setInMemoryFormat(InMemoryFormat.BINARY);
        final TestHazelcastInstanceFactory factory = createHazelcastInstanceFactory(2);
        final HazelcastInstance h[] = factory.newInstances(config);
        final IMap<String, String> map1 = h[0].getMap(MAP_NAME);
        final IMap<String, String> map2 = h[1].getMap(MAP_NAME);
        warmUpPartitions(h);
        Assert.assertTrue( map1.size() == 0 && (map1.size() == map2.size()) );
        //put and check
        map1.put("key", "value");
        final long costOfMapOnNode1AfterPut = map1.getLocalMapStats().getHeapCost();
        final long costOfMapOnNode2AfterPut = map2.getLocalMapStats().getHeapCost();
        Assert.assertTrue( costOfMapOnNode1AfterPut > 0 && (costOfMapOnNode1AfterPut == costOfMapOnNode2AfterPut));
        //remove and check
        map1.remove("key");
        final long costOfMapOnNode1AfterRemove = map1.getLocalMapStats().getHeapCost();
        final long costOfMapOnNode2AfterRemove = map2.getLocalMapStats().getHeapCost();
        Assert.assertTrue( costOfMapOnNode1AfterRemove == 0 && (costOfMapOnNode1AfterRemove == costOfMapOnNode2AfterRemove));
    }

    @Test
    public void testNearCache() throws InterruptedException {
        final String NO_NEAR_CACHED_MAP = "testIssue833";
        final String NEAR_CACHED_MAP = "testNearCache";

        final Config config = new Config();
        final NearCacheConfig nearCacheConfig = new NearCacheConfig();
        nearCacheConfig.setInMemoryFormat(InMemoryFormat.BINARY);
        config.getMapConfig(NEAR_CACHED_MAP).setNearCacheConfig(nearCacheConfig).setBackupCount(0);
        config.getMapConfig(NO_NEAR_CACHED_MAP).setBackupCount(0);

        final int n = 2;
        final TestHazelcastInstanceFactory factory = createHazelcastInstanceFactory(n);
        final HazelcastInstance h[] = factory.newInstances(config);
        warmUpPartitions(h);

        final IMap<String, String> noNearCached = h[0].getMap(NO_NEAR_CACHED_MAP);
        for (int i = 0; i < 1000; i++) {
            noNearCached.put("key" + i, "value" + i);
        }

        final IMap<String, String> nearCachedMap = h[0].getMap(NEAR_CACHED_MAP);
        for (int i = 0; i < 1000; i++) {
            nearCachedMap.put("key" + i, "value" + i);
        }

        for (int i = 0; i < 1000; i++) {
            nearCachedMap.get("key" + i);
        }

        Assert.assertTrue(nearCachedMap.getLocalMapStats().getHeapCost() > noNearCached.getLocalMapStats().getHeapCost());
    }

    @Test
    public void testInMemoryFormats() throws InterruptedException {
        final String BINARY_MAP = "testBinaryFormat";
        final String OBJECT_MAP = "testObjectFormat";
        final Config config = new Config();
        config.getMapConfig(BINARY_MAP).
                setInMemoryFormat(InMemoryFormat.BINARY).setBackupCount(0);
        config.getMapConfig(OBJECT_MAP).
                setInMemoryFormat(InMemoryFormat.OBJECT).setBackupCount(0);

        final int n = 2;
        final TestHazelcastInstanceFactory factory = createHazelcastInstanceFactory(n);
        final HazelcastInstance[] h = factory.newInstances(config);
        warmUpPartitions(h);

        // populate map.
        final IMap<String, String> binaryMap = h[0].getMap(BINARY_MAP);
        for (int i = 0; i < 1000; i++) {
            binaryMap.put("key" + i, "value" + i);
        }

        final IMap<String, String> objectMap = h[0].getMap(OBJECT_MAP);
        for (int i = 0; i < 1000; i++) {
            objectMap.put("key" + i, "value" + i);
        }

        for (int i = 0; i < n; i++) {
            Assert.assertTrue(h[i].getMap(BINARY_MAP).getLocalMapStats().getHeapCost() > 0);
            Assert.assertEquals(0, h[i].getMap(OBJECT_MAP).getLocalMapStats().getHeapCost());
        }

        // clear map
        binaryMap.clear();
        objectMap.clear();

        for (int i = 0; i < n; i++) {
            Assert.assertEquals(0, h[i].getMap(BINARY_MAP).getLocalMapStats().getHeapCost());
            Assert.assertEquals(0, h[i].getMap(OBJECT_MAP).getLocalMapStats().getHeapCost());
        }
    }

}
