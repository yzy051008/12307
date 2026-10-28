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

package org.opengoofy.index12306.biz.aggregationservice.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson配置类，用于配置无密码的Redis连接
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host:127.0.0.1}") String redisHost,
            @Value("${spring.data.redis.port:6379}") int redisPort,
            @Value("${spring.data.redis.password:}") String redisPassword) {
        Config config = new Config();
        var server = config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort)
                .setDatabase(0)
                .setTimeout(3000)
                .setConnectTimeout(10000)
                .setRetryAttempts(3)
                .setRetryInterval(1000);
        if (redisPassword != null && !redisPassword.isBlank()) {
            server.setPassword(redisPassword);
        }

        return Redisson.create(config);
    }
}