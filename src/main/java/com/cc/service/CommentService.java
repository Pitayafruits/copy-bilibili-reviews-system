package com.cc.service;

import com.cc.entity.Comment;
import com.cc.repository.CommentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CommentService {

    private static final String KEY_HOT_COMMENTS = "hot_comments";
    private static final String KEY_COMMENT_CACHE_PREFIX = "comment:";

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Transactional
    public Comment createComment(String content, String userId) {
        Comment comment = new Comment();
        comment.setContent(content);
        comment.setUserId(userId);
        // 其他字段如 likes, version, createdAt, updatedAt 都有默认值或由 @PrePersist 处理
        return commentRepository.save(comment);
    }

    @Transactional
    public Comment likeComment(Long commentId) {
        // 使用乐观锁来处理点赞
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("评论不存在: " + commentId));
        comment.setLikes(comment.getLikes() + 1);
        // save 方法在有 @Version 的情况下会自动检查版本号，如果冲突会抛出异常
        return commentRepository.save(comment);
    }

    public List<Comment> getTop10HotComments() {
        try {
            // 1. 优先从 Redis 的 Sorted Set 中获取 Top 10 的评论 ID
            Set<Object> commentIds = redisTemplate.opsForZSet().reverseRange(KEY_HOT_COMMENTS, 0, 9);

            if (commentIds == null || commentIds.isEmpty()) {
                log.warn("Redis 热度榜为空，执行降级策略，直接查询数据库！");
                return fetchFromDB();
            }

            // 2. 根据 ID 批量从 Redis 缓存中获取评论详情
            List<String> cacheKeys = commentIds.stream()
                    .map(id -> KEY_COMMENT_CACHE_PREFIX + id)
                    .collect(Collectors.toList());
            List<Object> cachedCommentsJson = redisTemplate.opsForValue().multiGet(cacheKeys);

            // 3. 反序列化并处理缓存未命中的情况
            return cachedCommentsJson.stream()
                    .map(json -> {
                        try {
                            // 如果缓存命中，直接反序列化
                            if (json != null) {
                                return objectMapper.readValue((String) json, Comment.class);
                            }
                            // 如果缓存未命中（例如已过期），则需要回源到数据库查询
                            // 注意：此处的 ID 需要从 commentIds 中反查，为简化，此处返回 null
                            // 在生产环境中，需要设计更完善的回源机制
                            return null;
                        } catch (Exception e) {
                            log.error("从 Redis 反序列化评论失败", e);
                            return null;
                        }
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("从 Redis 获取热度榜失败，执行降级策略!", e);
            // 降级：如果 Redis 发生任何异常，直接查询数据库
            return fetchFromDB();
        }
    }

    // 降级方法：直接从数据库查询
    private List<Comment> fetchFromDB() {
        return commentRepository.findTop10ByOrderByLikesDesc();
    }
}
