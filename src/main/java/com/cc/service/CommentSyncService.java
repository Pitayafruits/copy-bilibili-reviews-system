package com.cc.service;

import com.cc.entity.Comment;
import com.cc.repository.CommentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class CommentSyncService {

    private static final Logger log = LoggerFactory.getLogger(CommentSyncService.class);

    private static final String KEY_HOT_COMMENTS = "hot_comments";
    private static final String KEY_HOT_COMMENTS_TEMP = "hot_comments_temp";
    private static final String KEY_COMMENT_CACHE_PREFIX = "comment:";

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public void syncHotCommentsToRedis() throws Exception {
        log.info("开始执行热点评论数据同步业务逻辑...");

        // 1. 定义同步范围
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        PageRequest pageRequest = PageRequest.of(0, 1000); // Top 1000

        // 2. 从TiDB查询
        List<Comment> hotComments = commentRepository.findTopLikedCommentsSince(sevenDaysAgo, pageRequest);
        if (hotComments.isEmpty()) {
            log.info("未发现需要同步的热点评论，任务结束。");
            return;
        }
        log.info("从数据库查询到 {} 条热点评论进行同步。", hotComments.size());

        // 3. 写入临时Key
        redisTemplate.delete(KEY_HOT_COMMENTS_TEMP);
        for (Comment comment : hotComments) {
            redisTemplate.opsForZSet().add(KEY_HOT_COMMENTS_TEMP, comment.getId().toString(), comment.getLikes());
            String cacheKey = KEY_COMMENT_CACHE_PREFIX + comment.getId();
            String commentJson = objectMapper.writeValueAsString(comment);
            redisTemplate.opsForValue().set(cacheKey, commentJson, 1, TimeUnit.HOURS);
        }

        // 4. 原子化重命名
        redisTemplate.rename(KEY_HOT_COMMENTS_TEMP, KEY_HOT_COMMENTS);
        log.info("成功同步 {} 条热点评论到 Redis！", hotComments.size());
    }
}
