package cn.lyf.redis.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.*;
import org.springframework.util.ObjectUtils;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author lyf
 * @description: RedisConfig 集群版
 * @version: v1.0
 * @since 2022-04-09 14:45
 */
@Configuration
@EnableCaching
public class RedisConfiguration extends CachingConfigurerSupport {
    private static final String REDIS_PREFIX = "redis://";
    private static final String REDIS_HOST_APPEND_PORT = ":";
    @Autowired
    private RedisProperties redisProperties;

    /**
     * 自定义缓存key的生成策略。
     * 默认的生成策略是看不懂的(乱码内容) 通过Spring 的依赖注入特性进行自定义的配置注入并且此类是一个配置类可以更多程度的自定义配置
     *
     * @return KeyGenerator
     */
    @Bean
    @Override
    public KeyGenerator keyGenerator() {
        return (target, method, params) -> {
            StringBuilder sb = new StringBuilder();
            sb.append(target.getClass().getName());
            sb.append(method.getName());
            for (Object obj : params) {
                sb.append(obj.toString());
            }
            return sb.toString();
        };
    }

    /**
     * 缓存配置管理器
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(genericJackson2JsonRedisSerializer()))
                .entryTtl(Duration.ofMinutes(5L));

        Map<String, RedisCacheConfiguration> map = new HashMap<>();
        map.put("custom", cacheConfiguration);

        return RedisCacheManager.builder(connectionFactory)
                .withInitialCacheConfigurations(map)
                .transactionAware()
                .cacheWriter(RedisCacheWriter.lockingRedisCacheWriter(connectionFactory))
                .build();
    }

    /**
     * 通过配置RedisStandaloneConfiguration实例来
     * 创建Redis Standolone模式的客户端连接创建工厂
     * 配置hostname和port
     *
     * @return LettuceConnectionFactory
     */
    @Bean
    public JedisConnectionFactory redisConnectionFactory() {
        JedisConnectionFactory jedisConnectionFactory;
        RedisProperties.Cluster cluster = redisProperties.getCluster();
        String password = redisProperties.getPassword();
        if (cluster != null) {
            // 集群配置
            List<String> nodes = cluster.getNodes();
            RedisClusterConfiguration clusterConfiguration = new RedisClusterConfiguration(nodes);
            if (!ObjectUtils.isEmpty(password)) {
                clusterConfiguration.setPassword(password);
            }
            jedisConnectionFactory = new JedisConnectionFactory(clusterConfiguration);
        } else {
            // 单机配置
            RedisStandaloneConfiguration standaloneConfiguration = new RedisStandaloneConfiguration(
                    redisProperties.getHost(), redisProperties.getPort());
            if (!ObjectUtils.isEmpty(password)) {
                standaloneConfiguration.setPassword(password);
            }
            jedisConnectionFactory =
                    new JedisConnectionFactory(standaloneConfiguration);
        }
        return jedisConnectionFactory;
    }

    /**
     * 保证序列化之后不会乱码的配置
     *
     * @param connectionFactory connectionFactory
     * @return RedisTemplate
     */
    @Bean(name = "jsonRedisTemplate")
    public RedisTemplate<String, Serializable> redisTemplate(JedisConnectionFactory connectionFactory) {
        return getRedisTemplate(connectionFactory, genericJackson2JsonRedisSerializer());
    }

    /**
     * 解决：
     * org.springframework.data.redis.serializer.SerializationException:
     * Could not write JSON: Java 8 date/time type `java.time.LocalDateTime` not supported
     *
     * @return GenericJackson2JsonRedisSerializer
     */
    @Bean
    @Primary // 当存在多个Bean时，此bean优先级最高
    public GenericJackson2JsonRedisSerializer genericJackson2JsonRedisSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();
        // 解决查询缓存转换异常的问题
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        /*objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.WRAPPER_ARRAY);*/
        // 支持 jdk 1.8 日期   ---- start ---
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.registerModule(new Jdk8Module())
                .registerModule(new JavaTimeModule())
                .registerModule(new ParameterNamesModule());
        // --end --
        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }

    /**
     * 采用jdk序列化的方式
     *
     * @param connectionFactory connectionFactory
     * @return RedisTemplate
     */
    @Bean(name = "jdkRedisTemplate")
    public RedisTemplate<String, Serializable> redisTemplateByJdkSerialization(JedisConnectionFactory connectionFactory) {
        return getRedisTemplate(connectionFactory, new JdkSerializationRedisSerializer());
    }

    private RedisTemplate<String, Serializable> getRedisTemplate(JedisConnectionFactory connectionFactory,
                                                                 RedisSerializer<?> redisSerializer) {
        RedisTemplate<String, Serializable> redisTemplate = new RedisTemplate<>();
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(redisSerializer);

        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(redisSerializer);
        connectionFactory.afterPropertiesSet();
        redisTemplate.setConnectionFactory(connectionFactory);
        return redisTemplate;
    }

    /**
     * 注入Redisson
     *
     * @return RedissonClient
     */
    @Bean
    public RedissonClient redisson() {
        Config config = new Config();
        String password = redisProperties.getPassword();
        if (redisProperties.getCluster() != null) {
            //集群模式配置
            List<String> nodes = redisProperties.getCluster().getNodes();

            List<String> clusterNodes = new ArrayList<>();
            for (String node : nodes) {
                clusterNodes.add(REDIS_PREFIX + node);
            }
            ClusterServersConfig clusterServersConfig = config.useClusterServers()
                    .addNodeAddress(clusterNodes.toArray(new String[0]));

            if (!ObjectUtils.isEmpty(password)) {
                clusterServersConfig.setPassword(password);
            }
        } else {
            //单节点配置
            String address =
                    REDIS_PREFIX + redisProperties.getHost() + REDIS_HOST_APPEND_PORT + redisProperties.getPort();
            SingleServerConfig serverConfig = config.useSingleServer();
            serverConfig.setAddress(address);
            if (!ObjectUtils.isEmpty(password)) {
                serverConfig.setPassword(password);
            }
            serverConfig.setDatabase(redisProperties.getDatabase());
        }

        return Redisson.create(config);
    }
}
