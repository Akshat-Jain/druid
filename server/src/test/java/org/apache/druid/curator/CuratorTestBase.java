/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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

package org.apache.druid.curator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.apache.curator.test.Timing;
import org.apache.curator.utils.ZKPaths;
import org.apache.druid.client.DruidServer;
import org.apache.druid.server.initialization.ZkPathsConfig;
import org.apache.druid.timeline.DataSegment;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;

import java.io.IOException;

/**
 */
public class CuratorTestBase
{
  protected TestingServer server;
  protected Timing timing;
  protected CuratorFramework curator;

  public void setupServerAndCurator() throws Exception
  {
    server = new TestingServer();
    timing = new Timing();
    curator = CuratorFrameworkFactory
        .builder()
        .connectString(server.getConnectString())
        .sessionTimeoutMs(timing.session())
        .connectionTimeoutMs(timing.connection())
        .retryPolicy(new RetryOneTime(1))
        .compressionProvider(new PotentiallyGzippedCompressionProvider(true))
        .build();
  }

  protected void setupZNodeForServer(DruidServer server, ZkPathsConfig zkPathsConfig, ObjectMapper jsonMapper)
  {
    final String announcementsPath = zkPathsConfig.getAnnouncementsPath();
    final String inventoryPath = zkPathsConfig.getLiveSegmentsPath();

    String zkPath = ZKPaths.makePath(announcementsPath, server.getHost());
    try {
      curator.create()
             .creatingParentsIfNeeded()
             .forPath(zkPath, jsonMapper.writeValueAsBytes(server.getMetadata()));
      curator.create()
             .creatingParentsIfNeeded()
             .forPath(ZKPaths.makePath(inventoryPath, server.getHost()));
    }
    catch (KeeperException.NodeExistsException e) {
      /*
       * For some reason, Travis build sometimes fails here because of
       * org.apache.zookeeper.KeeperException$NodeExistsException: KeeperErrorCode = NodeExists, though it should never
       * happen because zookeeper should be in a clean state for each run of tests.
       * Address issue: https://github.com/apache/druid/issues/1512
       */
      try {
        curator.setData()
               .forPath(zkPath, jsonMapper.writeValueAsBytes(server.getMetadata()));
        curator.setData()
               .forPath(ZKPaths.makePath(inventoryPath, server.getHost()));
      }
      catch (Exception e1) {
        throw new RuntimeException(e1);
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected void announceSegmentForServer(
      DruidServer druidServer,
      DataSegment segment,
      ZkPathsConfig zkPathsConfig,
      ObjectMapper jsonMapper
  )
  {
    final String segmentAnnouncementPath =
        ZKPaths.makePath(zkPathsConfig.getLiveSegmentsPath(), druidServer.getHost(), segment.getId().toString());

    try {
      curator.create()
             .compressed()
             .withMode(CreateMode.EPHEMERAL)
             .forPath(segmentAnnouncementPath, jsonMapper.writeValueAsBytes(ImmutableSet.of(segment)));
    }
    catch (KeeperException.NodeExistsException e) {
      try {
        curator.setData()
               .forPath(segmentAnnouncementPath, jsonMapper.writeValueAsBytes(ImmutableSet.of(segment)));
      }
      catch (Exception e1) {
        throw new RuntimeException(e1);
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected void unannounceSegmentForServer(DruidServer druidServer, DataSegment segment, ZkPathsConfig zkPathsConfig)
      throws Exception
  {
    String path = ZKPaths.makePath(
        zkPathsConfig.getLiveSegmentsPath(),
        druidServer.getHost(),
        segment.getId().toString()
    );
    curator.delete().guaranteed().forPath(path);
  }

  public void tearDownServerAndCurator()
  {
    try {
      curator.close();
      server.close();
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public String getConnectString()
  {
    return server.getConnectString();
  }
}
