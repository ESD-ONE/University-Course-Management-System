package com.ucms.notification.config;

import com.ucms.notification.service.LivePushFanout;
import com.ucms.notification.service.LivePushHub;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** Live pushes reach browsers on every replica (see LivePushHub). Off in tests, which
 *  have no Redis: pushes are then delivered on the one instance. */
@Configuration
@ConditionalOnProperty(name = "ucms.live-push.fanout.enabled", havingValue = "true", matchIfMissing = true)
public class LivePushFanoutConfig {

    @Bean
    public LivePushFanout livePushFanout(StringRedisTemplate redis) {
        return new LivePushFanout(redis);
    }

    @Bean
    public RedisMessageListenerContainer livePushListener(RedisConnectionFactory connectionFactory, LivePushHub hub) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> LivePushFanout.onMessage(message.getBody(), hub::deliver),
                new ChannelTopic(LivePushFanout.CHANNEL));
        return container;
    }
}
