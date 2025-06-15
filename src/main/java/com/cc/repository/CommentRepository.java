package com.cc.repository;

import com.cc.entity.Comment;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    // 查询点赞数最高的前10条评论
    List<Comment> findTop10ByOrderByLikesDesc();

    /**
     * 查询在指定时间之后，点赞数最高的N条评论
     * @param since 时间点，比如7天前
     * @param pageable 分页参数，用于限制返回的记录数 N
     * @return 评论列表
     */
    @Query("SELECT c FROM Comment c WHERE c.updatedAt >= :since ORDER BY c.likes DESC")
    List<Comment> findTopLikedCommentsSince(@Param("since") LocalDateTime since, Pageable pageable);
}
