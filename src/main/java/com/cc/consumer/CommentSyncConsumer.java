package com.cc.consumer;

import com.cc.entity.Comment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Service
public class CommentSyncConsumer {

    private static final Logger log = LoggerFactory.getLogger(CommentSyncConsumer.class);

    private static final String KEY_HOT_COMMENTS = "hot_comments";
    private static final String KEY_COMMENT_CACHE_PREFIX = "comment:";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 定义TiDB时间格式的解析器，这个格式是 "yyyy-MM-dd HH:mm:ss"
    private static final DateTimeFormatter TIDB_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @KafkaListener(topics = "comment-topic", groupId = "comment-sync-group-final")
    public void listen(@Payload(required = false) byte[] messageBytes) {
        if (messageBytes == null || messageBytes.length == 0) {
            // 心跳消息，正常忽略
            return;
        }

        String originalMessage = new String(messageBytes, StandardCharsets.UTF_8);
        String jsonPayload = "";
        try {
            int jsonStart = originalMessage.indexOf('{');
            if (jsonStart == -1) {
                log.warn("收到的消息不包含有效的JSON对象，已忽略。消息: {}", originalMessage);
                return;
            }
            jsonPayload = originalMessage.substring(jsonStart);

            log.info("清理后的有效JSON消息: {}", jsonPayload);
            JsonNode rootNode = objectMapper.readTree(jsonPayload);

            String eventType = null;
            JsonNode dataNode = null;

            if (rootNode.has("u")) {
                eventType = "UPSERT";
                dataNode = rootNode.get("u");
            } else if (rootNode.has("d")) {
                eventType = "DELETE";
                dataNode = rootNode.get("d");
            } else {
                log.warn("收到的JSON格式未知 (既没有'u'也没有'd')，已忽略。消息: {}", jsonPayload);
                return;
            }

            Comment comment = new Comment();

            // 从每个包装对象中提取 'v' (value)
            comment.setId(dataNode.get("id").get("v").asLong());
            comment.setContent(dataNode.get("content").get("v").asText());

            // --- 关键修正：适配实体类中的 userId ---
            // JSON 中是 "user_id"，实体类中是 "userId"，需要手动对应
            comment.setUserId(dataNode.get("user_id").get("v").asText());

            comment.setLikes(dataNode.get("likes").get("v").asInt());
            comment.setVersion(dataNode.get("version").get("v").asInt());

            // --- 关键修正：将字符串转换为实体类所需的 LocalDateTime ---
            String createdAtStr = dataNode.get("created_at").get("v").asText();
            String updatedAtStr = dataNode.get("updated_at").get("v").asText();

            comment.setCreatedAt(LocalDateTime.parse(createdAtStr, TIDB_DATETIME_FORMATTER));
            comment.setUpdatedAt(LocalDateTime.parse(updatedAtStr, TIDB_DATETIME_FORMATTER));

            log.info("成功解析出 Comment 对象: {}", comment);

            // 后续业务逻辑完全不变
            if ("UPSERT".equals(eventType)) {
                if (comment.getId() != null) {
                    redisTemplate.opsForZSet().add(KEY_HOT_COMMENTS, comment.getId().toString(), comment.getLikes());
                    log.info("更新 Redis 热度榜: Comment ID = {}, Likes = {}", comment.getId(), comment.getLikes());

                    String cacheKey = KEY_COMMENT_CACHE_PREFIX + comment.getId();
                    String commentJson = objectMapper.writeValueAsString(comment);
                    redisTemplate.opsForValue().set(cacheKey, commentJson, 1, TimeUnit.HOURS);
                    log.info("更新 Redis 缓存: Key = {}", cacheKey);
                }
            } else if ("DELETE".equals(eventType)) {
                if (comment.getId() != null) {
                    redisTemplate.opsForZSet().remove(KEY_HOT_COMMENTS, comment.getId().toString());
                    log.info("从 Redis 热度榜中移除: Comment ID = {}", comment.getId());

                    redisTemplate.delete(KEY_COMMENT_CACHE_PREFIX + comment.getId());
                    log.info("从 Redis 缓存中删除: Key = {}", KEY_COMMENT_CACHE_PREFIX + comment.getId());
                }
            }

        } catch (Exception e) {
            log.error("处理 Kafka 消息时发生未知异常! 原始消息: [{}], 尝试处理的JSON部分: [{}]", originalMessage, jsonPayload, e);
        }
    }
}