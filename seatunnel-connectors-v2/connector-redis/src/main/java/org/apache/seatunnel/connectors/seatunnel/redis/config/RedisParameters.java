/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.connectors.seatunnel.redis.config;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.common.exception.CommonErrorCodeDeprecated;
import org.apache.seatunnel.connectors.seatunnel.redis.client.RedisClient;
import org.apache.seatunnel.connectors.seatunnel.redis.client.RedisClusterClient;
import org.apache.seatunnel.connectors.seatunnel.redis.client.RedisSingleClient;
import org.apache.seatunnel.connectors.seatunnel.redis.exception.RedisConnectorException;

import org.apache.commons.lang3.StringUtils;

import lombok.Data;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

@Data
public class RedisParameters implements Serializable {
    private String host;
    private int port;
    private String auth = "";
    private int dbNum;
    private String user = "";
    private String keysPattern;
    private String keyField;
    private RedisDataType redisDataType;
    private RedisConfig.RedisMode mode;
    private RedisConfig.HashKeyParseMode hashKeyParseMode;
    private List<String> redisNodes = Collections.emptyList();
    private long expire = RedisConfig.EXPIRE.defaultValue();
    private int batchSize = RedisConfig.BATCH_SIZE.defaultValue();

    public void buildWithConfig(ReadonlyConfig config) {
        // set host
        this.host = config.get(RedisConfig.HOST);
        // set port
        this.port = config.get(RedisConfig.PORT);
        // set db_num
        this.dbNum = config.get(RedisConfig.DB_NUM);
        // set hash key mode
        this.hashKeyParseMode = config.get(RedisConfig.HASH_KEY_PARSE_MODE);
        // set expire
        this.expire = config.get(RedisConfig.EXPIRE);
        // set auth
        if (config.getOptional(RedisConfig.AUTH).isPresent()) {
            this.auth = config.get(RedisConfig.AUTH);
        }
        // set user
        if (config.getOptional(RedisConfig.USER).isPresent()) {
            this.user = config.get(RedisConfig.USER);
        }
        // set mode
        this.mode = config.get(RedisConfig.MODE);
        // set redis nodes information
        if (config.getOptional(RedisConfig.NODES).isPresent()) {
            this.redisNodes = config.get(RedisConfig.NODES);
        }
        // set key
        if (config.getOptional(RedisConfig.KEY).isPresent()) {
            this.keyField = config.get(RedisConfig.KEY);
        }
        // set keysPattern
        if (config.getOptional(RedisConfig.KEY_PATTERN).isPresent()) {
            this.keysPattern = config.get(RedisConfig.KEY_PATTERN);
        }
        // set redis data type verification factory createAndPrepareSource
        this.redisDataType = config.get(RedisConfig.DATA_TYPE);
        // Indicates the number of keys to attempt to return per iteration.default 10
        this.batchSize = config.get(RedisConfig.BATCH_SIZE);
    }

    public RedisClient buildRedisClient() {
        Jedis jedis = this.buildJedis();
        if (mode.equals(RedisConfig.RedisMode.SINGLE)) {
            return new RedisSingleClient(this, jedis);
        } else {
            return new RedisClusterClient(this, jedis);
        }
    }

    public Jedis buildJedis() {
        switch (mode) {
            case SINGLE:
                Jedis jedis = new Jedis(host, port);
                if (StringUtils.isNotBlank(auth)) {
                    jedis.auth(auth);
                }
                if (StringUtils.isNotBlank(user)) {
                    jedis.aclSetUser(user);
                }
                jedis.select(dbNum);
                return jedis;
            case CLUSTER:
                HashSet<HostAndPort> nodes = new HashSet<>();
                HostAndPort node = new HostAndPort(host, port);
                nodes.add(node);
                if (!redisNodes.isEmpty()) {
                    for (String redisNode : redisNodes) {
                        String[] splits = redisNode.split(":");
                        if (splits.length != 2) {
                            throw new RedisConnectorException(
                                    CommonErrorCodeDeprecated.ILLEGAL_ARGUMENT,
                                    "Invalid redis node information,"
                                            + "redis node information must like as the following: [host:port]");
                        }
                        HostAndPort hostAndPort =
                                new HostAndPort(splits[0], Integer.parseInt(splits[1]));
                        nodes.add(hostAndPort);
                    }
                }
                ConnectionPoolConfig connectionPoolConfig = new ConnectionPoolConfig();
                JedisCluster jedisCluster;
                if (StringUtils.isNotBlank(auth)) {
                    jedisCluster =
                            new JedisCluster(
                                    nodes,
                                    JedisCluster.DEFAULT_TIMEOUT,
                                    JedisCluster.DEFAULT_TIMEOUT,
                                    JedisCluster.DEFAULT_MAX_ATTEMPTS,
                                    auth,
                                    connectionPoolConfig);
                } else {
                    jedisCluster = new JedisCluster(nodes);
                }
                JedisWrapper jedisWrapper = new JedisWrapper(jedisCluster);
                jedisWrapper.select(dbNum);
                return jedisWrapper;
            default:
                // do nothing
                throw new RedisConnectorException(
                        CommonErrorCodeDeprecated.UNSUPPORTED_OPERATION,
                        "Not support this redis mode");
        }
    }
}
